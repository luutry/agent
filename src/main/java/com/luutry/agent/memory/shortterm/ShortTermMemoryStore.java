package com.luutry.agent.memory.shortterm;

import com.luutry.agent.memory.model.ConversationTurn;

import java.util.List;
import java.util.Optional;

/**
 * 短期记忆存储接口，用于管理会话中的对话轮次与摘要。
 * <p>
 * 用途：定义当前会话对话历史的增删改查操作。
 * 处理问题：每个会话需要隔离的、带TTL限制的短期存储，
 * 用于保存原始对话轮次；实现可以是Redis或内存存储。
 * 处理方式：基于接口设计，允许在不改变业务逻辑的情况下切换实现。
 * </p>
 */
public interface ShortTermMemoryStore {

    /**
     * 向会话中追加一轮对话，轮次号由存储层原子分配。
     * <p>
     * 多实例部署时轮次号必须由存储端生成（Redis INCR），否则并发写同一会话会出现重号。
     * </p>
     *
     * @param sessionId 会话唯一标识符
     * @param userId    归属用户ID，用于隔离
     * @param turn      要添加的对话轮次
     * @return 本次分配的轮次号（从1开始）；存储不可用时返回0
     */
    int addTurn(String sessionId, String userId, ConversationTurn turn);

    /**
     * 获取会话的所有对话轮次。
     *
     * @param sessionId 会话唯一标识符
     * @return 对话轮次列表，按turnNumber升序排列
     */
    List<ConversationTurn> getAllTurns(String sessionId);

    /**
     * 获取会话中最近的N轮对话。
     *
     * @param sessionId 会话唯一标识符
     * @param limit     最大返回轮次数
     * @return 近期轮次列表
     */
    List<ConversationTurn> getRecentTurns(String sessionId, int limit);

    /**
     * 获取会话中的总轮次数。
     *
     * @param sessionId 会话唯一标识符
     * @return 轮次数
     */
    int getTurnCount(String sessionId);

    /**
     * 读取会话摘要，与轮次同生命周期存储，避免重启后上下文只剩半截。
     *
     * @param sessionId 会话唯一标识符
     * @return 摘要文本；无摘要或存储不可用时返回空字符串
     */
    String getSummary(String sessionId);

    /**
     * 保存会话摘要。
     *
     * @param sessionId 会话唯一标识符
     * @param summary   摘要文本
     */
    void saveSummary(String sessionId, String summary);

    /**
     * 清除会话的所有轮次与摘要（会话结束或重置）。
     *
     * @param sessionId 会话唯一标识符
     */
    void clearSession(String sessionId);

    /**
     * 检查会话是否存在且仍然有效（未过期）。
     *
     * @param sessionId 会话唯一标识符
     * @return 会话存在且活跃时返回true
     */
    boolean sessionExists(String sessionId);

    /**
     * 获取会话关联的用户ID。
     *
     * @param sessionId 会话唯一标识符
     * @return 会话存在时返回用户ID
     */
    Optional<String> getSessionUserId(String sessionId);
}
