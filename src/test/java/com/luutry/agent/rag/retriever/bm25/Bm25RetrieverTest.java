package com.luutry.agent.rag.retriever.bm25;

import com.luutry.agent.rag.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bm25Retriever.
 * Covers: keyword matching, scoring, empty query, topK, Chinese text.
 */
@DisplayName("Bm25Retriever Tests")
class Bm25RetrieverTest {

    private Bm25Retriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new Bm25Retriever();
        // Index test chunks
        retriever.addChunk(TextChunk.builder()
                .id("c1").content("员工年假制度规定每年工作满一年可享受5天年假").docId("d1").build());
        retriever.addChunk(TextChunk.builder()
                .id("c2").content("员工试用期为三个月，试用期内享受正式员工80%薪资").docId("d1").build());
        retriever.addChunk(TextChunk.builder()
                .id("c3").content("绩效考核每季度进行一次，评分分为ABCD四个等级").docId("d2").build());
        retriever.addChunk(TextChunk.builder()
                .id("c4").content("加班需提前申请，加班工资按1.5倍计算").docId("d2").build());
    }

    @Test
    @DisplayName("retrieve - finds relevant chunks for Chinese query")
    void testRetrieveChinese() {
        List<TextChunk> results = retriever.retrieve("年假制度", 5);
        assertFalse(results.isEmpty());
        // First result should be about 年假
        assertTrue(results.get(0).getContent().contains("年假"));
    }

    @Test
    @DisplayName("retrieve - empty query returns empty")
    void testRetrieveEmpty() {
        assertTrue(retriever.retrieve("", 5).isEmpty());
        assertTrue(retriever.retrieve(null, 5).isEmpty());
        assertTrue(retriever.retrieve("  ", 5).isEmpty());
    }

    @Test
    @DisplayName("retrieve - no match returns empty")
    void testRetrieveNoMatch() {
        List<TextChunk> results = retriever.retrieve("xyzabc不存在的关键词", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("retrieve - respects topK limit")
    void testRetrieveTopK() {
        List<TextChunk> results = retriever.retrieve("员工", 2);
        assertTrue(results.size() <= 2);
    }

    @Test
    @DisplayName("retrieve - results are sorted by score descending")
    void testRetrieveSorted() {
        List<TextChunk> results = retriever.retrieve("员工试用期薪资", 5);
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore());
        }
    }

    @Test
    @DisplayName("size - returns correct count")
    void testSize() {
        assertEquals(4, retriever.size());
    }

    @Test
    @DisplayName("clear - removes all indexed chunks")
    void testClear() {
        retriever.clear();
        assertEquals(0, retriever.size());
        assertTrue(retriever.retrieve("年假", 5).isEmpty());
    }

    @Test
    @DisplayName("tokenize - handles Chinese bigrams and English words")
    void testTokenize() {
        var tokens = retriever.tokenize("员工考勤管理");
        assertTrue(tokens.contains("员工"));
        assertTrue(tokens.contains("考勤"));
        assertTrue(tokens.contains("管理"));
    }
}
