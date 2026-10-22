package com.luutry.agent.memory.shortterm;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 短期记忆存储的内存实现，用于开发和测试环境。
 * <p>
 * 用途：提供无需Redis的短期记忆存储，模拟TTL过期行为。
 * 处理问题：开发/测试环境中Redis可能不可用，此实现使用ConcurrentHashMap +
 * ScheduledExecutor模拟Redis会话TTL行为。
 * 处理方式：每个会话是一个ConversationTurn列表；后台调度器定期根据
 * 最后访问时间 + TTL清理过期会话。
 * </p>
 * <p>
 * TODO: 生产部署时替换为RedisShortTermStore，使用RedisTemplate实现。
 * </p>
 */
@Slf4j
@Component
public class InMemoryShortTermStore implements ShortTermMemoryStore {

    /**
     * 会话数据：sessionId -> 对话轮次列表
     */
    private final ConcurrentHashMap<String, List<ConversationTurn>> sessions = new ConcurrentHashMap<>();

    /**
     * 会话元数据：sessionId -> (userId, 最后访问时间)
     */
    private final ConcurrentHashMap<String, SessionMeta> sessionMeta = new ConcurrentHashMap<>();

    /**
     * TTL过期后台调度器
     */
    private final ScheduledExecutorService ttlScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "short-term-memory-ttl");
        t.setDaemon(true);
        return t;
    });

    public InMemoryShortTermStore() {
        // 每60秒执行一次TTL清理
        ttlScheduler.scheduleAtFixedRate(this::evictExpiredSessions, 60, 60, TimeUnit.SECONDS);
        log.info("InMemoryShortTermStore initialized, TTL={}ms", AgentConstants.DEFAULT_SESSION_TTL_MS);
    }

    @Override
    public void addTurn(String sessionId, String userId, ConversationTurn turn) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(turn, "turn must not be null");

        sessions.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        sessions.get(sessionId).add(turn);

        sessionMeta.put(sessionId, new SessionMeta(userId, Instant.now()));
        log.debug("Added turn #{} to session {}, total turns: {}",
                turn.getTurnNumber(), sessionId, sessions.get(sessionId).size());
    }

    @Override
    public List<ConversationTurn> getAllTurns(String sessionId) {
        List<ConversationTurn> turns = sessions.get(sessionId);
        if (turns == null) {
            return Collections.emptyList();
        }
        sessionMeta.computeIfPresent(sessionId, (k, meta) -> meta.touch());
        return Collections.unmodifiableList(new ArrayList<>(turns));
    }

    @Override
    public List<ConversationTurn> getRecentTurns(String sessionId, int limit) {
        List<ConversationTurn> allTurns = getAllTurns(sessionId);
        if (allTurns.isEmpty()) {
            return Collections.emptyList();
        }
        int fromIndex = Math.max(0, allTurns.size() - limit);
        return allTurns.subList(fromIndex, allTurns.size());
    }

    @Override
    public int getTurnCount(String sessionId) {
        List<ConversationTurn> turns = sessions.get(sessionId);
        return turns != null ? turns.size() : 0;
    }

    @Override
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        sessionMeta.remove(sessionId);
        log.debug("Cleared session {}", sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId) && !isSessionExpired(sessionId);
    }

    @Override
    public Optional<String> getSessionUserId(String sessionId) {
        SessionMeta meta = sessionMeta.get(sessionId);
        if (meta == null || isSessionExpired(sessionId)) {
            return Optional.empty();
        }
        return Optional.of(meta.userId);
    }

    /**
     * 清理超过TTL的过期会话。
     */
    private void evictExpiredSessions() {
        List<String> expired = sessionMeta.entrySet().stream()
                .filter(e -> isExpired(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String sessionId : expired) {
            clearSession(sessionId);
            log.info("Evicted expired session {}", sessionId);
        }

        if (!expired.isEmpty()) {
            log.debug("TTL eviction completed, removed {} sessions, remaining {}", expired.size(), sessions.size());
        }
    }

    /**
     * 基于TTL检查会话是否已过期。
     */
    private boolean isSessionExpired(String sessionId) {
        SessionMeta meta = sessionMeta.get(sessionId);
        return meta != null && isExpired(meta);
    }

    /**
     * 检查会话元数据是否已超过TTL。
     */
    private boolean isExpired(SessionMeta meta) {
        long elapsed = Instant.now().toEpochMilli() - meta.lastAccessTime.toEpochMilli();
        return elapsed > AgentConstants.DEFAULT_SESSION_TTL_MS;
    }

    /**
     * 内部会话元数据持有者。
     */
    private record SessionMeta(String userId, Instant lastAccessTime) {
        SessionMeta touch() {
            return new SessionMeta(userId, Instant.now());
        }
    }
}
