package com.luutry.agent.rag.chunker;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.rag.model.RagDocument;
import com.luutry.agent.rag.model.TextChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 层次化文本切片器，保留文档章节结构。
 * <p>
 * 用途：将文档切分为片段，同时保持条款/章节层级，
 * 防止简单固定长度切片导致的语义断裂。
 * 处理问题：HR制度文档具有结构化章节（章、节、条），
 * 从条款中间切开会破坏语义。此切片器尊重章节边界。
 * 处理方式：
 * 1. 按Markdown标题（# ## ###）或编号章节（第X章, 第X节）分割
 * 2. 遍历文档时跟踪当前章节路径
 * 3. 如果章节超过MAX_CHUNK_CHARS，按段落分割
 * 4. 相邻切片之间添加重叠，保持上下文连续性
 * </p>
 */
@Slf4j
@Component
public class HierarchicalChunker {

    /**
     * 匹配Markdown标题的正则
     */
    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$", Pattern.MULTILINE);

    /**
     * 匹配中文编号章节的正则（第一章, 第二节, 第三条等）
     */
    private static final Pattern CN_SECTION = Pattern.compile("^(第[一二三四五六七八九十百]+[章节条])\\s*", Pattern.MULTILINE);

    /**
     * 段落分隔符（双换行）
     */
    private static final Pattern PARAGRAPH_SEP = Pattern.compile("\\n\\s*\\n");

    /**
     * 将文档切分为保留章节层级的文本片段。
     *
     * @param document 待切片的源文档
     * @return 带章节路径的文本切片列表
     */
    public List<TextChunk> chunk(RagDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        // 优先尝试按Markdown标题切片
        List<SectionBlock> blocks = splitByHeadings(content);

        // 未找到标题则尝试中文章节模式
        if (blocks.size() <= 1) {
            blocks = splitByChineseSections(content);
        }

        // 仍无结构则回退到按段落切片
        if (blocks.size() <= 1) {
            blocks = splitByParagraphs(content, "");
        }

        // 将章节块转换为切片，拆分超大块
        List<TextChunk> chunks = new ArrayList<>();
        int position = 0;
        for (SectionBlock block : blocks) {
            if (block.text.isBlank()) {
                continue;
            }
            List<TextChunk> blockChunks = splitOversized(block, position);
            chunks.addAll(blockChunks);
            position += blockChunks.size();
        }

        // 计算Token数
        chunks.forEach(TextChunk::calculateTokenCount);

        log.debug("Chunked document '{}' into {} chunks", document.getTitle(), chunks.size());
        return chunks;
    }

    /**
     * 按Markdown标题分割内容，跟踪章节层级。
     */
    List<SectionBlock> splitByHeadings(String content) {
        Matcher matcher = MD_HEADING.matcher(content);
        List<int[]> headingPositions = new ArrayList<>();
        List<String> headingTitles = new ArrayList<>();
        List<Integer> headingLevels = new ArrayList<>();

        while (matcher.find()) {
            headingPositions.add(new int[]{matcher.start(), matcher.end()});
            headingLevels.add(matcher.group(1).length());
            headingTitles.add(matcher.group(2).trim());
        }

        if (headingPositions.isEmpty()) {
            return List.of(new SectionBlock(content, ""));
        }

        List<SectionBlock> blocks = new ArrayList<>();
        // 第一个标题之前的内容（序言）
        if (headingPositions.get(0)[0] > 0) {
            String preamble = content.substring(0, headingPositions.get(0)[0]).trim();
            if (!preamble.isBlank()) {
                blocks.add(new SectionBlock(preamble, ""));
            }
        }

        // 跟踪章节层级
        String[] sectionStack = new String[3]; // levels 1, 2, 3
        for (int i = 0; i < headingPositions.size(); i++) {
            int level = headingLevels.get(i);
            int start = headingPositions.get(i)[1]; // after heading line
            int end = (i + 1 < headingPositions.size()) ? headingPositions.get(i + 1)[0] : content.length();

            sectionStack[level - 1] = headingTitles.get(i);
            // 清除更深层级
            for (int j = level; j < 3; j++) {
                sectionStack[j] = null;
            }

            String sectionPath = buildSectionPath(sectionStack);
            String text = content.substring(start, end).trim();
            if (!text.isBlank()) {
                blocks.add(new SectionBlock(text, sectionPath));
            }
        }

        return blocks;
    }

