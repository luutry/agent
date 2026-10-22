package com.luutry.agent.memory.decay;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.longterm.LongTermMemoryStore;
import com.luutry.agent.memory.model.MemoryEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 记忆衰减调度器，负责管理记忆遗忘和分数重计算。
 * <p>
 * 用途：模拟类似人类记忆的衰减机制，长期未使用的记忆会逐渐淡化。
 * 处理问题：如果没有衰减机制，长期记忆会无限增长并积累过期条目。
 * 处理方式：采用指数时间衰减结合访问频率增强因子计算动态衰减分数。
 * 低于阈值的记忆会被定期清理。
 * 公式：decayScore = e^(-λ × 距上次访问小时数) × (1 + log(1 + 访问次数))
 * </p>
 */
@Slf4j
@Component
public class MemoryDecayScheduler {

    private final LongTermMemoryStore longTermStore;

    public MemoryDecayScheduler(LongTermMemoryStore longTermStore) {
        this.longTermStore = longTermStore;
    }

    /**
     * 根据当前时间和访问模式重新计算记忆条目的衰减分数。
     *
     * @param entry 需要重新计算的记忆条目
     * @return 更新后的衰减分数
     */
    public double recalculateDecayScore(MemoryEntry entry) {
        double timeDecay = computeTimeDecay(entry.getLastAccessedAt());
        double accessBoost = computeAccessBoost(entry.getAccessCount());
        double newScore = Math.min(1.0, timeDecay * accessBoost);

        entry.setDecayScore(newScore);
        log.trace("Recalculated decay for memory {}: timeDecay={}, accessBoost={}, score={}",
                entry.getId(), String.format("%.4f", timeDecay),
                String.format("%.4f", accessBoost), String.format("%.4f", newScore));
        return newScore;
    }

    /**
     * 执行完整的衰减周期：重算所有分数、移除过期条目、遗忘低分条目。
     *
     * @param userId 目标用户ID（如果为null则处理所有用户）
     * @return 被遗忘的条目数
     */
    public int runDecayCycle(String userId) {
        log.debug("Starting memory decay cycle for userId={}", userId);

        // 步骤1：先移除已过期条目
        int expiredCount = longTermStore.removeExpired();

        // 步骤2：重算分数并遗忘低分条目
        List<MemoryEntry> entries;
        if (userId != null) {
            entries = longTermStore.getByUserId(userId);
        } else {
            // 当前实现不支持全量衰减（无用户ID过滤）
            log.warn("Full decay cycle without userId filter is not supported in current implementation");
            return expiredCount;
        }

        int forgottenCount = 0;
        for (MemoryEntry entry : entries) {
            double newScore = recalculateDecayScore(entry);
            if (newScore < AgentConstants.MEMORY_DECAY_THRESHOLD) {
                longTermStore.delete(entry.getId());
                forgottenCount++;
                log.debug("Forgot memory: id={}, score={}", entry.getId(), String.format("%.4f", newScore));
            }
        }

        log.info("Decay cycle completed: expired={}, forgotten={}, remaining={}",
                expiredCount, forgottenCount, longTermStore.count(userId));
        return forgottenCount;
    }

    /**
     * 使用指数衰减计算时间衰减因子。
     * 公式：e^(-λ × 距上次访问小时数)
     *
     * @param lastAccessedAt 上次访问时间戳
     * @return 衰减因子，取值范围 (0, 1]
     */
    double computeTimeDecay(Instant lastAccessedAt) {
        double hoursSinceAccess = Duration.between(lastAccessedAt, Instant.now()).toMinutes() / 60.0;
        if (hoursSinceAccess < 0) {
            hoursSinceAccess = 0;
        }
        return Math.exp(-AgentConstants.MEMORY_DECAY_LAMBDA * hoursSinceAccess);
    }

    /**
     * 计算访问频率增强因子。
     * 公式：1 + log(1 + 访问次数)
     *
     * @param accessCount 访问次数
     * @return 增强因子，>= 1.0
     */
    double computeAccessBoost(int accessCount) {
        return 1.0 + Math.log(1.0 + accessCount);
    }
}
