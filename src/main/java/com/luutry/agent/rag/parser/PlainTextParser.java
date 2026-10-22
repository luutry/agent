package com.luutry.agent.rag.parser;

import com.luutry.agent.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯文本和Markdown文档解析器实现。
 * <p>
 * 用途：将纯文本或Markdown格式的HR制度文档解析为RagDocument。
 * 处理问题：需要从原始文本内容中提取文档结构（标题、章节、元数据）；
 * Markdown标题（# ## ###）表示章节层级。
 * 处理方式：简单文本解析器，原样保留内容；检测Markdown标题以感知结构。
 * 元数据（版本、日期）从内容头部提取或使用默认值。
 * </p>
 * <p>
 * 支持格式："plaintext"、"markdown"
 * 未来：扩展Apache Tika以支持PDF/Word解析。
 * </p>
 */
@Slf4j
@Component
public class PlainTextParser implements DocumentParser {

    /**
     * 匹配Markdown标题的正则（# 标题, ## 章节, ### 条款）
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.+)$", Pattern.MULTILINE);

    /**
     * 提取版本信息的正则，如 "v1.0"、"v2.1"
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("[Vv](\\d+\\.\\d+)");

    /**
     * 提取日期信息的正则，如 "2024-01-01"、"2024年1月1日"
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[-年](\\d{1,2})[-月](\\d{1,2})");

    @Override
    public RagDocument parse(String title, String content, String category) {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");

        String version = extractVersion(content);
        LocalDate effectiveDate = extractFirstDate(content);

        RagDocument doc = RagDocument.builder()
                .title(title)
                .content(content)
                .category(category != null ? category : "general")
                .version(version)
                .effectiveDate(effectiveDate)
                .source("plaintext_parser")
                .build();

        log.debug("Parsed document: title='{}', version={}, category={}, content length={}",
                title, version, category, content.length());
        return doc;
    }

    @Override
    public boolean supports(String format) {
        return "plaintext".equalsIgnoreCase(format) || "markdown".equalsIgnoreCase(format);
    }

    /**
     * 从内容头部区域提取版本字符串。
     */
    private String extractVersion(String content) {
        // 仅在前500个字符内搜索版本信息
        String header = content.length() > 500 ? content.substring(0, 500) : content;
        Matcher matcher = VERSION_PATTERN.matcher(header);
        if (matcher.find()) {
            return "v" + matcher.group(1);
        }
        return "v1.0";
    }

    /**
     * 提取内容中的第一个日期作为生效日期。
     */
    private LocalDate extractFirstDate(String content) {
        String header = content.length() > 500 ? content.substring(0, 500) : content;
        Matcher matcher = DATE_PATTERN.matcher(header);
        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                log.trace("Failed to parse date from content: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 统计Markdown文档中的标题数量。
     * 可用于切片过程中的结构分析。
     */
    public int countHeadings(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        Matcher matcher = HEADING_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
