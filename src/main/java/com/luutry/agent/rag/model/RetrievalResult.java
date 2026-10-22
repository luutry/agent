package com.luutry.agent.rag.model;

import com.luutry.agent.common.enums.DataSource;
import lombok.Builder;
import lombok.Data;

/**
 * 检索结果，封装文本切片及评分和溯源元数据。
 * <p>
 * 用途：封装单次检索命中，携带下游消费所需的完整上下文。
 * 处理问题：上下文聚合器不仅需要切片文本，还需要相关性分数、
 * 数据来源标签和溯源信息（哪个文档、哪个章节、哪个版本），
 * 用于来源归属和可信度排序。
 * 处理方式：在TextChunk基础上包装额外元数据；实现Comparable以支持按分数排序。
 * </p>
 */
@Data
@Builder
public class RetrievalResult implements Comparable<RetrievalResult> {

    /**
     * 匹配的文本切片
     */
    private TextChunk chunk;

    /**
     * 相关性分数（越高越相关）
     */
    @Builder.Default
    private double score = 0.0;

    /**
     * 数据来源标签（检索结果始终为RAG）
     */
    @Builder.Default
    private DataSource dataSource = DataSource.RAG;

    /**
     * 溯源用的来源文档标题
     */
    private String docTitle;

    /**
     * 溯源用的来源文档版本
     */
    private String docVersion;

    /**
     * 格式化溯源字符串，用于引用（如 "[考勤管理制度v2.1, 第三章>第二节]"）
     */
    private String provenance;

    /**
     * 根据文档和切片信息构建溯源字符串。
     */
    public static String buildProvenance(String docTitle, String docVersion, String sectionPath) {
        StringBuilder sb = new StringBuilder("[");
        sb.append(docTitle != null ? docTitle : "Unknown");
        if (docVersion != null && !docVersion.isBlank()) {
            sb.append(" ").append(docVersion);
        }
        if (sectionPath != null && !sectionPath.isBlank()) {
            sb.append(", ").append(sectionPath);
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public int compareTo(RetrievalResult other) {
        return Double.compare(other.score, this.score);
    }
}
