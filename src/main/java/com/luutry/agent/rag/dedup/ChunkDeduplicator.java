package com.luutry.agent.rag.dedup;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.rag.model.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 切片去重器，移除近似重复的检索结果。
 * <p>
 * 用途：消除混合检索中BM25和稠密向量检索可能对同一内容返回重叠结果的冗余。
 * 处理问题：重复切片浪费Token预算，并可能导致LLM偏向重复信息。
 * 去重确保上下文多样、无冗余。
 * 处理方式：计算切片对之间字符n-gram的Jaccard相似度；
 * 如果相似度超过阈值（0.8），保留分数较高的那个。
 * </p>
 */
@Slf4j
@Component
public class ChunkDeduplicator {

    /**
     * 对检索结果去重，移除近似重复的切片。
     *
     * @param results 检索结果列表（可能包含重复）
     * @return 去重后的列表，保留最高分条目
     */
    public List<RetrievalResult> deduplicate(List<RetrievalResult> results) {
        if (results == null || results.size() <= 1) {
            return results != null ? results : Collections.emptyList();
        }

        // 先按分数降序排列（保留最佳匹配）
        List<RetrievalResult> sorted = results.stream()
                .sorted(Comparator.comparingDouble(RetrievalResult::getScore).reversed())
                .collect(Collectors.toList());

        List<RetrievalResult> kept = new ArrayList<>();
        List<Set<String>> keptNgrams = new ArrayList<>();

        for (RetrievalResult candidate : sorted) {
            Set<String> candidateNgrams = getCharNgrams(candidate.getChunk().getContent(), 3);

            boolean isDuplicate = false;
            for (Set<String> existingNgrams : keptNgrams) {
                double similarity = jaccardSimilarity(candidateNgrams, existingNgrams);
                if (similarity >= AgentConstants.RAG_DEDUP_THRESHOLD) {
                    isDuplicate = true;
                    log.trace("Dedup: removing chunk with similarity={}", String.format("%.2f", similarity));
                    break;
                }
            }

            if (!isDuplicate) {
                kept.add(candidate);
                keptNgrams.add(candidateNgrams);
            }
        }

        if (kept.size() < sorted.size()) {
            log.debug("Dedup: {} results reduced to {} (removed {} duplicates)",
                    sorted.size(), kept.size(), sorted.size() - kept.size());
        }

        return kept;
    }

    /**
     * 计算两个集合之间的Jaccard相似度。
     */
    double jaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0;
        }
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }

        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;
        return union > 0 ? (double) intersection / union : 0.0;
    }

    /**
     * 从文本生成字符n-gram。
     */
    Set<String> getCharNgrams(String text, int n) {
        if (text == null || text.length() < n) {
            return text != null ? Set.of(text.toLowerCase()) : Collections.emptySet();
        }
        String lower = text.toLowerCase().replaceAll("\\s+", " ");
        Set<String> ngrams = new HashSet<>();
        for (int i = 0; i <= lower.length() - n; i++) {
            ngrams.add(lower.substring(i, i + n));
        }
        return ngrams;
    }
}
