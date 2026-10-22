package com.luutry.agent.memory.longterm;

import com.luutry.agent.memory.model.MemoryEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 长期记忆存储的内存实现，用于开发和测试环境。
 * <p>
 * 用途：提供无需向量数据库的长期记忆存储，模拟语义搜索。
 * 处理问题：开发/测试环境中Milvus可能不可用，此实现使用关键词重叠评分
 * 来模拟基于嵌入的语义检索。
 * 处理方式：使用ConcurrentHashMap存储条目；语义搜索采用查询与记忆内容之间
 * 的简单字符级重叠比率作为相关性代理。
 * </p>
 * <p>
 * TODO: 生产环境替换为MilvusLongTermStore，使用LangChain4j Embedding + Milvus。
 * </p>
 */
@Slf4j
@Component
public class InMemoryLongTermStore implements LongTermMemoryStore {

    /**
     * 记忆存储：memoryId -> MemoryEntry
     */
    private final ConcurrentHashMap<String, MemoryEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(MemoryEntry entry) {
        Objects.requireNonNull(entry, "MemoryEntry must not be null");
        Objects.requireNonNull(entry.getId(), "MemoryEntry.id must not be null");
        Objects.requireNonNull(entry.getUserId(), "MemoryEntry.userId must not be null");

        store.put(entry.getId(), entry);
        log.debug("Saved long-term memory: id={}, userId={}, tag={}", entry.getId(), entry.getUserId(), entry.getTag());
    }

    @Override
    public MemoryEntry getById(String memoryId) {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        MemoryEntry entry = store.get(memoryId);
        if (entry != null) {
            entry.recordAccess();
        }
        return entry;
    }

    @Override
    public List<MemoryEntry> getByUserId(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return store.values().stream()
                .filter(e -> userId.equals(e.getUserId()))
                .filter(e -> !e.isExpired())
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryEntry> getByTag(String userId, String tag) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tag, "tag must not be null");
        return store.values().stream()
                .filter(e -> userId.equals(e.getUserId()))
                .filter(e -> tag.equals(e.getTag()))
                .filter(e -> !e.isExpired())
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryEntry> search(String userId, String query, int topK) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(query, "query must not be null");

        if (query.isBlank() || topK <= 0) {
            return Collections.emptyList();
        }

        // 使用关键词重叠评分模拟语义搜索
        Set<String> queryTokens = tokenize(query.toLowerCase());

        return store.values().stream()
                .filter(e -> userId.equals(e.getUserId()))
                .filter(e -> !e.isExpired())
                .map(e -> new ScoredEntry(e, computeRelevanceScore(e, query, queryTokens)))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingDouble((ScoredEntry s) -> s.score).reversed())
                .limit(topK)
                .peek(s -> s.entry.recordAccess())
                .map(s -> s.entry)
                .collect(Collectors.toList());
    }

    @Override
    public boolean delete(String memoryId) {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        MemoryEntry removed = store.remove(memoryId);
        if (removed != null) {
            log.debug("Deleted long-term memory: id={}", memoryId);
            return true;
        }
        return false;
    }

    @Override
    public int deleteByUserId(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<String> toRemove = store.values().stream()
                .filter(e -> userId.equals(e.getUserId()))
                .map(MemoryEntry::getId)
                .collect(Collectors.toList());

        toRemove.forEach(store::remove);
        log.debug("Deleted {} memories for userId={}", toRemove.size(), userId);
        return toRemove.size();
    }

    @Override
    public int removeExpired() {
        List<String> expired = store.values().stream()
                .filter(MemoryEntry::isExpired)
                .map(MemoryEntry::getId)
                .collect(Collectors.toList());

        expired.forEach(store::remove);
        if (!expired.isEmpty()) {
            log.info("Removed {} expired long-term memories", expired.size());
        }
        return expired.size();
    }

    @Override
    public int forgetByDecayScore(double threshold) {
        List<String> toForget = store.values().stream()
                .filter(e -> e.getDecayScore() < threshold)
                .map(MemoryEntry::getId)
                .collect(Collectors.toList());

        toForget.forEach(store::remove);
        if (!toForget.isEmpty()) {
            log.info("Forgot {} memories with decay score < {}", toForget.size(), threshold);
        }
        return toForget.size();
    }

    @Override
    public int count(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return (int) store.values().stream()
                .filter(e -> userId.equals(e.getUserId()))
                .filter(e -> !e.isExpired())
                .count();
    }

    /**
     * 计算记忆条目与查询之间的相关性评分。
     * 采用子串包含和词元重叠的组合策略。
     */
    private double computeRelevanceScore(MemoryEntry entry, String query, Set<String> queryTokens) {
        String content = entry.getContent().toLowerCase();
        String tag = entry.getTag() != null ? entry.getTag().toLowerCase() : "";

        double score = 0.0;

        // 完整查询子串匹配（最高权重）
        if (content.contains(query.toLowerCase())) {
            score += 3.0;
        }

        // 词元重叠
        Set<String> contentTokens = tokenize(content);
        long overlap = queryTokens.stream().filter(contentTokens::contains).count();
        score += (double) overlap / Math.max(queryTokens.size(), 1) * 2.0;

        // 标签匹配加分
        if (!tag.isEmpty() && queryTokens.stream().anyMatch(t -> tag.contains(t))) {
            score += 1.0;
        }

        // 衰减分数作为乘数（访问越多的记忆排名越高）
        score *= entry.getDecayScore();

        return score;
    }

    /**
     * 简单的分词器，用于关键词匹配。
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        // 按空白和常见标点分词，过滤短词元
        return Arrays.stream(text.split("[\\s,;.!?()\\[\\]{}]+"))
                .filter(s -> s.length() >= 2)
                .collect(Collectors.toSet());
    }

    /**
     * 内部评分条目，用于搜索结果排序。
     */
    private record ScoredEntry(MemoryEntry entry, double score) {
    }
}
