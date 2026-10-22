package com.luutry.agent.rag;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.rag.chunker.HierarchicalChunker;
import com.luutry.agent.rag.dedup.ChunkDeduplicator;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.parser.PlainTextParser;
import com.luutry.agent.rag.rerank.SimpleReranker;
import com.luutry.agent.rag.retriever.HybridRetriever;
import com.luutry.agent.rag.retriever.bm25.Bm25Retriever;
import com.luutry.agent.rag.retriever.dense.DenseRetriever;
import com.luutry.agent.rag.source.ProvenanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for RagService.
 * Covers: full pipeline (ingest → retrieve → rerank → dedup → provenance → budget).
 */
@DisplayName("RagService Tests")
class RagServiceTest {

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(
                new PlainTextParser(),
                new HierarchicalChunker(),
                new HybridRetriever(new Bm25Retriever(), new DenseRetriever()),
                new SimpleReranker(),
                new ChunkDeduplicator(),
                new ProvenanceTracker()
        );
    }

    @Test
    @DisplayName("ingestDocument - parses, chunks, and indexes")
    void testIngestDocument() {
        String content = """
                # 员工考勤管理制度 v2.0
                生效日期: 2024-01-01
                
                ## 第一章 工作时间
                公司标准工作时间为每日9:00至18:00，午休1小时。
                员工需按时打卡考勤。
                
                ## 第二章 请假制度
                员工请假需提前提交书面申请。
                事假需提前1天申请，病假需提供医院证明。
                年假按国家规定执行。
                
                ## 第三章 加班管理
                加班需提前向直属领导申请。
                加班工资按1.5倍计算，节假日按3倍计算。
                """;

        ragService.ingestDocument("考勤管理制度", content, "attendance");

        assertEquals(1, ragService.getDocumentCount());
        assertTrue(ragService.getIndexedChunkCount() > 0);
    }

    @Test
    @DisplayName("retrieve - full pipeline returns results with provenance")
    void testRetrieveFullPipeline() {
        // Ingest test documents
        ragService.ingestDocument("考勤制度",
                "# 考勤制度\n## 年假\n员工每年享受5天年假。\n## 事假\n事假需提前1天申请。",
                "attendance");
        ragService.ingestDocument("绩效制度",
                "# 绩效制度\n## 考核周期\n每季度进行一次绩效考核。\n## 评分标准\n分为ABCD四个等级。",
                "performance");

        // Retrieve
        List<RetrievalResult> results = ragService.retrieve("年假");
        assertFalse(results.isEmpty());

        // Check provenance
        RetrievalResult first = results.get(0);
        assertNotNull(first.getProvenance());
        assertNotNull(first.getDocTitle());
        assertEquals("考勤制度", first.getDocTitle());
    }

    @Test
    @DisplayName("retrieve - empty query returns empty")
    void testRetrieveEmpty() {
        assertTrue(ragService.retrieve("").isEmpty());
        assertTrue(ragService.retrieve(null).isEmpty());
    }

    @Test
    @DisplayName("retrieve - respects token budget")
    void testRetrieveTokenBudget() {
        // Ingest a large document
        StringBuilder sb = new StringBuilder("# 大文档\n");
        for (int i = 0; i < 50; i++) {
            sb.append("## 章节").append(i).append("\n");
            sb.append("这是第").append(i).append("章的详细内容，包含员工考勤管理的相关规定。\n\n");
        }
        ragService.ingestDocument("大文档", sb.toString(), "general");

        List<RetrievalResult> results = ragService.retrieve("员工考勤管理", 20);

        // Total tokens should not exceed budget
        int totalTokens = results.stream()
                .mapToInt(r -> r.getChunk().getTokenCount())
                .sum();
        assertTrue(totalTokens <= AgentConstants.RAG_TOKEN_BUDGET,
                "Total tokens " + totalTokens + " exceeds budget " + AgentConstants.RAG_TOKEN_BUDGET);
    }

    @Test
    @DisplayName("expireDocument - filters expired doc chunks from results")
    void testExpireDocument() {
        ragService.ingestDocument("旧制度", "# 旧制度\n员工年假10天。", "attendance");
        ragService.ingestDocument("新制度", "# 新制度\n员工年假15天。", "attendance");

        // Get the old doc ID and expire it
        var results = ragService.retrieve("旧制度年假", 10);
        assertFalse(results.isEmpty());

        // Expire the old document
        ragService.expireDocument(results.stream()
                .filter(r -> "旧制度".equals(r.getDocTitle()))
                .findFirst()
                .map(r -> r.getChunk().getDocId())
                .orElse(""));

        // After expiration, old doc chunks should be filtered
        var newResults = ragService.retrieve("年假", 10);
        assertTrue(newResults.stream().noneMatch(r -> "旧制度".equals(r.getDocTitle())));
    }

    @Test
    @DisplayName("retrieve - results are from DataSource.RAG")
    void testRetrieveDataSource() {
        ragService.ingestDocument("测试", "# 测试\n内容", "test");
        List<RetrievalResult> results = ragService.retrieve("测试内容");

        assertFalse(results.isEmpty());
        results.forEach(r -> assertEquals(com.luutry.agent.common.enums.DataSource.RAG, r.getDataSource()));
    }

    @Test
    @DisplayName("clearAll - removes all indexed data")
    void testClearAll() {
        ragService.ingestDocument("文档1", "# 文档1\n内容A", "test");
        ragService.ingestDocument("文档2", "# 文档2\n内容B", "test");

        assertTrue(ragService.getDocumentCount() > 0);
        ragService.clearAll();
        assertEquals(0, ragService.getDocumentCount());
        assertEquals(0, ragService.getIndexedChunkCount());
    }

    @Test
    @DisplayName("retrieve - no results for unmatched query")
    void testRetrieveNoMatch() {
        ragService.ingestDocument("考勤", "# 考勤\n员工需要按时打卡。", "attendance");
        List<RetrievalResult> results = ragService.retrieve("xyzabc完全不相关的查询");
        assertTrue(results.isEmpty());
    }
}
