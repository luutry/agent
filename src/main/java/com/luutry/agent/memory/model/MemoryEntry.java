package com.luutry.agent.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 记忆条目，表示长期记忆存储中的单个记忆单元。
 * <p>
 * 用途：为长期记忆存储提供统一的记忆模型，支持记忆衰减和TTL过期机制。
 * 处理问题：长期记忆需要生命周期管理（创建时间、访问次数、衰减分数、过期时间），
 * 以模拟类似人类的记忆保持和遗忘机制。
 * 处理方式：每个条目携带衰减计算所需的元数据；分数基于时间流逝和访问频率动态计算。
 * </p>
 */
@Data
@Builder
public class MemoryEntry {

    /**
     * 记忆条目的唯一标识符
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * 记忆归属的用户ID，用于记忆隔离
     */
    private String userId;

    /**
     * 记忆类型分类
     */
    @Builder.Default
    private MemoryType type = MemoryType.LONG_TERM;

    /**
     * 记忆内容文本
     */
    private String content;

    /**
     * 可选的主题/分类标签，用于分组（如 "query_preference"、"team_insight"）
     */
    private String tag;

    /**
     * 创建时间戳
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 最近访问时间戳，每次检索时更新
     */
    @Builder.Default
    private Instant lastAccessedAt = Instant.now();

    /**
     * 记忆被访问/检索的次数
     */
    @Builder.Default
    private int accessCount = 0;

    /**
     * 过期时间戳；null表示永不过期
     */
    private Instant expireAt;

    /**
     * 当前衰减分数，由MemoryDecayScheduler动态更新。
     * 取值范围：[0.0, 1.0]，值越低越容易被遗忘。
     */
    @Builder.Default
    private double decayScore = 1.0;

    /**
     * 记录一次访问：访问次数加1，并更新最近访问时间。
     */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = Instant.now();
    }

    /**
     * 检查该记忆条目是否已过期。
     */
    public boolean isExpired() {
        if (expireAt == null) {
            return false;
        }
        return Instant.now().isAfter(expireAt);
    }
}
