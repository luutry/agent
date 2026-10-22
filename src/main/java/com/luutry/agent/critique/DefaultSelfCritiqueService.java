package com.luutry.agent.critique;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.aggregate.model.FactSet;
import com.luutry.agent.critique.model.CritiqueResult;
import com.luutry.agent.critique.model.CritiqueVerdict;
import com.luutry.agent.planner.model.AgentResponse;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SelfCritiqueService默认实现。
 * <p>
 * 用途：验证Agent回答的幻觉、冲突和完整性。
 * 处理问题：Agent可能编造上下文不支持的数据、产生来自不同源的冲突信息，
 * 或未能解决用户Query。
 * 处理方式：三阶段验证：
 * 1. 幻觉检测：将回答拆分为句子，逐句检查上下文片段
 * 2. 冲突检测：检查TOOL和RAG片段是否包含矛盾的数值
 * 3. 完整性检查：验证回答是否包含原始Query中的关键实体
 * 最终判定：幻觉分数低于阈值则REJECT，存在冲突/不完整则WARNING，否则PASS。
 * </p>
 */
@Service
public class DefaultSelfCritiqueService implements SelfCritiqueService {

    /**
     * 从文本中提取带可选单位的数值的正则模式。
     */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([%％万亿元个条人次])?");

    @Override
    public CritiqueResult critique(AgentResponse response, AggregatedContext context, ProcessedQuery query) {
        if (response == null || response.getAnswer() == null || response.getAnswer().isBlank()) {
            return CritiqueResult.reject("Empty or null answer", "抱歉，未能生成有效回答，请重试。");
        }

        String answer = response.getAnswer();
        CritiqueResult result = CritiqueResult.builder()
                .verdict(CritiqueVerdict.PASS)
                .confidence(1.0)
                .issues(new ArrayList<>())
                .hallucinationScore(1.0)
                .hasConflict(false)
                .incomplete(false)
                .build();

        // 阶段1：幻觉检测（模板路径按渲染绑定的 factKey 集合比对；其余按 Fact 三元组精确校验，杜绝张冠李戴 R5）
        double hallucinationScore = detectHallucination(answer, context,
                response.getFactSet(), response.getUsedFactKeys());
        result.setHallucinationScore(hallucinationScore);

        if (hallucinationScore < AgentConstants.CRITIQUE_HALLUCINATION_THRESHOLD) {
            result.setVerdict(CritiqueVerdict.REJECT);
            result.setConfidence(0.0);
            result.addIssue("Hallucination detected: only " + String.format("%.0f%%", hallucinationScore * 100)
                    + " of answer is supported by context");
            result.setRevisedAnswer("抱歉，当前信息不足以回答您的问题，请提供更多细节或换个方式提问。");
            return result;
        }

        if (hallucinationScore < 0.8) {
            result.addIssue("Partial hallucination: " + String.format("%.0f%%", hallucinationScore * 100)
                    + " of answer is supported by context");
            result.setConfidence(result.getConfidence() - 0.2);
        }

        // 阶段2：冲突检测
        boolean hasConflict = detectConflict(context);
        result.setHasConflict(hasConflict);

        if (hasConflict) {
            result.addIssue("Conflicting information detected between data sources");
            result.setConfidence(result.getConfidence() - 0.15);
        }

        // 阶段3：完整性检查
        boolean isIncomplete = detectIncompleteness(answer, query);
        result.setIncomplete(isIncomplete);

        if (isIncomplete) {
            result.addIssue("Answer may be incomplete: missing key entities from the query");
            result.setConfidence(result.getConfidence() - 0.1);
        }

        // 最终判定结果确定
        if (result.getConfidence() < 0.5) {
            result.setVerdict(CritiqueVerdict.REJECT);
            result.setRevisedAnswer("回答存在较多不确定性，建议核实后参考。");
        } else if (result.hasIssues()) {
            result.setVerdict(CritiqueVerdict.WARNING);
        }

        result.setConfidence(Math.max(0.0, result.getConfidence()));
        return result;
    }

