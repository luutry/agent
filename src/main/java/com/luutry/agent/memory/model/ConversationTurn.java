package com.luutry.agent.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 单次对话轮次（一次用户提问 + Agent响应）。
 * <p>
 * 用途：表示对话会话中的一轮交互。
 * 处理问题：需要存储原始对话数据，包括用户输入、Agent响应
 * 和任何工具结果，用于会话上下文重建。
 * 处理方式：不可变的记录式对象，带时间戳排序。
 * </p>
 */
@Data
@Builder
public class ConversationTurn {

    /**
     * 轮次在会话中的序号（从1开始）
     */
    private int turnNumber;

    /**
     * 用户输入消息
     */
    private String userMessage;

    /**
     * Agent响应消息
     */
    private String assistantMessage;

    /**
     * 本轮次中产生的工具结果（工具名 -> 结果摘要）
     */
    @Builder.Default
    private List<String> toolResults = Collections.emptyList();

    /**
     * 该轮次创建时的时间戳
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * 该轮次的估算Token数（用户 + 助手消息长度 / 4）
     */
    public int estimateTokens() {
        int charLen = (userMessage != null ? userMessage.length() : 0)
                + (assistantMessage != null ? assistantMessage.length() : 0);
        for (String toolResult : toolResults) {
            charLen += toolResult != null ? toolResult.length() : 0;
        }
        return charLen / 4;
    }
}
