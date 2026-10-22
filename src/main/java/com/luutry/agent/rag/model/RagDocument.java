package com.luutry.agent.rag.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * RAG文档模型，表示HR制度文档。
 * <p>
 * 用途：封装知识库存储和检索所需的文档元数据。
 * 处理问题：HR制度文档具有版本控制、生效日期和过期时间；
 * 系统必须过滤过期文档，并能追溯到具体版本。
 * 处理方式：每个文档携带完整的生命周期元数据（版本、生效/过期日期、
 * 类别），以支持基于时间的过滤和来源归属。
 * </p>
 */
@Data
@Builder
public class RagDocument {

    /**
     * 文档唯一标识符
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * 文档标题（如 "员工考勤管理制度"）
     */
    private String title;

    /**
     * 文档版本（如 "v2.1"）
     */
    @Builder.Default
    private String version = "v1.0";

    /**
     * 该版本的生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 该版本的过期日期（null表示永不过期）
     */
    private LocalDate expireDate;

    /**
     * 文档类别（如 "attendance"、"performance"、"onboarding"）
     */
    private String category;

    /**
     * 文档的完整原始文本内容
     */
    private String content;

    /**
     * 来源标识符（如文件路径、URL或系统名称）
     */
    @Builder.Default
    private String source = "knowledge_base";

    /**
     * 检查该文档当前是否有效（未过期）。
     */
    public boolean isEffective() {
        LocalDate now = LocalDate.now();
        if (effectiveDate != null && now.isBefore(effectiveDate)) {
            return false;
        }
        if (expireDate != null && now.isAfter(expireDate)) {
            return false;
        }
        return true;
    }
}
