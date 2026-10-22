package com.luutry.agent.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 三层记忆模型的记忆类型枚举。
 * <p>
 * 用途：区分不同记忆层级，用于存储、检索和生命周期管理。
 * 处理问题：Agent需要管理具有不同生命周期和访问模式的记忆数据。
 * 处理方式：基于枚举的类型标记，每个层级有独立的存储实现。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum MemoryType {

    /**
     * 短期会话记忆：当前会话中的原始对话轮次。
     * 存储于Redis，TTL = 30分钟。
     */
    SHORT_TERM("short_term", "当前会话对话轮次"),

    /**
     * 会话摘要记忆：对较早对话轮次的压缩摘要。
     * 当对话轮次超过滑动窗口大小时生成。
     */
    SUMMARY("summary", "压缩会话摘要"),

    /**
     * 跨会话长期记忆：用户偏好、管辖组织、历史结论等。
     * 以向量嵌入形式存储于向量数据库，支持语义检索、TTL过期和记忆遗忘。
     */
    LONG_TERM("long_term", "跨会话持久化记忆");

    private final String code;
    private final String description;
}
