package com.luutry.agent.rag;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.observability.trace.AgentTrace;
import com.luutry.agent.rag.chunker.HierarchicalChunker;
import com.luutry.agent.rag.dedup.ChunkDeduplicator;
import com.luutry.agent.rag.model.RagDocument;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.rag.parser.DocumentParser;
import com.luutry.agent.rag.rerank.ChunkReranker;
import com.luutry.agent.rag.retriever.HybridRetriever;
import com.luutry.agent.rag.source.ProvenanceTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一RAG服务入口，编排完整的检索管道。
 * <p>
 * 用途：文档摄入和查询检索的统一API，协调所有RAG子模块
 * （解析、切片、索引、检索、重排、去重、溯源）。
 * 处理问题：RAG管道步骤繁多，此服务封装完整流程，
 * 调用方无需了解内部编排细节。
 * 管道：
 * 1. 摄入：解析文档 → 切片 → 索引到检索器 → 注册溯源
 * 2. 检索：混合搜索 → 重排 → 去重 → Token预算裁剪 → 溯源充实
 * </p>
 */
@Slf4j
@Service
public class RagService {

    private final DocumentParser parser;
    private final HierarchicalChunker chunker;
    private final HybridRetriever hybridRetriever;
    private final ChunkReranker reranker;
    private final ChunkDeduplicator deduplicator;
    private final ProvenanceTracker provenanceTracker;

    public RagService(DocumentParser parser,
                      HierarchicalChunker chunker,
                      HybridRetriever hybridRetriever,
                      ChunkReranker reranker,
                      ChunkDeduplicator deduplicator,
                      ProvenanceTracker provenanceTracker) {
        this.parser = parser;
        this.chunker = chunker;
        this.hybridRetriever = hybridRetriever;
        this.reranker = reranker;
        this.deduplicator = deduplicator;
        this.provenanceTracker = provenanceTracker;
    }

    /**
     * 将文档摄入RAG知识库。
     * 执行解析、切片、索引和溯源注册。
     *
     * @param title    文档标题
     * @param content  原始文本内容
     * @param category 文档类别
     * @return 解析并索引后的RagDocument
     */
    @AgentTrace(stage = "rag_ingest")
    public RagDocument ingestDocument(String title, String content, String category) {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");

        // 步骤1：解析
        RagDocument doc = parser.parse(title, content, category);

        // 步骤2：切片
        List<TextChunk> chunks = chunker.chunk(doc);
        chunks.forEach(c -> c.setDocId(doc.getId()));

        // 步骤3：索引
        hybridRetriever.addChunks(chunks);

        // 步骤4：注册溯源
        provenanceTracker.registerDocument(doc);

        log.info("Ingested document '{}': {} chunks indexed", title, chunks.size());
        return doc;
    }

    /**
     * 摄入已构建的RagDocument（已解析）。
     *
     * @param document 待摄入的文档
     * @return 创建的切片数
     */
    @AgentTrace(stage = "rag_ingest")
    public int ingestDocument(RagDocument document) {
        Objects.requireNonNull(document, "document must not be null");

        List<TextChunk> chunks = chunker.chunk(document);
        chunks.forEach(c -> c.setDocId(document.getId()));
        hybridRetriever.addChunks(chunks);
        provenanceTracker.registerDocument(document);

        log.info("Ingested document '{}': {} chunks indexed", document.getTitle(), chunks.size());
        return chunks.size();
    }

    /**
     * 通过完整RAG管道检索与查询相关的切片。
     * 管道：混合搜索 → 过滤过期 → 重排 → 去重 → Token预算 → 溯源
     *
     * @param query 搜索查询
     * @return 带溯源信息的检索结果列表，在Token预算范围内
     */
    @AgentTrace(stage = "rag_retrieve")
    public List<RetrievalResult> retrieve(String query) {
        return retrieve(query, AgentConstants.RAG_DEFAULT_TOP_K);
    }

    /**
     * 使用自定义topK检索相关切片。
     *
     * @param query 搜索查询
     * @param topK  去重/预算裁剪前的最大结果数
     * @return 带溯源信息的检索结果列表，在Token预算范围内
     */
    @AgentTrace(stage = "rag_retrieve")
    public List<RetrievalResult> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 步骤1：混合检索
        List<TextChunk> chunks = hybridRetriever.retrieve(query, topK);
        if (chunks.isEmpty()) {
            log.debug("No chunks retrieved for query: '{}'", query);
            return Collections.emptyList();
        }

        // 步骤2：封装为RetrievalResult
        List<RetrievalResult> results = chunks.stream()
                .map(chunk -> RetrievalResult.builder()
                        .chunk(chunk)
                        .score(chunk.getScore())
                        .dataSource(DataSource.RAG)
                        .build())
                .collect(Collectors.toList());

        // 步骤3：充实溯源信息
        results.forEach(provenanceTracker::enrichProvenance);

        // 步骤4：重排
        results = reranker.rerank(query, results);

        // 步骤5：去重
        results = deduplicator.deduplicate(results);

        // 步骤6：Token预算裁剪
        results = trimToTokenBudget(results);

        log.info("Retrieved {} results for query '{}' (after dedup & budget trim)", results.size(), query);
        return results;
    }

    /**
     * 将文档标记为过期（其切片将在检索时被过滤）。
     *
     * @param docId 待过期的文档ID
     */
    public void expireDocument(String docId) {
        hybridRetriever.markExpired(docId);
        log.debug("Marked document {} as expired", docId);
    }

    /**
     * 获取已索引切片的总数。
     *
     * @return 切片数量
     */
    public int getIndexedChunkCount() {
        return hybridRetriever.size();
    }

    /**
     * 获取已注册文档的总数。
     *
     * @return 文档数量
     */
    public int getDocumentCount() {
        return provenanceTracker.getDocumentCount();
    }

    /**
     * 清除所有索引数据。
     */
    public void clearAll() {
        hybridRetriever.clear();
        provenanceTracker.clear();
        log.info("Cleared all RAG indexed data");
    }

    /**
     * 裁剪结果以适应Token预算。
     * 累计Token数，超出预算时停止。
     */
    private List<RetrievalResult> trimToTokenBudget(List<RetrievalResult> results) {
        int budget = AgentConstants.RAG_TOKEN_BUDGET;
        java.util.concurrent.atomic.AtomicInteger accumulated = new java.util.concurrent.atomic.AtomicInteger(0);

        return results.stream()
                .filter(r -> {
                    int chunkTokens = r.getChunk().getTokenCount();
                    if (accumulated.get() + chunkTokens > budget) {
                        return false;
                    }
                    accumulated.addAndGet(chunkTokens);
                    return true;
                })
                .collect(Collectors.toList());
    }
}
