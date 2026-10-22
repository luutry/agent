package com.luutry.agent.preprocessor.cleaner;

import lombok.Builder;
import lombok.Getter;

/**
 * 查询清洗结果值对象。
 * <p>
 * 用途：封装清洗后的文本及元信息，供下游（QueryRouter/Controller）
 * 根据截断状态做出差异化决策。
 * </p>
 * <p>
 * 下游消费指南：
 * <ul>
 *   <li>{@code truncated == true}：查询被截断，可提示用户"查询过长已自动截断"，
 *       或切换为摘要检索模式。</li>
 *   <li>{@code hardRejected == true}：原始输入超过 HARD 阈值，由上游决定是否拒答。
 *       清洗器本身不拒答，仍返回截断后的文本。</li>
 *   <li>{@code filteredToEmpty == true}：清洗后文本为空（原始输入全是噪声/控制字符），
 *       下游应走闲聊/无效提问分支。</li>
 *   <li>{@code boundary}：标识截断发生在哪种边界，NONE 表示未截断，
 *       SENTENCE/WORD 语义完整度高，CODE_POINT/HARD_LIMIT 度低。</li>
 *   <li>{@code originalCodePoints}：原始输入的 Unicode code point 数量，
 *       用于可观测指标采集和长度分布统计。</li>
 * </ul>
 * </p>
 */
@Getter
@Builder
public class CleanResult {

    /**
     * 清洗后的查询文本（已移除控制字符、规范化空白、合并标点、脱敏、截断）。
     * 可能为空字符串（当原始输入全是噪声时），但不会为 null。
     */
    private final String cleanedText;

    /**
     * 是否发生了截断（清洗后文本长度超过 WARN 阈值）。
     * true 时下游应：
     * 1. 考虑提示用户"查询过长已截断"
     * 2. 考虑切换为摘要检索模式
     * 3. 缓存 key 使用原文 hash 而非截断后文本
     */
    private final boolean truncated;

    /**
     * 是否超过 HARD 拒答阈值（原始输入长度超过 hardRejectThresholdChars）。
     * 清洗器本身不拒答，仅标记此标志，由上游 Controller 决定是否拒答。
     */
    private final boolean hardRejected;

    /**
     * 原始输入是否触发过 ReDoS 防护硬截断（长度超过 hardLimit = warn × multiplier）。
     * 与 {@link #boundary} 独立：boundary 记录最终截断点，本标志记录"输入曾被硬上限削过"，
     * 避免最终截断覆盖掉硬上限这一事实。
     */
    private final boolean hardLimited;

    /**
     * 截断发生的边界类型。
     * NONE 表示未截断；SENTENCE/WORD 表示在语义边界截断；
     * CODE_POINT 表示在 code point 边界截断；HARD_LIMIT 表示 ReDoS 防护硬截断。
     */
    private final TruncationBoundary boundary;

    /**
     * 原始输入（硬截断后、清洗前）的 Unicode code point 数量。
     * 用于可观测指标采集（查询长度分布、P95/P99 统计）。
     */
    private final int originalCodePoints;

    /**
     * 清洗后文本是否为空（原始输入全是噪声/控制字符/空白，或脱敏+清洗后无内容）。
     * true 时下游应走闲聊/无效提问分支。
     */
    private final boolean filteredToEmpty;

    /**
     * 原始输入的 hash 值（用于缓存 key，避免截断后不同原文产生相同 key）。
     * 当 truncated=true 时，下游应使用此 hash 作为缓存 key 而非 cleanedText。
     */
    private final int originalHash;

    /**
     * 快速判断清洗结果是否可直接用于后续管道。
     * 条件：文本非空 且 未被 HARD 拒答。
     * <p>
     * TODO(doc/TODO/017_清洗元信息无人消费.md): 主链路未调用，QueryRouter 用 cleaned.isBlank() 另判，
     * 与本方法语义已漂移。
     * </p>
     */
    public boolean isUsable() {
        return !filteredToEmpty && !hardRejected;
    }

    /**
     * 创建一个未截断的正常清洗结果。
     *
     * @param cleanedText 清洗后文本
     * @return 正常结果
     */
    public static CleanResult ok(String cleanedText) {
        return CleanResult.builder()
                .cleanedText(cleanedText)
                .truncated(false)
                .hardRejected(false)
                .hardLimited(false)
                .boundary(TruncationBoundary.NONE)
                .originalCodePoints(cleanedText.codePointCount(0, cleanedText.length()))
                .filteredToEmpty(cleanedText.isEmpty())
                .originalHash(cleanedText.hashCode())
                .build();
    }

    /**
     * 创建一个异常降级结果（铁律 #1 兜底）。
     * 降级路径无法判断是否被硬截断，按最坏情况标记 hardLimited=true。
     *
     * @param fallbackText 降级文本（硬截断后、数字已脱敏的输入）
     * @param originalLen  原始输入长度
     * @return 降级结果，标记为 HARD_LIMIT 截断
     */
    public static CleanResult degraded(String fallbackText, int originalLen) {
        return degraded(fallbackText, originalLen, true);
    }

    /**
     * 创建异常降级结果，显式指定硬截断标记。
     *
     * @param fallbackText 降级文本（硬截断后、数字已脱敏的输入）
     * @param originalLen  原始输入长度
     * @param hardLimited  原始输入是否被硬上限截断过
     * @return 降级结果，标记为 HARD_LIMIT 截断
     */
    public static CleanResult degraded(String fallbackText, int originalLen, boolean hardLimited) {
        return CleanResult.builder()
                .cleanedText(fallbackText)
                .truncated(true)
                .hardRejected(false)
                .hardLimited(hardLimited)
                .boundary(TruncationBoundary.HARD_LIMIT)
                .originalCodePoints(originalLen)
                .filteredToEmpty(fallbackText == null || fallbackText.isEmpty())
                .originalHash(fallbackText != null ? fallbackText.hashCode() : 0)
                .build();
    }
}
