package com.luutry.agent.memory.longterm;

import com.luutry.agent.memory.model.MemoryEntry;

import java.util.List;

/**
 * 长期记忆存储接口，用于跨会话持久化记忆。
 * <p>
 * 用途：定义长期记忆的存储和检索操作，支持语义搜索、TTL过期和记忆衰减。
 * 处理问题：Agent需要跨会话记住用户偏好、管辖组织、历史洞察等信息，
 * 以提供个性化响应。
 * 处理方式：接口化设计，支持多种实现（内存Mock用于测试，Milvus向量库用于生产）。
 * </p>
 */
public interface LongTermMemoryStore {

    /**
     * 将记忆条目保存到长期存储。
     *
     * @param entry 要持久化的记忆条目
     */
    void save(MemoryEntry entry);

    /**
     * 根据ID获取记忆条目。
     *
     * @param memoryId 记忆唯一标识符
     * @return 记忆条目，未找到时返回null
     */
    MemoryEntry getById(String memoryId);

    /**
     * 获取指定用户的所有记忆。
     *
     * @param userId 用户标识符
     * @return 用户的记忆列表
     */
    List<MemoryEntry> getByUserId(String userId);

    /**
     * 根据用户ID和标签获取记忆。
     *
     * @param userId 用户标识符
     * @param tag    记忆标签/分类
     * @return 过滤后的记忆列表
     */
    List<MemoryEntry> getByTag(String userId, String tag);

    /**
     * 语义搜索相关记忆。
     *
     * @param userId 用户标识符
     * @param query  搜索查询文本
     * @param topK   最大返回结果数
     * @return 相关记忆列表，按相关性分数降序排列
     */
    List<MemoryEntry> search(String userId, String query, int topK);

    /**
     * 根据ID删除记忆条目。
     *
     * @param memoryId 记忆唯一标识符
     * @return 删除成功返回true，未找到返回false
     */
    boolean delete(String memoryId);

    /**
     * 删除指定用户的所有记忆。
     *
     * @param userId 用户标识符
     * @return 删除的条目数
     */
    int deleteByUserId(String userId);

    /**
     * 移除已过期记忆（基于TTL的清理）。
     *
     * @return 移除的条目数
     */
    int removeExpired();

    /**
     * 移除衰减分数低于阈值的记忆。
     *
     * @param threshold 最低可接受衰减分数
     * @return 被遗忘的条目数
     */
    int forgetByDecayScore(double threshold);

    /**
     * 获取指定用户的记忆总数。
     *
     * @param userId 用户标识符
     * @return 记忆数量
     */
    int count(String userId);
}