    /**
     * 按中文章节标记（第X章/节/条）分割内容。
     */
    List<SectionBlock> splitByChineseSections(String content) {
        Matcher matcher = CN_SECTION.matcher(content);
        List<int[]> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        while (matcher.find()) {
            positions.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(1));
        }

        if (positions.isEmpty()) {
            return List.of(new SectionBlock(content, ""));
        }

        List<SectionBlock> blocks = new ArrayList<>();
        // 序言
        if (positions.get(0)[0] > 0) {
            String preamble = content.substring(0, positions.get(0)[0]).trim();
            if (!preamble.isBlank()) {
                blocks.add(new SectionBlock(preamble, ""));
            }
        }

        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i)[1];
            int end = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : content.length();
            String text = content.substring(start, end).trim();
            if (!text.isBlank()) {
                blocks.add(new SectionBlock(text, titles.get(i)));
            }
        }

        return blocks;
    }

    /**
     * 按段落分割内容，使用给定的章节路径。
     */
    List<SectionBlock> splitByParagraphs(String content, String sectionPath) {
        String[] paragraphs = PARAGRAPH_SEP.split(content);
        List<SectionBlock> blocks = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isBlank()) continue;

            if (current.length() + trimmed.length() > AgentConstants.RAG_CHUNK_MAX_CHARS && current.length() > 0) {
                blocks.add(new SectionBlock(current.toString().trim(), sectionPath));
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(trimmed);
        }
        if (current.length() > 0) {
            blocks.add(new SectionBlock(current.toString().trim(), sectionPath));
        }

        return blocks;
    }

    /**
     * 将超大章节块拆分为多个切片。
     */
    private List<TextChunk> splitOversized(SectionBlock block, int startPosition) {
        List<TextChunk> chunks = new ArrayList<>();

        if (block.text.length() <= AgentConstants.RAG_CHUNK_MAX_CHARS) {
            chunks.add(TextChunk.builder()
                    .content(block.text)
                    .sectionPath(block.sectionPath)
                    .position(startPosition)
                    .build());
            return chunks;
        }

        // Split by paragraphs within the section
        String[] paragraphs = PARAGRAPH_SEP.split(block.text);
        StringBuilder current = new StringBuilder();
        int pos = startPosition;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isBlank()) continue;

            if (current.length() + trimmed.length() > AgentConstants.RAG_CHUNK_MAX_CHARS && current.length() > 0) {
                // 添加重叠部分以保持上下文连续
                String chunkContent = current.toString().trim();
                chunks.add(TextChunk.builder()
                        .content(chunkContent)
                        .sectionPath(block.sectionPath)
                        .position(pos++)
                        .build());

                // 重叠：保留末尾N个字符
                String overlap = getOverlap(chunkContent);
                current = new StringBuilder(overlap);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(trimmed);
        }
        if (current.length() > 0) {
            chunks.add(TextChunk.builder()
                    .content(current.toString().trim())
                    .sectionPath(block.sectionPath)
                    .position(pos)
                    .build());
        }

        return chunks;
    }

    /**
     * 获取内容末尾的重叠文本，用于上下文连续性。
     */
    private String getOverlap(String content) {
        if (content.length() <= AgentConstants.RAG_CHUNK_OVERLAP_CHARS) {
            return content;
        }
        return content.substring(content.length() - AgentConstants.RAG_CHUNK_OVERLAP_CHARS);
    }

    /**
     * 从章节栈构建章节路径。
     */
    private String buildSectionPath(String[] stack) {
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (s != null && !s.isBlank()) {
                if (sb.length() > 0) sb.append(">");
                sb.append(s);
            }
        }
        return sb.toString();
    }

    /**
     * 切片过程中的内部章节块持有者。
     */
    record SectionBlock(String text, String sectionPath) {
    }
}
