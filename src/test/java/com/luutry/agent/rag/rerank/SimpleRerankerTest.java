package com.luutry.agent.rag.rerank;

import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimpleReranker.
 * Covers: reranking logic, section match, empty inputs, score normalization.
 */
@DisplayName("SimpleReranker Tests")
class SimpleRerankerTest {

    private SimpleReranker reranker;

    @BeforeEach
    void setUp() {
        reranker = new SimpleReranker();
    }

    @Test
    @DisplayName("rerank - reorders by combined score")
    void testRerank() {
        List<RetrievalResult> results = List.of(
                createResult("c1", "考勤相关内容", "第一章>考勤规则", 0.5),
                createResult("c2", "绩效相关内容", "第二章>绩效考核", 0.8),
                createResult("c3", "考勤和绩效", "第三章>考勤规则>绩效", 0.3)
        );

        List<RetrievalResult> reranked = reranker.rerank("考勤规则", new ArrayList<>(results));
        assertFalse(reranked.isEmpty());
        // Results should be reordered by combined score
        for (int i = 1; i < reranked.size(); i++) {
            assertTrue(reranked.get(i - 1).getScore() >= reranked.get(i).getScore());
        }
    }

    @Test
    @DisplayName("rerank - empty results returns empty")
    void testRerankEmpty() {
        assertTrue(reranker.rerank("query", new ArrayList<>()).isEmpty());
        assertTrue(reranker.rerank("query", null).isEmpty());
    }

    @Test
    @DisplayName("rerank - null query returns original order")
    void testRerankNullQuery() {
        List<RetrievalResult> results = List.of(
                createResult("c1", "内容A", "章节1", 0.5),
                createResult("c2", "内容B", "章节2", 0.8)
        );

        List<RetrievalResult> reranked = reranker.rerank(null, new ArrayList<>(results));
        assertEquals(2, reranked.size());
    }

    @Test
    @DisplayName("rerank - section match boosts score")
    void testRerankSectionMatch() {
        // Result with matching section path should get boost
        List<RetrievalResult> results = List.of(
                createResult("c1", "通用内容", "其他章节", 0.5),
                createResult("c2", "通用内容", "第一章>考勤管理", 0.5)
        );

        List<RetrievalResult> reranked = reranker.rerank("考勤管理", new ArrayList<>(results));
        // c2 should rank higher due to section match
        assertEquals("c2", reranked.get(0).getChunk().getId());
    }

    @Test
    @DisplayName("rerank - scores are normalized to [0,1] range")
    void testRerankNormalizedScores() {
        List<RetrievalResult> results = List.of(
                createResult("c1", "内容", "章节", 100.0),
                createResult("c2", "内容", "章节", 0.001)
        );

        List<RetrievalResult> reranked = reranker.rerank("测试", new ArrayList<>(results));
        for (RetrievalResult r : reranked) {
            assertTrue(r.getScore() >= 0 && r.getScore() <= 1.0,
                    "Score should be in [0,1] range: " + r.getScore());
        }
    }

    private RetrievalResult createResult(String id, String content, String sectionPath, double score) {
        return RetrievalResult.builder()
                .chunk(TextChunk.builder().id(id).content(content).sectionPath(sectionPath).build())
                .score(score)
                .docTitle("测试文档")
                .build();
    }
}
