package com.luutry.agent.memory.summary;

import com.luutry.agent.memory.model.ConversationTurn;

import java.util.List;

/**
 * 对话摘要压缩器接口，用于压缩多轮对话。
 * <p>
 * 用途：定义对话摘要生成规范，防止长多轮会话中上下文Token爆炸。
 * 处理问题：当对话轮次超过滑动窗口大小时，需要将较早的轮次压缩为摘要，
 * 以保持上下文在Token预算范围内。
 * 处理方式：接口化设计，支持不同压缩策略（简单截断、基于LLM、抽取式）的插拔。
 * </p>
 */
public interface ConversationSummarizer {

    /**
     * 从对话轮次列表生成摘要。
     *
     * @param turns 需要压缩的对话轮次
     * @return 摘要文本，截断至最大长度
     */
    String summarize(List<ConversationTurn> turns);

    /**
     * 将已有摘要与新轮次合并，生成更新后的摘要。
     *
     * @param existingSummary 之前的摘要文本（可能为null或空）
     * @param newTurns        需要纳入的新对话轮次
     * @return 更新后的摘要文本
     */
    String mergeAndSummarize(String existingSummary, List<ConversationTurn> newTurns);

    /**
     * 检查给定轮次数是否需要触发摘要压缩（超过窗口大小）。
     *
     * @param turnCount 当前会话的轮次数
     * @return 需要触发摘要压缩时返回true
     */
    boolean needsSummarization(int turnCount);

    /**
     * 将轮次分为两组：需要压缩的和需要保留原始的。
     *
     * @param turns 所有对话轮次
     * @return 分组结果，包含待压缩轮次和保留轮次
     */
    SplitResult splitForSummarization(List<ConversationTurn> turns);

    /**
     * 轮次分组结果。
     */
    record SplitResult(List<ConversationTurn> toSummarize, List<ConversationTurn> toKeep) {
    }
}
