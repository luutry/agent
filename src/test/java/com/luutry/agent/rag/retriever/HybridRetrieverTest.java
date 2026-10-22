package com.luutry.agent.rag.retriever;

import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.rag.retriever.bm25.Bm25Retriever;
import com.luutry.agent.rag.retriever.dense.DenseRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HybridRetriever.
 * Covers: RRF fusion, expired doc filtering, combined retrieval.
 */
@DisplayName("HybridRetriever Tests")
class HybridRetrieverTest {

    private HybridRetriever hybrid;
    private Bm25Retriever bm25;
    private DenseRetriever dense;

    @BeforeEach
    void setUp() {
        bm25 = new Bm25Retriever();
        dense = new DenseRetriever();
        hybrid = new HybridRetriever(bm25, dense);

        // Index test chunks
        hybrid.addChunk(TextChunk.builder()
                .id("c1").docId("d1").content("员工年假制度规定每年5天带薪年假").build());
        hybrid.addChunk(TextChunk.builder()
                .id("c2").docId("d1").content("员工试用期三个月，薪资80%").build());
        hybrid.addChunk(TextChunk.builder()
                .id("c3").docId("d2").content("绩效考核每季度一次，ABCD四等级").build());
        hybrid.addChunk(TextChunk.builder()
                .id("c4").docId("d3").content("加班工资按1.5倍计算，需提前申请").build());
    }

    @Test
    @DisplayName("retrieve - combines BM25 and Dense results")
    void testHybridRetrieve() {
        List<TextChunk> results = hybrid.retrieve("年假", 5);
        assertFalse(results.isEmpty());
        // Results should have RRF scores
        assertTrue(results.get(0).getScore() > 0);
    }

    @Test
    @DisplayName("retrieve - empty query returns empty")
    void testEmptyQuery() {
        assertTrue(hybrid.retrieve("", 5).isEmpty());
        assertTrue(hybrid.retrieve(null, 5).isEmpty());
    }

    @Test
    @DisplayName("retrieve - filters expired documents")
    void testExpiredFiltering() {
        // Mark d1 as expired
        hybrid.markExpired("d1");

        List<TextChunk> results = hybrid.retrieve("员工年假试用期", 10);
        // Should not contain chunks from d1
        assertTrue(results.stream().noneMatch(c -> "d1".equals(c.getDocId())));
    }

    @Test
    @DisplayName("retrieve - results sorted by RRF score")
    void testSortedByScore() {
        List<TextChunk> results = hybrid.retrieve("绩效考核", 5);
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore());
        }
    }

    @Test
    @DisplayName("size - returns correct count")
    void testSize() {
        assertEquals(4, hybrid.size());
    }

    @Test
    @DisplayName("clear - removes all data")
    void testClear() {
        hybrid.clear();
        assertEquals(0, hybrid.size());
        assertTrue(hybrid.retrieve("年假", 5).isEmpty());
    }
}