    /**
     * 检测回答是否存在幻觉。
     * <p>
     * 有结构化事实（FactSet 非空，来自工具的结构化数据）时，按 Fact 三元组精确校验：
     * 含数值的句子必须全部命中，任一未命中（数值不在事实集或张冠李戴）即返回 0.0（R5），不做比例放行。
     * 无结构化事实时（RAG/闲聊等）回退文本子串/bigram 与数值验证，按支持比例计分。
     * </p>
     *
     * @param answer  Agent的回答文本
     * @param context 聚合上下文
     * @param factSet 从执行产物提取的事实集合，可为空
     * @param usedFactKeys 模板渲染时绑定的事实键（entity#metric），非空时走零解析集合比对
     * @return 事实校验路径返回 0.0 或 1.0；文本回退路径返回被支持的句子比例
     */
    private double detectHallucination(String answer, AggregatedContext context, FactSet factSet,
                                       Set<String> usedFactKeys) {
        boolean useFacts = factSet != null && !factSet.isEmpty();

        // 模板渲染路径：渲染时已把每个数值绑定到 factKey，校验退化为集合比对，零自然语言解析（复审 N5）。
        if (useFacts && usedFactKeys != null && !usedFactKeys.isEmpty()) {
            Set<String> validKeys = factSet.keys();
            for (String key : usedFactKeys) {
                if (!validKeys.contains(key)) {
                    return 0.0;
                }
            }
            return 1.0;
        }

        List<String> sentences = splitSentences(answer);
        if (sentences.isEmpty()) {
            if (useFacts) {
                return 1.0;
            }
            return (context == null || context.isEmpty()) && containsSpecificData(answer) ? 0.0 : 1.0;
        }

        if (useFacts) {
            // R5：含数值的句子必须 100% 被事实支撑，任一未命中即判幻觉，直接归零；
            // 不用"支持比例"放行，杜绝"两条真数据夹一条编造"（复审 N1）。
            // 无数值的元话语句经 verifyByFacts 天然返回 true，不参与判定。
            for (String sentence : sentences) {
                if (!verifyByFacts(sentence, factSet)) {
                    return 0.0;
                }
            }
            return 1.0;
        }

        if (context == null || context.isEmpty()) {
            // 无可用上下文：如果回答包含具体数据，则可能是幻觉
            return containsSpecificData(answer) ? 0.0 : 1.0;
        }

        String contextText = buildContextText(context);
        int supportedCount = 0;
        for (String sentence : sentences) {
            if (isSupported(sentence, contextText) || verifyNumericValues(sentence, contextText)) {
                supportedCount++;
            }
        }
        return (double) supportedCount / sentences.size();
    }

    /**
     * 按 Fact 三元组精确校验单句：句中每个数值都必须确属于其就近前置的实体。
     * 无数值主张时视为通过；数值归属错误（张冠李戴）或数值不在事实集合中时判为幻觉。
     */
    private boolean verifyByFacts(String sentence, FactSet factSet) {
        List<Double> values = new ArrayList<>();
        List<Integer> valuePositions = new ArrayList<>();
        Matcher matcher = NUMERIC_PATTERN.matcher(sentence);
        while (matcher.find()) {
            try {
                double num = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2);
                if ("%".equals(unit) || "％".equals(unit)) {
                    num = num / 100.0;
                }
                values.add(num);
                valuePositions.add(matcher.start());
            } catch (NumberFormatException ignored) {
                // 跳过无法解析的数值
            }
        }
        if (values.isEmpty()) {
            return true;
        }

