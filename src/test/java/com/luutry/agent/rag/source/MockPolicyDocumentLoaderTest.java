package com.luutry.agent.rag.source;

import com.luutry.agent.rag.RagService;
import com.luutry.agent.rag.chunker.HierarchicalChunker;
import com.luutry.agent.rag.dedup.ChunkDeduplicator;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.parser.PlainTextParser;
import com.luutry.agent.rag.rerank.SimpleReranker;
import com.luutry.agent.rag.retriever.HybridRetriever;
import com.luutry.agent.rag.retriever.bm25.Bm25Retriever;
import com.luutry.agent.rag.retriever.dense.DenseRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockPolicyDocumentLoader.
 */
@DisplayName("MockPolicyDocumentLoader Tests")
class MockPolicyDocumentLoaderTest {

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
    @DisplayName("should ingest every policy document from classpath")
    void shouldSeedAllPolicies() {
        int seeded = new MockPolicyDocumentLoader(ragService).seed();

        assertEquals(6, seeded, "mock/policies.json 中的制度应全部入库");
        assertEquals(6, ragService.getDocumentCount());
        assertTrue(ragService.getIndexedChunkCount() > 0);
    }

    @Test
    @DisplayName("should retrieve annual leave policy after seeding")
    void shouldRetrievePolicyAfterSeeding() {
        new MockPolicyDocumentLoader(ragService).seed();

        List<RetrievalResult> results = ragService.retrieve("年假");

        assertFalse(results.isEmpty(), "制度库应能命中'年假'");
        assertTrue(results.stream().anyMatch(r -> "员工年假管理制度".equals(r.getDocTitle())));
        assertNotNull(results.get(0).getProvenance(), "检索结果必须带来源（R6）");
    }
}
