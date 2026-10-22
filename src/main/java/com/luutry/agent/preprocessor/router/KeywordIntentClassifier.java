package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.IntentResult;
import com.luutry.agent.preprocessor.model.QueryIntent;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于关键词的意图分类器 — 仅作 Embedding 分类器的降级后备。
 * <p>
 * 用途：当 Embedding 服务调用失败（超时/网络异常/服务不可用）时，
 * 提供基于关键词匹配的兜底分类能力，保证主流程不中断。
 * </p>
 * <p>
 * 设计说明：
 * 不再作为 @Component 注册（由 LlmConfig 中作为 Bean 注入给 EmbeddingIntentClassifier）。
 * 关键词列表通过构造函数显式传入，不再依赖 IntentClassificationPolicy。
 * 保留 V2 的核心评分逻辑：绝对评分 + 递减收益 + 短词边界保护 + MIXED 加严。
 * </p>
 */
@Slf4j
public class KeywordIntentClassifier implements IntentClassifier {

    /** 置信度衰减除数：命中几个关键词达到满分 1.0 */
    private static final double CONFIDENCE_DIVISOR = 3.0;

    /** MIXED 意图要求的最低绝对命中数 */
    private static final int MIXED_MIN_HITS = 2;

    /** 无匹配时的默认置信度（低值表示不确定） */
    private static final double NO_MATCH_CONFIDENCE = 0.1;

    /** 短词最大字符长度（≤ 此值启用边界保护） */
    private static final int SHORT_WORD_MAX_LEN = 2;

    private final List<String> toolKeywords;
    private final List<String> ragKeywords;
    private final List<String> chitchatKeywords;

    /**
     * @param toolKeywords    TOOL 意图关键词列表
     * @param ragKeywords     RAG 意图关键词列表
     * @param chitchatKeywords CHITCHAT 意图关键词列表（不应含"是/对/嗯"等高频单字）
     */
    public KeywordIntentClassifier(List<String> toolKeywords,
                                   List<String> ragKeywords,
                                   List<String> chitchatKeywords) {
        this.toolKeywords = toolKeywords != null ? toolKeywords : List.of();
        this.ragKeywords = ragKeywords != null ? ragKeywords : List.of();
        this.chitchatKeywords = chitchatKeywords != null ? chitchatKeywords : List.of();
    }

    @Override
    public IntentResult classify(String query) {
        if (query == null || query.isBlank()) {
            return IntentResult.chitchat();
        }

        String lowerQuery = query.toLowerCase();

        int toolHits = countKeywordHits(lowerQuery, toolKeywords);
        int ragHits = countKeywordHits(lowerQuery, ragKeywords);
        int chitchatHits = countKeywordHits(lowerQuery, chitchatKeywords);

        int totalHits = toolHits + ragHits + chitchatHits;
        if (totalHits == 0) {
            return new IntentResult(QueryIntent.CHITCHAT, NO_MATCH_CONFIDENCE,
                    buildScores(0.0, 0.0, 0.0));
        }

        double toolScore = Math.min(toolHits / CONFIDENCE_DIVISOR, 1.0);
        double ragScore = Math.min(ragHits / CONFIDENCE_DIVISOR, 1.0);
        double chitchatScore = Math.min(chitchatHits / CONFIDENCE_DIVISOR, 1.0);

        QueryIntent finalIntent;
        double confidence;

        if (toolHits >= MIXED_MIN_HITS && ragHits >= MIXED_MIN_HITS) {
            finalIntent = QueryIntent.MIXED;
            confidence = Math.min((toolHits + ragHits) / (CONFIDENCE_DIVISOR * 2), 1.0);
        } else if (toolScore >= ragScore && toolScore >= chitchatScore) {
            finalIntent = QueryIntent.TOOL;
            confidence = toolScore;
        } else if (ragScore >= toolScore && ragScore >= chitchatScore) {
            finalIntent = QueryIntent.RAG;
            confidence = ragScore;
        } else {
            finalIntent = QueryIntent.CHITCHAT;
            confidence = chitchatScore;
        }

        Map<QueryIntent, Double> scores = buildScores(toolScore, ragScore, chitchatScore);
        if (finalIntent == QueryIntent.MIXED) {
            scores.put(QueryIntent.MIXED, confidence);
        }

        return new IntentResult(finalIntent, confidence, scores);
    }

    /**
     * 统计查询中命中的关键词数量。
     * 对短词（长度 ≤ SHORT_WORD_MAX_LEN）启用边界匹配保护。
     */
    private int countKeywordHits(String lowerQuery, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            if (lowerKeyword.length() <= SHORT_WORD_MAX_LEN) {
                if (matchesWithBoundary(lowerQuery, lowerKeyword)) {
                    count++;
                }
            } else {
                if (lowerQuery.contains(lowerKeyword)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 短词边界匹配：仅对 1 字关键词启用前后边界检查。
     * 2 字关键词本身已具备足够语义特异性，无需边界保护。
     */
    private boolean matchesWithBoundary(String lowerQuery, String lowerKeyword) {
        if (lowerKeyword.length() >= 2) {
            return lowerQuery.contains(lowerKeyword);
        }

        int idx = lowerQuery.indexOf(lowerKeyword);
        while (idx >= 0) {
            boolean prefixOk = (idx == 0) || !isChineseChar(lowerQuery.charAt(idx - 1));
            int afterIdx = idx + lowerKeyword.length();
            boolean suffixOk = (afterIdx >= lowerQuery.length()) || !isChineseChar(lowerQuery.charAt(afterIdx));

            if (prefixOk && suffixOk) {
                return true;
            }
            idx = lowerQuery.indexOf(lowerKeyword, idx + 1);
        }
        return false;
    }

    private boolean isChineseChar(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }

    private Map<QueryIntent, Double> buildScores(double tool, double rag, double chitchat) {
        Map<QueryIntent, Double> scores = new HashMap<>();
        scores.put(QueryIntent.TOOL, tool);
        scores.put(QueryIntent.RAG, rag);
        scores.put(QueryIntent.CHITCHAT, chitchat);
        return scores;
    }
}