        List<String> entityNames = new ArrayList<>();
        List<Integer> entityPositions = new ArrayList<>();
        for (String entity : factSet.entities()) {
            int idx = sentence.indexOf(entity);
            if (idx >= 0) {
                entityNames.add(entity);
                entityPositions.add(idx);
            }
        }

        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            String owner = nearestPrecedingEntity(entityNames, entityPositions, valuePositions.get(i));
            if (owner != null) {
                if (!factSet.containsEntityValue(owner, value)) {
                    return false;
                }
            } else if (!factSetContainsValue(factSet, value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 找到数值位置之前最近出现的实体名，无则返回 null。
     */
    private String nearestPrecedingEntity(List<String> names, List<Integer> positions, int valuePos) {
        String best = null;
        int bestPos = -1;
        for (int i = 0; i < names.size(); i++) {
            int p = positions.get(i);
            if (p < valuePos && p > bestPos) {
                best = names.get(i);
                bestPos = p;
            }
        }
        return best;
    }

    /**
     * 判断数值是否在事实集合的任一数值中出现（容差内）。
     */
    private boolean factSetContainsValue(FactSet factSet, double value) {
        for (Double v : factSet.allNumericValues()) {
            if (Math.abs(v - value) <= FactSet.VALUE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通过检查TOOL与RAG片段中是否包含矛盾的数值来检测冲突。
     *
     * @param context 聚合上下文
     * @return 如果检测到冲突则返回true
     */
    private boolean detectConflict(AggregatedContext context) {
        if (context == null || context.isEmpty()) {
            return false;
        }

        List<String> toolValues = extractNumericValues(context.getSegmentsBySource(
                com.luutry.agent.common.enums.DataSource.TOOL));
        List<String> ragValues = extractNumericValues(context.getSegmentsBySource(
                com.luutry.agent.common.enums.DataSource.RAG));

        if (toolValues.isEmpty() || ragValues.isEmpty()) {
            return false;
        }

        // 检查TOOL的数值是否与RAG冲突
        for (String toolVal : toolValues) {
            for (String ragVal : ragValues) {
                if (toolVal.equals(ragVal)) {
                    // 两个源中找到相同值 - 如果上下文不同则可能是冲突
                    // 简化处理：如果相同数字出现在不同周围文本中则标记
                    continue;
                }
                // 检查数值是否显著不同（超过2倍差异）
                try {
                    double toolNum = Double.parseDouble(toolVal);
                    double ragNum = Double.parseDouble(ragVal);
                    if (toolNum > 0 && ragNum > 0) {
                        double ratio = Math.max(toolNum, ragNum) / Math.min(toolNum, ragNum);
                        if (ratio > 2.0) {
                            return true;
                        }
                    }
                } catch (NumberFormatException e) {
                    // 跳过非数值
                }
            }
        }

        return false;
    }

    /**
     * 通过检查回答是否提及Query中的关键实体来检测不完整性。
     *
     * @param answer Agent的回答
     * @param query  原始处理后的Query
     * @return 如果回答似乎不完整则返回true
     */
    private boolean detectIncompleteness(String answer, ProcessedQuery query) {
        if (query == null || query.getEntities().isEmpty()) {
            return false;
        }

        int missingEntities = 0;
        String answerLower = answer.toLowerCase();

        for (String entity : query.getEntities()) {
            if (entity != null && !entity.isBlank() && !answerLower.contains(entity.toLowerCase())) {
                missingEntities++;
            }
        }

        // 如果超过一半的实体缺失，则回答不完整
        double missingRatio = (double) missingEntities / query.getEntities().size();
        return missingRatio > 0.5;
    }

    /**
     * 使用中英文标点将文本拆分为句子。
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 按中文句号、感叹号、问号或英文对应符号拆分
        String[] parts = text.split("[。！？；;\\n]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 4) { // 跳过过短的片段
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * 检查句子是否被上下文文本支持。
     * 使用子串匹配：如果句子（或其大部分）出现在上下文中。
     */
    private boolean isSupported(String sentence, String contextText) {
        if (sentence.isBlank() || contextText.isBlank()) {
            return false;
        }

        String normalizedSentence = sentence.replaceAll("\\s+", "");
        String normalizedContext = contextText.replaceAll("\\s+", "");

        // 直接子串匹配
        if (normalizedContext.contains(normalizedSentence)) {
            return true;
        }

        // 检查句子至少60%的字符是否出现在上下文中
        int matchCount = 0;
        for (int i = 0; i < normalizedSentence.length() - 1; i++) {
            String bigram = normalizedSentence.substring(i, i + 2);
            if (normalizedContext.contains(bigram)) {
                matchCount++;
            }
        }

        double matchRatio = normalizedSentence.length() > 2
                ? (double) matchCount / (normalizedSentence.length() - 1)
                : 0.0;

        return matchRatio >= 0.6;
    }

    /**
     * 从所有上下文片段构建单一匹配文本。
     */
    private String buildContextText(AggregatedContext context) {
        StringBuilder sb = new StringBuilder();
        for (ContextSegment segment : context.getSegments()) {
            if (segment.getContent() != null) {
                sb.append(segment.getContent()).append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * 验证回答句子中的数值是否出现在上下文中。
     * 处理LLM将原始数据改写为自然语言后子串匹配失败的场景。
     * 例如：上下文 "turnoverRate=0.05" vs 回答 "离职率为5%" → 归一化后均为5.0，匹配成功。
     */
    private boolean verifyNumericValues(String sentence, String contextText) {
        Set<Double> sentenceValues = extractNormalizedNumericValues(sentence);
        if (sentenceValues.isEmpty()) {
            return false;
        }

        Set<Double> contextValues = extractNormalizedNumericValues(contextText);
        if (contextValues.isEmpty()) {
            return false;
        }

        // 句子中至少有一个数值能在上下文中找到匹配
        for (Double sVal : sentenceValues) {
            for (Double cVal : contextValues) {
                if (Math.abs(sVal - cVal) < 0.01) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 从文本中提取数值并归一化（百分比转为小数值）。
     * 例如："离职率5%" → {5.0}, "占比0.05" → {0.05}, "150人" → {150.0}
     */
    private Set<Double> extractNormalizedNumericValues(String text) {
        Set<Double> values = new HashSet<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        Matcher matcher = NUMERIC_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                double num = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2);
                if ("%".equals(unit) || "％".equals(unit)) {
                    num = num / 100.0;
                }
                values.add(num);
            } catch (NumberFormatException e) {
                // 跳过无法解析的数值
            }
        }
        return values;
    }

    /**
     * 检查回答是否包含需要上下文支持的具体数据值（数字、名称等）。
     */
    private boolean containsSpecificData(String text) {
        Matcher matcher = NUMERIC_PATTERN.matcher(text);
        return matcher.find();
    }

    /**
     * 从上下文片段列表中提取所有数值。
     */
    private List<String> extractNumericValues(List<ContextSegment> segments) {
        List<String> values = new ArrayList<>();
        for (ContextSegment segment : segments) {
            if (segment.getContent() != null) {
                Matcher matcher = NUMERIC_PATTERN.matcher(segment.getContent());
                while (matcher.find()) {
                    values.add(matcher.group(1));
                }
            }
        }
        return values;
    }
}
