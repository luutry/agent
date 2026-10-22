package com.luutry.agent.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 文本切片模型，表示RAG文档的一个片段。
 * <p>
 * 用途：表示从文档中提取的文本片段，保留层次化章节信息，
 * 用于精确的来源归属。
 * 处理问题：检索相关信息时，需要知道文本来自哪个具体章节/条款，
 * 而不仅仅是文档标题。
 * 处理方式：每个切片携带章节路径（如 "第三章>第二节>考勤规则"）
 * 和在文档中的位置索引。
 * </p>
 */
@Data
@Builder
public class TextChunk {

    /**
     * 切片唯一标识符
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * 父文档ID
     */
    private String docId;

    /**
     * 切片文本内容
     */
    private String content;

    /**
     * 层次化章节路径（如 "第三章>第二节>考勤规则"）
     */
    @Builder.Default
    private String sectionPath = "";

    /**
     * 在文档中的位置索引（从0开始）
     */
    private int position;

    /**
     * 该切片的估算Token数
     */
    @Builder.Default
    private int tokenCount = 0;

    /**
     * 检索相关性分数（由检索器/重排器设置）
     */
    @Builder.Default
    private double score = 0.0;

    /**
     * 根据内容计算估算Token数（字符数 / 4）。
     */
    public void calculateTokenCount() {
        this.tokenCount = content != null ? content.length() / 4 : 0;
    }
}
