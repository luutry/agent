package com.luutry.agent.memory;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.memory.decay.MemoryDecayScheduler;
import com.luutry.agent.memory.longterm.LongTermMemoryStore;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.model.MemoryType;
import com.luutry.agent.memory.shortterm.ShortTermMemoryStore;
import com.luutry.agent.memory.summary.ConversationSummarizer;
import com.luutry.agent.observability.trace.AgentTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一记忆管理器，协调三层记忆模型。
 * <p>
 * 用途：所有记忆操作的统一入口，协调短期记忆、摘要记忆和长期记忆层级，
 * 提供记忆隔离和检索合并能力。
 * 处理问题：Agent需要统一的API来跨不同记忆层级读写数据，无需关心底层实现细节；
 * 同时负责为LLM组装会话上下文。
 * 处理方式：委托给具体的存储实现，同时叠加摘要触发、用户ID隔离、可观测性埋点等横切关注点。
 * </p>
 */
@Slf4j
@Component
public class MemoryManager {

    private final ShortTermMemoryStore shortTermStore;
    private final LongTermMemoryStore longTermStore;
    private final ConversationSummarizer summarizer;
    private final MemoryDecayScheduler decayScheduler;

    public MemoryManager(ShortTermMemoryStore shortTermStore,
                         LongTermMemoryStore longTermStore,
                         ConversationSummarizer summarizer,
                         MemoryDecayScheduler decayScheduler) {
        this.shortTermStore = shortTermStore;
        this.longTermStore = longTermStore;
        this.summarizer = summarizer;
        this.decayScheduler = decayScheduler;
    }

    /**
     * 向会话的短期记忆中添加一轮对话。
     * 当轮次数超过窗口大小时，自动触发摘要压缩。
     *
     * @param sessionId 会话标识符
     * @param userId    归属用户ID
     * @param turn      要添加的对话轮次
     */
    @AgentTrace(stage = "memory_write")
    public void addToSession(String sessionId, String userId, ConversationTurn turn) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(turn, "turn must not be null");

        // 轮次号由存储层分配：Redis 下为原子 INCR，内存下为列表长度，避免并发重号
        int turnNumber = shortTermStore.addTurn(sessionId, userId, turn);
        turn.setTurnNumber(turnNumber);
        log.debug("Added turn #{} to session {} for user {}", turnNumber, sessionId, userId);

