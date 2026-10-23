package com.luutry.agent.memory.shortterm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis 的短期记忆存储，服务端唯一持有会话历史。
 * <p>
 * 用途：会话轮次与摘要跨进程、跨实例共享，进程重启不丢多轮上下文。
 * 处理问题：内存实现只能单实例、重启即失忆，无法支撑多副本部署与真实对话连续。
 * 处理方式：turns 用 List（顺序天然有序，LTRIM 控容量）、meta 用 Hash（归属与原子轮次号）、
 * summary 用 String；三把 key 同 TTL 滑动续期，失败按 R8 降级为空历史并计数。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agent.memory", name = "store", havingValue = "redis")
public class RedisShortTermStore implements ShortTermMemoryStore {

    static final String DEFAULT_KEY_PREFIX = "agent:sess:";

    private static final String SUFFIX_TURNS = ":turns";
    private static final String SUFFIX_META = ":meta";
    private static final String SUFFIX_SUMMARY = ":summary";

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_ACCESS = "lastAccess";
    private static final String FIELD_TURN_SEQ = "turnSeq";

    private static final Duration TTL = Duration.ofMillis(AgentConstants.DEFAULT_SESSION_TTL_MS);
    private static final long WARN_INTERVAL_MS = 60_000L;

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    /** 降级次数，供测试与健康检查观察；不记录 sessionId，避免标识泄露 */
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong lastWarnAt = new AtomicLong();

    public RedisShortTermStore(StringRedisTemplate redis) {
        this(redis, DEFAULT_KEY_PREFIX);
    }

    RedisShortTermStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
    }

    @Override
    public int addTurn(String sessionId, String userId, ConversationTurn turn) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(turn, "turn must not be null");

        String turnsKey = turnsKey(sessionId);
        String metaKey = metaKey(sessionId);
        try {
            // 轮次号必须由 Redis 原子分配，控制器侧 count+1 在并发/多实例下会重号
            long turnNumber = redis.opsForHash().increment(metaKey, FIELD_TURN_SEQ, 1);
            turn.setTurnNumber((int) turnNumber);

            redis.opsForList().rightPush(turnsKey, serialize(turn));
            redis.opsForList().trim(turnsKey, -AgentConstants.MAX_TURNS_PER_SESSION, -1);

            long now = System.currentTimeMillis();
            redis.opsForHash().put(metaKey, FIELD_USER_ID, userId);
            redis.opsForHash().putIfAbsent(metaKey, FIELD_CREATED_AT, String.valueOf(now));
            redis.opsForHash().put(metaKey, FIELD_LAST_ACCESS, String.valueOf(now));

            expire(turnsKey);
            expire(metaKey);
            expire(summaryKey(sessionId));
            return (int) turnNumber;
        } catch (Throwable t) {
            degrade("addTurn", t);
            return 0;
        }
    }

    @Override
    public List<ConversationTurn> getAllTurns(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return readRange(sessionId, 0, -1);
    }

    @Override
    public List<ConversationTurn> getRecentTurns(String sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return readRange(sessionId, -limit, -1);
    }

    @Override
    public int getTurnCount(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try {
            Long size = redis.opsForList().size(turnsKey(sessionId));
            return size == null ? 0 : (int) Math.min(size, Integer.MAX_VALUE);
        } catch (Throwable t) {
            degrade("getTurnCount", t);
            return 0;
        }
    }

    @Override
    public String getSummary(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try {
            String summary = redis.opsForValue().get(summaryKey(sessionId));
            return summary == null ? "" : summary;
        } catch (Throwable t) {
            degrade("getSummary", t);
            return "";
        }
    }

    @Override
    public void saveSummary(String sessionId, String summary) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (summary == null || summary.isBlank()) {
            return;
        }
        try {
            redis.opsForValue().set(summaryKey(sessionId), summary, TTL);
        } catch (Throwable t) {
            degrade("saveSummary", t);
        }
    }

    @Override
    public void clearSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try {
            redis.delete(List.of(turnsKey(sessionId), metaKey(sessionId), summaryKey(sessionId)));
        } catch (Throwable t) {
            degrade("clearSession", t);
        }
    }

    @Override
    public boolean sessionExists(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try {
            return Boolean.TRUE.equals(redis.hasKey(metaKey(sessionId)));
        } catch (Throwable t) {
            degrade("sessionExists", t);
            return false;
        }
    }

    @Override
    public Optional<String> getSessionUserId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try {
            Object userId = redis.opsForHash().get(metaKey(sessionId), FIELD_USER_ID);
            return userId == null ? Optional.empty() : Optional.of(userId.toString());
        } catch (Throwable t) {
            degrade("getSessionUserId", t);
            return Optional.empty();
        }
    }

    /**
     * 启动时探活：Redis 不可用不应阻断启动，但必须在日志里说清会话历史会降级。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            connection.ping();
            log.info("Redis 会话存储已连接：{}", keyPrefix);
        } catch (Throwable t) {
            log.warn("Redis 会话存储不可用，多轮历史将降级为空（检查 spring.data.redis 配置）: {}", t.toString());
        }
    }

    /**
     * 当前降级次数，便于测试断言与后续接入指标。
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    private List<ConversationTurn> readRange(String sessionId, long start, long end) {
        String turnsKey = turnsKey(sessionId);
        try {
            expire(turnsKey);
            expire(metaKey(sessionId));
            List<String> raw = redis.opsForList().range(turnsKey, start, end);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<ConversationTurn> turns = new ArrayList<>(raw.size());
            for (String json : raw) {
                ConversationTurn turn = deserialize(json);
                if (turn != null) {
                    turns.add(turn);
                }
            }
            return Collections.unmodifiableList(turns);
        } catch (Throwable t) {
            degrade("readRange", t);
            return Collections.emptyList();
        }
    }

    private void expire(String key) {
        redis.expire(key, TTL);
    }

    private String turnsKey(String sessionId) {
        return keyPrefix + sessionId + SUFFIX_TURNS;
    }

    private String metaKey(String sessionId) {
        return keyPrefix + sessionId + SUFFIX_META;
    }

    private String summaryKey(String sessionId) {
        return keyPrefix + sessionId + SUFFIX_SUMMARY;
    }

    private String serialize(ConversationTurn turn) {
        JSONObject json = new JSONObject();
        json.put("n", turn.getTurnNumber());
        json.put("u", turn.getUserMessage());
        json.put("a", turn.getAssistantMessage());
        json.put("ts", turn.getTimestamp() != null ? turn.getTimestamp().toEpochMilli() : System.currentTimeMillis());
        json.put("t", turn.getToolResults() == null ? List.of() : turn.getToolResults());
        return json.toJSONString();
    }

    private ConversationTurn deserialize(String raw) {
        try {
            JSONObject json = JSON.parseObject(raw);
            if (json == null) {
                return null;
            }
            List<String> toolResults = Collections.emptyList();
            JSONArray array = json.getJSONArray("t");
            if (array != null && !array.isEmpty()) {
                toolResults = array.toJavaList(String.class);
            }
            Long ts = json.getLong("ts");
            return ConversationTurn.builder()
                    .turnNumber(json.getIntValue("n"))
                    .userMessage(json.getString("u"))
                    .assistantMessage(json.getString("a"))
                    .toolResults(toolResults)
                    .timestamp(ts != null ? Instant.ofEpochMilli(ts) : Instant.now())
                    .build();
        } catch (Throwable t) {
            degrade("deserialize", t);
            return null;
        }
    }

    /**
     * 降级：读路径退化为空历史，写路径丢弃本次写入，绝不把异常抛给对话主链路（R8）。
     */
    private void degrade(String operation, Throwable t) {
        long count = failureCount.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last > WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("Redis 会话存储降级（累计 {} 次），operation={}: {}", count, operation, t.toString());
        }
    }
}
