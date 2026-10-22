package com.luutry.agent.rag.retriever.bm25;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.rag.retriever.DocumentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于BM25算法的文本检索实现，使用内存倒排索引。
 * <p>
 * 用途：实现BM25算法进行基于关键词的文本检索。
 * 处理问题：HR制度查询需要精确关键词匹配，特定术语（如 "年假"、"试用期"、"加班"）
 * 必须在切片中被找到。
 * 处理方式：构建倒排索引（词项 -> 文档频率）；使用BM25公式评分：
 * score = IDF(qi) * (TF * (k1+1)) / (TF + k1 * (1 - b + b * dl/avgdl))
 * 使用字符bigram分词以支持中文文本。
 * </p>
 */
@Slf4j
@Component
public class Bm25Retriever implements DocumentRetriever {

    /**
     * BM25词频饱和参数
     */
    private static final double K1 = AgentConstants.RAG_BM25_K1;

    /**
     * BM25文档长度归一化参数
     */
    private static final double B = AgentConstants.RAG_BM25_B;

    /**
     * 已索引的切片
     */
    private final List<TextChunk> chunks = Collections.synchronizedList(new ArrayList<>());

    /**
     * 倒排索引：词项 -> [(切片索引, 词频)]
     */
    private final ConcurrentHashMap<String, List<int[]>> invertedIndex = new ConcurrentHashMap<>();

    /**
     * 文档长度（每个切片的词元数）
     */
    private final List<Integer> docLengths = Collections.synchronizedList(new ArrayList<>());

    /**
     * 平均文档长度
     */
    private double avgDocLength = 0.0;

    @Override
    public List<TextChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        int n = chunks.size();
        Map<Integer, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            List<int[]> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            // IDF = log((N - df + 0.5) / (df + 0.5) + 1)
            int df = postings.size();
            double idf = Math.log((n - df + 0.5) / (df + 0.5) + 1.0);

            for (int[] posting : postings) {
                int chunkIdx = posting[0];
                int tf = posting[1];
                int dl = docLengths.get(chunkIdx);

                // BM25评分组件
                double numerator = tf * (K1 + 1);
                double denominator = tf + K1 * (1 - B + B * dl / avgDocLength);
                double score = idf * numerator / denominator;

                scores.merge(chunkIdx, score, Double::sum);
            }
        }

        // 按分数降序排列，返回topK
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    TextChunk chunk = chunks.get(e.getKey());
                    chunk.setScore(e.getValue());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void addChunk(TextChunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");

        int idx = chunks.size();
        chunks.add(chunk);

        Set<String> terms = tokenize(chunk.getContent());
        docLengths.add(terms.size());

        // 更新倒排索引
        Map<String, Integer> termFreqs = new HashMap<>();
        for (String term : terms) {
            termFreqs.merge(term, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : termFreqs.entrySet()) {
            invertedIndex.computeIfAbsent(entry.getKey(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new int[]{idx, entry.getValue()});
        }

        // 重新计算平均文档长度
        avgDocLength = docLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
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
        invertedIndex.clear();
        docLengths.clear();
        avgDocLength = 0.0;
    }

    /**
     * 使用字符bigram（中文）+ 空白分词（英文）将文本分词为词项。
     */
    Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> terms = new HashSet<>();
        String lower = text.toLowerCase();

        // 提取中文汉字bigram
        for (int i = 0; i < lower.length() - 1; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                terms.add(String.valueOf(c1) + c2);
            }
        }

        // 提取单个中文字符（用于短查询）
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isChinese(c)) {
                terms.add(String.valueOf(c));
            }
        }

        // 提取英文单词（按空白/标点分词）
        String[] words = lower.split("[\\s,;.!?()\\[\\]{}，。；！？（）【】]+");
        for (String word : words) {
            if (word.length() >= 2 && !word.chars().allMatch(c -> isChinese((char) c))) {
                terms.add(word);
            }
        }

        return terms;
    }

    /**
     * 检查字符是否为中文字符。
     */
    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }
}
