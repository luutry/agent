package com.luutry.agent.memory.summary;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 滑动窗口摘要压缩器，压缩较早的轮次同时保留近期轮次的原始内容。
 * <p>
 * 用途：使用滑动窗口策略实现对话摘要压缩。
 * 处理问题：多轮对话会导致Token爆炸；此压缩器只保留最近的WINDOW_SIZE轮原始文本，
 * 将较早的轮次压缩为摘要。
 * 处理方式：当轮次超过窗口大小时，提取较早轮次，生成简单的文本摘要
 * （关键信息拼接 + 截断），保留近期轮次不变。
 * </p>
 * <p>
 * 当前实现使用简单文本拼接 + 截断。
 * TODO: 集成基于LLM的语义压缩（通过LangChain4j）。
 * </p>
 */
@Slf4j
@Component
public class SlidingWindowSummarizer implements ConversationSummarizer {

    /**
     * 摘要前缀标记，便于识别
     */
    private static final String SUMMARY_PREFIX = "[Summary] ";

    /**
     * 轮次摘要之间的分隔符
     */
    private static final String TURN_SEPARATOR = " | ";

    @Override
    public String summarize(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(SUMMARY_PREFIX);
        for (int i = 0; i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            if (i > 0) {
                sb.append(TURN_SEPARATOR);
            }
            // 提取每轮对话的关键信息
            sb.append(extractTurnSummary(turn));
        }

        String result = sb.toString();
        // 截断至最大长度
        if (result.length() > AgentConstants.MAX_SUMMARY_LENGTH) {
            result = result.substring(0, AgentConstants.MAX_SUMMARY_LENGTH - 3) + "...";
            log.debug("Summary truncated to {} chars", AgentConstants.MAX_SUMMARY_LENGTH);
        }

        log.debug("Generated summary from {} turns, length={} chars", turns.size(), result.length());
        return result;
    }

    @Override
    public String mergeAndSummarize(String existingSummary, List<ConversationTurn> newTurns) {
        if (newTurns == null || newTurns.isEmpty()) {
            return existingSummary != null ? existingSummary : "";
        }

        String newSummary = summarize(newTurns);

        if (existingSummary == null || existingSummary.isBlank()) {
            return newSummary;
        }

        // 合并：将已有摘要与新摘要组合，然后截断
        String merged = existingSummary;
        // 去除已有摘要的前缀以便干净合并
        if (merged.startsWith(SUMMARY_PREFIX)) {
            merged = merged.substring(SUMMARY_PREFIX.length());
        }

        String combined = SUMMARY_PREFIX + merged + TURN_SEPARATOR + newSummary.substring(SUMMARY_PREFIX.length());
        if (combined.length() > AgentConstants.MAX_SUMMARY_LENGTH) {
            combined = combined.substring(0, AgentConstants.MAX_SUMMARY_LENGTH - 3) + "...";
        }

        log.debug("Merged summary: existing {} chars + new {} turns -> {} chars",
                existingSummary.length(), newTurns.size(), combined.length());
        return combined;
    }

    @Override
    public boolean needsSummarization(int turnCount) {
        return turnCount > AgentConstants.SUMMARY_WINDOW_SIZE;
    }

    @Override
    public SplitResult splitForSummarization(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return new SplitResult(Collections.emptyList(), Collections.emptyList());
        }

        if (turns.size() <= AgentConstants.SUMMARY_WINDOW_SIZE) {
            return new SplitResult(Collections.emptyList(), new ArrayList<>(turns));
        }

        int splitIndex = turns.size() - AgentConstants.SUMMARY_WINDOW_SIZE;
        List<ConversationTurn> toSummarize = new ArrayList<>(turns.subList(0, splitIndex));
        List<ConversationTurn> toKeep = new ArrayList<>(turns.subList(splitIndex, turns.size()));

        log.debug("Split {} turns: {} to summarize, {} to keep",
                turns.size(), toSummarize.size(), toKeep.size());
        return new SplitResult(toSummarize, toKeep);
    }

    /**
     * 从单轮对话中提取简要摘要。
     */
    private String extractTurnSummary(ConversationTurn turn) {
        StringBuilder sb = new StringBuilder();
        sb.append("Q:").append(truncate(turn.getUserMessage(), 80));
        sb.append(" A:").append(truncate(turn.getAssistantMessage(), 80));

        if (turn.getToolResults() != null && !turn.getToolResults().isEmpty()) {
            sb.append(" Tools:").append(turn.getToolResults().size()).append(" called");
        }
        return sb.toString();
    }

    /**
     * 将文本截断至最大长度，超出部分用省略号替代。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }
}
