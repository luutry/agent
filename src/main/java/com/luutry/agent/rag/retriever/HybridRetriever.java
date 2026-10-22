package com.luutry.agent.rag.retriever;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.rag.retriever.bm25.Bm25Retriever;
import com.luutry.agent.rag.retriever.dense.DenseRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器，使用倒数排名融合（RRF）组合BM25和稠密向量检索。
 * <p>
 * 用途：合并关键词精确匹配（BM25）和语义匹配（稠密向量）的检索结果，
 * 提升整体召回率和精确率。
 * 处理问题：BM25擅长精确关键词匹配但会遗漏同义词；
 * 稠密向量检索能捕获语义相似性但可能遗漏特定术语。
 * 混合融合结合两者优势。
 * 处理方式：RRF公式：score(d) = Σ 1/(k + rank_bm25(d)) + Σ 1/(k + rank_dense(d))
 * 其中k=60（标准RRF常数）。同时过滤已过期文档的切片。
 * </p>
 */
@Slf4j
@Component
public class HybridRetriever implements DocumentRetriever {

    private final Bm25Retriever bm25Retriever;
    private final DenseRetriever denseRetriever;

    /**
     * 已过期文档ID集合，检索时过滤
     */
    private final Set<String> expiredDocIds = Collections.synchronizedSet(new HashSet<>());

    public HybridRetriever(Bm25Retriever bm25Retriever, DenseRetriever denseRetriever) {
        this.bm25Retriever = bm25Retriever;
        this.denseRetriever = denseRetriever;
    }

    @Override
    public List<TextChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 从两个检索器检索，扩展topK用于融合
        int expandedK = topK * 2;
        List<TextChunk> bm25Results = bm25Retriever.retrieve(query, expandedK);
        List<TextChunk> denseResults = denseRetriever.retrieve(query, expandedK);

        // 应用RRF融合
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, TextChunk> chunkMap = new HashMap<>();
        double k = AgentConstants.RAG_RRF_K;

        // BM25排名
        for (int rank = 0; rank < bm25Results.size(); rank++) {
            TextChunk chunk = bm25Results.get(rank);
            if (isExpired(chunk)) continue;
            String chunkId = chunk.getId();
            rrfScores.merge(chunkId, 1.0 / (k + rank + 1), Double::sum);
            chunkMap.putIfAbsent(chunkId, chunk);
        }

        // 稠密向量排名
        for (int rank = 0; rank < denseResults.size(); rank++) {
            TextChunk chunk = denseResults.get(rank);
            if (isExpired(chunk)) continue;
            String chunkId = chunk.getId();
            rrfScores.merge(chunkId, 1.0 / (k + rank + 1), Double::sum);
            chunkMap.putIfAbsent(chunkId, chunk);
        }

        // 按RRF分数降序排列
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    TextChunk chunk = chunkMap.get(e.getKey());
                    chunk.setScore(e.getValue());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void addChunk(TextChunk chunk) {
        bm25Retriever.addChunk(chunk);
        denseRetriever.addChunk(chunk);
    }

    @Override
    public void addChunks(List<TextChunk> chunks) {
        bm25Retriever.addChunks(chunks);
        denseRetriever.addChunks(chunks);
    }

    @Override
    public int size() {
        return bm25Retriever.size();
    }

    @Override
    public void clear() {
        bm25Retriever.clear();
        denseRetriever.clear();
        expiredDocIds.clear();
    }

    /**
     * 将文档ID标记为过期（检索时会被过滤）。
     *
     * @param docId 过期文档ID
     */
    public void markExpired(String docId) {
        expiredDocIds.add(docId);
    }

    /**
     * 批量标记文档ID为过期。
     *
     * @param docIds 过期文档ID集合
     */
    public void markExpired(Collection<String> docIds) {
        expiredDocIds.addAll(docIds);
    }

    /**
     * 检查切片是否属于已过期文档。
     */
    private boolean isExpired(TextChunk chunk) {
        return chunk.getDocId() != null && expiredDocIds.contains(chunk.getDocId());
    }
}
