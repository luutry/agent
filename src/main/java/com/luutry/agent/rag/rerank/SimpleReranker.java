package com.luutry.agent.rag.rerank;

import com.luutry.agent.rag.model.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 简单重排器，综合检索分数、时效性和章节匹配。
 * <p>
 * 用途：使用多个加权信号优化检索结果。
 * 处理问题：纯检索分数可能偏向旧文档；此重排器在相关性基础上
 * 平衡文档新鲜度和结构匹配质量。
 * 公式：finalScore = retrievalScore * 0.7 + recencyScore * 0.2 + sectionMatchScore * 0.1
 * </p>
 * <p>
 * TODO: 生产环境替换为基于交叉编码器的重排器。
 * </p>
 */
@Slf4j
@Component
public class SimpleReranker implements ChunkReranker {

    /**
     * 原始检索分数的权重
     */
    private static final double RETRIEVAL_WEIGHT = 0.7;

    /**
     * 文档时效性分数的权重
     */
    private static final double RECENCY_WEIGHT = 0.2;

    /**
     * 章节路径匹配分数的权重
     */
    private static final double SECTION_WEIGHT = 0.1;

    /**
     * 时效性加分的最大天数窗口（新于此天数的文档获得满分加分）
     */
    private static final long MAX_RECENCY_DAYS = 365;

    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }
        if (query == null || query.isBlank()) {
            return new ArrayList<>(results);
        }

        Set<String> queryTerms = tokenize(query);

        return results.stream()
                .map(r -> {
                    double retrievalScore = normalizeScore(r.getScore());
                    double recencyScore = computeRecencyScore(r);
                    double sectionScore = computeSectionScore(r.getChunk().getSectionPath(), queryTerms);

                    double finalScore = retrievalScore * RETRIEVAL_WEIGHT
                            + recencyScore * RECENCY_WEIGHT
                            + sectionScore * SECTION_WEIGHT;

                    r.setScore(finalScore);
                    return r;
                })
                .sorted(Comparator.comparingDouble(RetrievalResult::getScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 使用Sigmoid类函数将检索分数归一化到[0, 1]范围。
     */
    private double normalizeScore(double score) {
        return 1.0 / (1.0 + Math.exp(-score * 5));
    }

    /**
     * 根据文档生效日期计算时效性分数。
     * 较新的文档获得更高分数。
     */
    private double computeRecencyScore(RetrievalResult result) {
        // 无日期信息时给中性分数
        if (result.getDocTitle() == null) {
            return 0.5;
        }
        // Mock实现：无法直接从RetrievalResult获取日期，
        // 给统一分数。生产中会从RagDocument读取。
        return 0.5;
    }

    /**
     * 计算章节路径匹配分数。
     * 章节路径包含查询相关词时得分更高。
     */
    private double computeSectionScore(String sectionPath, Set<String> queryTerms) {
        if (sectionPath == null || sectionPath.isBlank() || queryTerms.isEmpty()) {
            return 0.0;
        }

        String pathLower = sectionPath.toLowerCase();
        long matches = queryTerms.stream().filter(pathLower::contains).count();
        return Math.min(1.0, (double) matches / queryTerms.size());
    }

    /**
     * 简单的分词器，用于提取查询词。
     */
    private Set<String> tokenize(String text) {
        return Set.of(text.toLowerCase().split("[\\s,;.!?，。；！？]+"))
                .stream()
                .filter(s -> s.length() >= 2)
                .collect(Collectors.toSet());
    }
}
