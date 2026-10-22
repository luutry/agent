package com.luutry.agent.rag.chunker;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.rag.model.RagDocument;
import com.luutry.agent.rag.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HierarchicalChunker.
 * Covers: heading-based chunking, Chinese section chunking, paragraph fallback, overlap, oversized split.
 */
@DisplayName("HierarchicalChunker Tests")
class HierarchicalChunkerTest {

    private HierarchicalChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new HierarchicalChunker();
    }

    @Test
    @DisplayName("chunk - markdown headings create section paths")
    void testChunkMarkdownHeadings() {
        String content = """
                # 考勤管理制度
                总则说明
                
                ## 第一章 工作时间
                员工工作时间为9:00-18:00
                
                ## 第二章 请假制度
                请假需提前申请
                
                ### 事假
                事假需提前1天申请
                """;

        RagDocument doc = RagDocument.builder()
                .title("考勤制度")
                .content(content)
                .build();

        List<TextChunk> chunks = chunker.chunk(doc);
        assertFalse(chunks.isEmpty());
        // Should have chunks with section paths
        assertTrue(chunks.stream().anyMatch(c -> c.getSectionPath().contains("工作时间")));
        assertTrue(chunks.stream().anyMatch(c -> c.getSectionPath().contains("请假制度")));
    }

    @Test
    @DisplayName("chunk - Chinese sections create section paths")
    void testChunkChineseSections() {
        String content = """
                员工考勤管理制度
                
                第一章 总则
                本制度适用于全体员工。
                
                第二章 工作时间
                标准工作时间为每日8小时。
                
                第三章 请假制度
                请假需提前提交申请。
                """;

        RagDocument doc = RagDocument.builder()
                .title("考勤制度")
                .content(content)
                .build();

        List<TextChunk> chunks = chunker.chunk(doc);
        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().anyMatch(c -> c.getSectionPath().contains("第一章")));
    }

    @Test
    @DisplayName("chunk - plain text falls back to paragraph splitting")
    void testChunkPlainText() {
        String content = "这是第一段内容，关于考勤管理的基本规定。\n\n" +
                "这是第二段内容，关于请假流程的详细说明。\n\n" +
                "这是第三段内容，关于加班补偿的相关政策。";

        RagDocument doc = RagDocument.builder()
                .title("制度")
                .content(content)
                .build();

        List<TextChunk> chunks = chunker.chunk(doc);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> c.getContent() != null && !c.getContent().isBlank()));
    }

    @Test
    @DisplayName("chunk - empty content returns empty list")
    void testChunkEmpty() {
        RagDocument doc = RagDocument.builder().title("空文档").content("").build();
        assertTrue(chunker.chunk(doc).isEmpty());

        RagDocument doc2 = RagDocument.builder().title("空文档").content(null).build();
        assertTrue(chunker.chunk(doc2).isEmpty());
    }

    @Test
    @DisplayName("chunk - oversized sections are split by paragraph")
    void testChunkOversizedSplit() {
        // Create content with a single section exceeding MAX_CHUNK_CHARS
        StringBuilder sb = new StringBuilder();
        sb.append("# 大章节\n");
        for (int i = 0; i < 30; i++) {
            sb.append("这是第").append(i).append("段内容，用于测试超长段落的自动切分功能。\n\n");
        }

        RagDocument doc = RagDocument.builder()
                .title("长文档")
                .content(sb.toString())
                .build();

        List<TextChunk> chunks = chunker.chunk(doc);
        assertTrue(chunks.size() > 1, "Oversized section should be split into multiple chunks");
        assertTrue(chunks.stream().allMatch(c -> c.getContent().length() <= AgentConstants.RAG_CHUNK_MAX_CHARS + 100));
    }

    @Test
    @DisplayName("chunk - token count is calculated")
    void testChunkTokenCount() {
        String content = "# 标题\n这是测试内容。";
        RagDocument doc = RagDocument.builder().title("测试").content(content).build();

        List<TextChunk> chunks = chunker.chunk(doc);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> c.getTokenCount() >= 0));
    }

    @Test
    @DisplayName("chunk - null document throws NPE")
    void testChunkNull() {
        assertThrows(NullPointerException.class, () -> chunker.chunk(null));
    }
}