        // 检查是否需要触发摘要压缩
        int turnCount = shortTermStore.getTurnCount(sessionId);
        if (summarizer.needsSummarization(turnCount)) {
            triggerSummarization(sessionId);
        }
    }

    /**
     * 保存用户的长期记忆条目。
     *
     * @param userId  归属用户ID
     * @param content 记忆内容
     * @param tag     记忆分类标签
     */
    @AgentTrace(stage = "memory_write")
    public void saveLongTerm(String userId, String content, String tag) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(content, "content must not be null");

        MemoryEntry entry = MemoryEntry.builder()
                .userId(userId)
                .type(MemoryType.LONG_TERM)
                .content(content)
                .tag(tag)
                .expireAt(Instant.now().plus(AgentConstants.LONG_TERM_MEMORY_TTL_MS, ChronoUnit.MILLIS))
                .build();

        longTermStore.save(entry);
        log.debug("Saved long-term memory for user {}: tag={}, content length={}", userId, tag, content.length());
    }

    /**
     * 检索与查询相关的长期记忆。
     *
     * @param userId 用户标识符
     * @param query  搜索查询文本
     * @param topK   最大返回结果数
     * @return 相关记忆条目列表
     */
    @AgentTrace(stage = "memory_retrieve")
    public List<MemoryEntry> retrieveRelevant(String userId, String query, int topK) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(query, "query must not be null");

        List<MemoryEntry> results = longTermStore.search(userId, query, topK);
        log.debug("Retrieved {} relevant memories for user {} with query '{}'", results.size(), userId, query);
        return results;
    }

    /**
     * 获取完整的会话上下文，用于组装LLM输入。
     * 合并摘要（如果存在）+ 近期原始对话轮次。
     *
     * @param sessionId 会话标识符
     * @return 包含摘要和近期轮次的会话上下文
     */
    public SessionContext getSessionContext(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        String summary = summaryOf(sessionId);
        List<ConversationTurn> recentTurns = shortTermStore.getAllTurns(sessionId);

        // 如果已触发摘要压缩，只保留原始轮次（摘要已包含其余内容）
        if (summarizer.needsSummarization(recentTurns.size())) {
            ConversationSummarizer.SplitResult split = summarizer.splitForSummarization(recentTurns);
            return new SessionContext(summary, split.toKeep(), DataSource.MEMORY);
        }

        return new SessionContext(summary, recentTurns, DataSource.MEMORY);
    }

    /**
     * 获取会话最近 N 轮原始对话（按时间顺序，最旧在前）。
     * 与 {@link #getSessionContext} 不同，不走摘要压缩，供指代消解取最近实体使用。
     *
     * @param sessionId 会话标识符
     * @param limit     最大返回轮次数
     * @return 最近 N 轮对话，无数据时返回空列表
     */
    public List<ConversationTurn> getRecentTurns(String sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return shortTermStore.getRecentTurns(sessionId, Math.max(1, limit));
    }

    /**
     * 清除会话的短期记忆和摘要。
     *
     * @param sessionId 会话标识符
     */
    public void clearSession(String sessionId) {
        shortTermStore.clearSession(sessionId);
        log.debug("Cleared session {} (short-term + summary)", sessionId);
    }

    /**
     * 获取会话轮次数。
     *
     * @param sessionId 会话标识符
     * @return 轮次数
     */
    public int getSessionTurnCount(String sessionId) {
        return shortTermStore.getTurnCount(sessionId);
    }

    /**
     * 获取会话归属用户，用于入口处校验 sessionId 是否属于当前用户。
     *
     * @param sessionId 会话标识符
     * @return 归属用户ID；会话不存在或存储不可用时为空
     */
    public Optional<String> getSessionUserId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return shortTermStore.getSessionUserId(sessionId);
    }

    /**
     * 获取当前会话摘要文本。
     *
     * @param sessionId 会话标识符
     * @return 摘要文本，无摘要时返回空字符串
     */
    public String getSessionSummary(String sessionId) {
        return summaryOf(sessionId);
    }

    /**
     * 获取用户的所有长期记忆。
     *
     * @param userId 用户标识符
     * @return 记忆条目列表
     */
    public List<MemoryEntry> getUserLongTermMemories(String userId) {
        return longTermStore.getByUserId(userId);
    }

    /**
     * 执行用户长期记忆的衰减周期。
     *
     * @param userId 用户标识符
     * @return 被遗忘的条目数
     */
    public int runDecayCycle(String userId) {
        return decayScheduler.runDecayCycle(userId);
    }

    /** 摘要与轮次同源存储：重启/多实例后仍能读到同一份摘要。 */
    private String summaryOf(String sessionId) {
        String summary = shortTermStore.getSummary(sessionId);
        return summary == null ? "" : summary;
    }

    /**
     * 触发会话摘要压缩：压缩较早的对话轮次，保留近期轮次。
     */
    private void triggerSummarization(String sessionId) {
        List<ConversationTurn> allTurns = shortTermStore.getAllTurns(sessionId);
        ConversationSummarizer.SplitResult split = summarizer.splitForSummarization(allTurns);

        if (split.toSummarize().isEmpty()) {
            return;
        }

        // 获取已有摘要并合并
        String existingSummary = summaryOf(sessionId);
        String updatedSummary = summarizer.mergeAndSummarize(existingSummary, split.toSummarize());
        shortTermStore.saveSummary(sessionId, updatedSummary);

        log.debug("Summarization triggered for session {}: {} turns compressed, summary length={}",
                sessionId, split.toSummarize().size(), updatedSummary.length());
    }

    /**
     * 为LLM输入组装的会话上下文。
     */
    public record SessionContext(
            String summary,
            List<ConversationTurn> recentTurns,
            DataSource source
    ) {
        /**
         * 检查该上下文是否包含任何内容。
         */
        public boolean isEmpty() {
            return (summary == null || summary.isBlank()) && recentTurns.isEmpty();
        }

        /**
         * 估算该上下文的总Token数。
         */
        public int estimateTokens() {
            int tokens = summary != null ? summary.length() / 4 : 0;
            for (ConversationTurn turn : recentTurns) {
                tokens += turn.estimateTokens();
            }
            return tokens;
        }

        /**
         * 获取不可修改的近期轮次列表。
         */
        public List<ConversationTurn> getRecentTurns() {
            return Collections.unmodifiableList(recentTurns);
        }
    }
}
