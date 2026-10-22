package com.luutry.agent.rag.parser;

import com.luutry.agent.rag.model.RagDocument;

/**
 * 文档解析器接口，将原始文本转换为结构化RagDocument。
 * <p>
 * 用途：定义不同文档格式的解析规范，统一转换为带元数据的RagDocument模型。
 * 处理问题：HR制度文档有多种格式（纯文本、Markdown、PDF、Word）；
 * 此接口允许为每种格式插拔不同的解析器。
 * 处理方式：每种解析器实现处理特定格式，并提取结构信息（标题、章节、条款）到文档模型中。
 * </p>
 */
public interface DocumentParser {

    /**
     * 将原始文本内容解析为结构化RagDocument。
     *
     * @param title    文档标题
     * @param content  原始文本内容
     * @param category 文档类别
     * @return 带元数据的已解析RagDocument
     */
    RagDocument parse(String title, String content, String category);

    /**
     * 检查此解析器是否支持给定格式。
     *
     * @param format 格式标识符（如 "markdown"、"plaintext"、"pdf"）
     * @return 支持时返回true
     */
    boolean supports(String format);
}
