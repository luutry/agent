package com.luutry.agent.memory.shortterm;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis 会话存储集成测试。
 * <p>
 * 依赖本机 Redis（127.0.0.1:6379）；不可用时整类跳过，保证无 Redis 的 CI 仍然全绿。
 * 每个用例使用独立 DB 15 + 随机 key 前缀，结束后按前缀清理，不污染真实数据。
 * </p>
 */
@DisplayName("RedisShortTermStore Integration Tests")
class RedisShortTermStoreTest {

    private static final int TEST_DB = 15;
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private String prefix;
    private RedisShortTermStore store;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(HOST, PORT);
        factory.setDatabase(TEST_DB);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        assumeTrue(isRedisAvailable(), "本机无 Redis，跳过 Redis 会话存储集成测试");

        prefix = "it:sess:" + UUID.randomUUID().toString().replace("-", "") + ":";
        store = new RedisShortTermStore(template, prefix);
    }

    @AfterEach
    void tearDown() {
        if (template != null && prefix != null) {
            try {
                Set<String> keys = template.keys(prefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    template.delete(keys);
                }
            } catch (Exception ignored) {
                // 清理失败不影响测试结论
            }
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("addTurn - 轮次号原子递增且顺序、时间戳、工具结果不丢")
    void testAddTurnAndReadBack() {
        Instant ts = Instant.ofEpochMilli(1_700_000_000_000L);
        assertEquals(1, store.addTurn("s1", "u1", ConversationTurn.builder()
                .userMessage("Q1").assistantMessage("A1")
                .timestamp(ts).toolResults(List.of("tool:A")).build()));
        assertEquals(2, store.addTurn("s1", "u1", ConversationTurn.builder()
                .userMessage("Q2").assistantMessage("A2").build()));

        List<ConversationTurn> turns = store.getAllTurns("s1");
        assertEquals(2, turns.size());
        assertEquals("Q1", turns.get(0).getUserMessage());
        assertEquals("Q2", turns.get(1).getUserMessage());
        assertEquals(1, turns.get(0).getTurnNumber());
        assertEquals(2, turns.get(1).getTurnNumber());
        assertEquals(ts, turns.get(0).getTimestamp());
        assertEquals(List.of("tool:A"), turns.get(0).getToolResults());
        assertEquals(2, store.getTurnCount("s1"));
    }

    @Test
    @DisplayName("getRecentTurns - 只返回最近 N 轮")
    void testRecentTurns() {
        for (int i = 1; i <= 5; i++) {
            store.addTurn("s2", "u1", ConversationTurn.builder()
                    .userMessage("Q" + i).assistantMessage("A" + i).build());
        }

        List<ConversationTurn> recent = store.getRecentTurns("s2", 3);
        assertEquals(3, recent.size());
        assertEquals("Q3", recent.get(0).getUserMessage());
        assertEquals("Q5", recent.get(2).getUserMessage());
    }

    @Test
    @DisplayName("TTL - 轮次与元数据都带 30 分钟过期")
    void testTtl() {
        store.addTurn("s3", "u1", ConversationTurn.builder()
                .userMessage("Q").assistantMessage("A").build());

        Long turnsTtl = template.getExpire(prefix + "s3:turns");
        Long metaTtl = template.getExpire(prefix + "s3:meta");
        assertNotNull(turnsTtl);
        assertNotNull(metaTtl);
        assertTrue(turnsTtl > 0 && turnsTtl <= AgentConstants.DEFAULT_SESSION_TTL_MS / 1000 + 5);
        assertTrue(metaTtl > 0);
    }

    @Test
    @DisplayName("LTRIM - 超过上限只保留最新 50 轮")
    void testTrimCapsTurns() {
        int total = AgentConstants.MAX_TURNS_PER_SESSION + 5;
        for (int i = 1; i <= total; i++) {
            store.addTurn("s4", "u1", ConversationTurn.builder()
                    .userMessage("Q" + i).assistantMessage("A" + i).build());
        }

        assertEquals(AgentConstants.MAX_TURNS_PER_SESSION, store.getTurnCount("s4"));
        List<ConversationTurn> all = store.getAllTurns("s4");
        assertEquals(6, all.get(0).getTurnNumber(), "最旧的 5 轮应被裁掉");
        assertEquals(total, all.get(all.size() - 1).getTurnNumber());
    }

    @Test
    @DisplayName("summary - 与轮次同 TTL 存储，clearSession 一并清除")
    void testSummary() {
        store.addTurn("s5", "u1", ConversationTurn.builder()
                .userMessage("Q").assistantMessage("A").build());
        store.saveSummary("s5", "[Summary] Q | A");

        assertEquals("[Summary] Q | A", store.getSummary("s5"));
        assertTrue(template.getExpire(prefix + "s5:summary") > 0);

        store.clearSession("s5");
        assertEquals("", store.getSummary("s5"));
        assertEquals(0, store.getTurnCount("s5"));
        assertFalse(store.sessionExists("s5"));
    }

    @Test
    @DisplayName("session - 归属用户与存在性")
    void testSessionOwner() {
        assertFalse(store.sessionExists("s6"));
        assertEquals(Optional.empty(), store.getSessionUserId("s6"));

        store.addTurn("s6", "user-abc", ConversationTurn.builder()
                .userMessage("Q").assistantMessage("A").build());

        assertTrue(store.sessionExists("s6"));
        assertEquals(Optional.of("user-abc"), store.getSessionUserId("s6"));
    }

    @Test
    @DisplayName("multi-instance - 两个实例共享同一会话")
    void testTwoInstancesShareSession() {
        RedisShortTermStore other = new RedisShortTermStore(template, prefix);
        store.addTurn("s7", "u1", ConversationTurn.builder()
                .userMessage("第一轮").assistantMessage("A1").build());

        List<ConversationTurn> turns = other.getRecentTurns("s7", 3);
        assertEquals(1, turns.size());
        assertEquals("第一轮", turns.get(0).getUserMessage());
    }

    @Test
    @DisplayName("concurrency - 同一会话并发写入轮次号不重号")
    void testConcurrentTurnNumbers() throws Exception {
        int threads = 20;
        int perThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> assigned = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        assigned.add(store.addTurn("s8", "u1", ConversationTurn.builder()
                                .userMessage("Q-" + threadId + "-" + i)
                                .assistantMessage("A").build()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发写入应在超时前完成");

        assertEquals(threads * perThread, assigned.size());
        assertEquals(threads * perThread, assigned.stream().distinct().count(), "轮次号不得重复");
        assertFalse(assigned.contains(0), "不得出现降级返回的 0");
        assertEquals(0, store.getFailureCount());
        assertEquals(AgentConstants.MAX_TURNS_PER_SESSION, store.getTurnCount("s8"));
    }

    @Test
    @DisplayName("degrade - Redis 不可用时读写降级而不抛异常")
    void testDegradesWhenRedisDown() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(HOST, 6399);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .build();
        LettuceConnectionFactory deadFactory = new LettuceConnectionFactory(config, client);
        deadFactory.afterPropertiesSet();
        try {
            StringRedisTemplate deadTemplate = new StringRedisTemplate(deadFactory);
            deadTemplate.afterPropertiesSet();
            RedisShortTermStore deadStore = new RedisShortTermStore(deadTemplate, prefix);

            assertEquals(0, deadStore.addTurn("s9", "u1", ConversationTurn.builder()
                    .userMessage("Q").assistantMessage("A").build()));
            assertTrue(deadStore.getAllTurns("s9").isEmpty());
            assertEquals("", deadStore.getSummary("s9"));
            assertEquals(0, deadStore.getTurnCount("s9"));
            assertFalse(deadStore.sessionExists("s9"));
            assertEquals(Optional.empty(), deadStore.getSessionUserId("s9"));
            deadStore.clearSession("s9");
            assertTrue(deadStore.getFailureCount() > 0);
        } finally {
            deadFactory.destroy();
        }
    }

    private boolean isRedisAvailable() {
        try {
            String pong = template.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }
}
