package com.luutry.agent.aggregate.model;

import com.luutry.agent.common.enums.DataSource;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 经过合并、排序、去重和Token预算裁剪后的聚合上下文结果。
 * <p>
 * 用途：表示可供LLM消费的最终统一上下文。
 * 处理问题：反思校验层和回答生成器需要整合的、预算受控的上下文，
 * 并带有完整的溯源追踪信息用于来源归因。
 * 处理方式：包含有序的ContextSegment列表、总Token数、
 * 溯源引用列表和来源分布统计。
 * </p>
 */
@Data
@Builder
public class AggregatedContext {

    /**
     * 有序的上下文片段列表（按优先级然后按分数排序）
     */
    @Builder.Default
    private List<ContextSegment> segments = new ArrayList<>();

    /**
     * 所有片段的总估算Token数
     */
    @Builder.Default
    private int totalTokens = 0;

    /**
     * 每个片段的溯源引用列表（与segments列表并行）
     */
    @Builder.Default
    private List<String> provenanceList = new ArrayList<>();

    /**
     * 按数据源统计的片段分布（DataSource -> 数量）
     */
    @Builder.Default
    private Map<DataSource, Integer> sourceDistribution = Collections.emptyMap();

    /**
     * 上下文是否因Token预算限制而被截断
     */
    @Builder.Default
    private boolean truncated = false;

    /**
     * 获取片段总数
     */
    public int getSegmentCount() {
        return segments.size();
    }

    /**
     * 检查上下文是否为空（无片段）
     */
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * 按指定数据源过滤获取片段。
     *
     * @param source 要过滤的数据源
     * @return 指定来源的片段列表
     */
    public List<ContextSegment> getSegmentsBySource(DataSource source) {
        return segments.stream()
                .filter(s -> s.getSource() == source)
                .collect(Collectors.toList());
    }

    /**
     * 构建聚合上下文的格式化摘要，用于LLM Prompt注入。
     *
     * @return 包含所有片段及其溯源的格式化字符串
     */
    public String toPromptString() {
        if (segments.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            ContextSegment seg = segments.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append("(").append(seg.getSource().getDescription()).append(") ");
            sb.append(seg.getContent());
            if (seg.getProvenance() != null && !seg.getProvenance().isBlank()) {
                sb.append(" -- ").append(seg.getProvenance());
            }
            if (i < segments.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 从当前片段列表计算来源分布。
     *
     * @return DataSource到片段数量的映射
     */
    public Map<DataSource, Integer> computeDistribution() {
        return segments.stream()
                .collect(Collectors.groupingBy(ContextSegment::getSource, Collectors.counting()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue()));
    }
}
