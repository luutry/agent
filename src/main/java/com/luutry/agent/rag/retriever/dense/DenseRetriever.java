package com.luutry.agent.rag.retriever.dense;

import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.rag.retriever.DocumentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 稠密向量检索器，使用内存关键词重叠相似度模拟。
 * <p>
 * 用途：模拟基于嵌入的语义检索，无需向量数据库。
 * 处理问题：生产环境使用LangChain4j Embedding + Milvus进行语义搜索；
 * 开发/测试期间，此Mock实现使用关键词重叠比率作为
 * 文本嵌入余弦相似度的代理。
 * 处理方式：计算查询与每个切片之间的字符n-gram重叠；
 * score = |交集| / |并集|（类似Jaccard相似度）。
 * </p>
 * <p>
 * TODO: 生产环境替换为MilvusDenseRetriever，使用LangChain4j EmbeddingModel + Milvus。
 * </p>
 */
@Slf4j
@Component
public class DenseRetriever implements DocumentRetriever {

    /**
     * 已索引的切片
     */
    private final List<TextChunk> chunks = Collections.synchronizedList(new ArrayList<>());

    @Override
    public List<TextChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> queryNgrams = getNgrams(query.toLowerCase());
        if (queryNgrams.isEmpty()) {
            return Collections.emptyList();
        }

        return chunks.parallelStream()
                .map(chunk -> {
                    Set<String> chunkNgrams = getNgrams(chunk.getContent().toLowerCase());
                    double similarity = computeOverlap(queryNgrams, chunkNgrams);
                    chunk.setScore(similarity);
                    return chunk;
                })
                .filter(c -> c.getScore() > 0)
                .sorted(Comparator.comparingDouble(TextChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void addChunk(TextChunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        chunks.add(chunk);
    }

    @Override
    public void addChunks(List<TextChunk> newChunks) {
        newChunks.forEach(this::addChunk);
    }

    @Override
    public int size() {
        return chunks.size();
    }

    @Override
    public void clear() {
        chunks.clear();
    }

    /**
     * 计算两个n-gram集合之间的重叠比率。
     */
    private double computeOverlap(Set<String> queryNgrams, Set<String> chunkNgrams) {
        if (queryNgrams.isEmpty() || chunkNgrams.isEmpty()) {
            return 0.0;
        }
        long intersection = queryNgrams.stream().filter(chunkNgrams::contains).count();
        long union = queryNgrams.size() + chunkNgrams.size() - intersection;
        return union > 0 ? (double) intersection / union : 0.0;
    }

    /**
     * 从文本生成字符trigram。
     */
    Set<String> getNgrams(String text) {
        if (text == null || text.length() < 3) {
            return text != null && text.length() >= 2 ? Set.of(text) : Collections.emptySet();
        }
        Set<String> ngrams = new HashSet<>();
        for (int i = 0; i <= text.length() - 3; i++) {
            ngrams.add(text.substring(i, i + 3));
        }
        return ngrams;
    }
}
