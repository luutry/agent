package com.luutry.agent.rag.dedup;

import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChunkDeduplicator.
 * Covers: duplicate removal, similarity threshold, score preservation.
 */
@DisplayName("ChunkDeduplicator Tests")
class ChunkDeduplicatorTest {

    private ChunkDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new ChunkDeduplicator();
    }

    @Test
    @DisplayName("deduplicate - removes near-identical chunks")
    void testDeduplicateNearIdentical() {
        List<RetrievalResult> results = List.of(
                createResult("c1", "员工年假制度规定每年工作满一年可享受5天带薪年假", 0.9),
                createResult("c2", "员工年假制度规定每年工作满一年可享受5天带薪年假", 0.7)
        );

        List<RetrievalResult> deduped = deduplicator.deduplicate(new ArrayList<>(results));
        assertEquals(1, deduped.size());
        // Should keep the higher-scored one
        assertEquals("c1", deduped.get(0).getChunk().getId());
    }

    @Test
    @DisplayName("deduplicate - keeps distinct chunks")
    void testDeduplicateDistinct() {
        List<RetrievalResult> results = List.of(
                createResult("c1", "员工年假制度规定每年5天年假", 0.9),
                createResult("c2", "绩效考核每季度进行一次评分", 0.8),
                createResult("c3", "加班工资按1.5倍计算需提前申请", 0.7)
        );

        List<RetrievalResult> deduped = deduplicator.deduplicate(new ArrayList<>(results));
        assertEquals(3, deduped.size());
    }

    @Test
    @DisplayName("deduplicate - null/empty input returns empty")
    void testDeduplicateEmpty() {
        assertTrue(deduplicator.deduplicate(null).isEmpty());
        assertTrue(deduplicator.deduplicate(new ArrayList<>()).isEmpty());
    }

    @Test
    @DisplayName("deduplicate - single element returns same")
    void testDeduplicateSingle() {
        List<RetrievalResult> results = List.of(createResult("c1", "内容", 0.9));
        List<RetrievalResult> deduped = deduplicator.deduplicate(new ArrayList<>(results));
        assertEquals(1, deduped.size());
    }

    @Test
    @DisplayName("jaccardSimilarity - identical sets return 1.0")
    void testJaccardIdentical() {
        var set = deduplicator.getCharNgrams("测试内容", 3);
        assertEquals(1.0, deduplicator.jaccardSimilarity(set, set));
    }

    @Test
    @DisplayName("jaccardSimilarity - disjoint sets return 0.0")
    void testJaccardDisjoint() {
        var setA = deduplicator.getCharNgrams("abc", 3);
        var setB = deduplicator.getCharNgrams("xyz", 3);
        assertEquals(0.0, deduplicator.jaccardSimilarity(setA, setB));
    }

    private RetrievalResult createResult(String id, String content, double score) {
        return RetrievalResult.builder()
                .chunk(TextChunk.builder().id(id).content(content).build())
                .score(score)
                .build();
    }
}
