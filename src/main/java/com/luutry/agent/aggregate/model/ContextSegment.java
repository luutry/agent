package com.luutry.agent.aggregate.model;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.TokenEstimator;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 统一上下文片段，表示来自任意数据源的一条信息。
 * <p>
 * 用途：将所有上下文输入（Tool结果、RAG片段、Memory条目）标准化为单一模型，
 * 供上下文聚合器统一处理。
 * 处理问题：不同数据源有不同格式；需要通用包装器
 * 携带元数据（来源、分数、Token数、溯源）用于排序、去重和预算控制。
 * 处理方式：每个片段携带内容文本、来源标签、相关性分数、估算Token数、
 * 可选溯源引用和时间戳用于排序。
 * </p>
 */
@Data
@Builder
public class ContextSegment {

    /**
     * 该片段的文本内容
     */
    private String content;

    /**
     * 数据源标签（TOOL、RAG或MEMORY）
     */
    @Builder.Default
    private DataSource source = DataSource.TOOL;

    /**
     * 相关性/置信度分数（越高越相关）
     */
    @Builder.Default
    private double score = 0.0;

    /**
     * 该片段内容的估算Token数
     */
    @Builder.Default
    private int tokenCount = 0;

    /**
     * 溯源引用（如"[考勤管理制度v2.1, 第三章>第二节]"或"Tool:employee_search"）
     */
    private String provenance;

    /**
     * 该片段创建/检索时的时间戳
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * 从Tool结果创建ContextSegment。
     *
     * @param content    结果文本内容
     * @param score      置信度分数
     * @param provenance 工具名称或描述
     * @return 标记为TOOL来源的ContextSegment
     */
    public static ContextSegment fromTool(String content, double score, String provenance) {
        return ContextSegment.builder()
                .content(content)
                .source(DataSource.TOOL)
                .score(score)
                .tokenCount(TokenEstimator.estimate(content))
                .provenance(provenance)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 从RAG检索结果创建ContextSegment。
     *
     * @param content    Chunk文本内容
     * @param score      检索的相关性分数
     * @param provenance 文档引用字符串
     * @return 标记为RAG来源的ContextSegment
     */
    public static ContextSegment fromRag(String content, double score, String provenance) {
        return ContextSegment.builder()
                .content(content)
                .source(DataSource.RAG)
                .score(score)
                .tokenCount(TokenEstimator.estimate(content))
                .provenance(provenance)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 从Memory条目创建ContextSegment。
     *
     * @param content    记忆内容文本
     * @param score      衰减分数或相关性分数
     * @param provenance 记忆标签或类型描述
     * @return 标记为MEMORY来源的ContextSegment
     */
    public static ContextSegment fromMemory(String content, double score, String provenance) {
        return ContextSegment.builder()
                .content(content)
                .source(DataSource.MEMORY)
                .score(score)
                .tokenCount(TokenEstimator.estimate(content))
                .provenance(provenance)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 基于当前内容重新计算并更新Token数。
     */
    public void recalculateTokens() {
        this.tokenCount = TokenEstimator.estimate(content);
    }
}
