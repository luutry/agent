package com.luutry.agent.aggregate;

import com.luutry.agent.common.constant.AgentConstants;

/**
 * Token估算工具类，用于上下文预算控制。
 * <p>
 * 用途：从文本内容估算Token数量，用于LLM上下文窗口管理。
 * 处理问题：需要跟踪所有上下文片段的总Token数，以强制执行预算限制。
 * 中文字符比英文单词消耗更多Token，需要校正比例。
 * 处理方式：统计字符数，应用中文字符比例校正（1.5倍），
 * 然后除以基础比例（英文每4个字符一个Token）。
 * </p>
 */
public final class TokenEstimator {

    private TokenEstimator() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 估算给定文本的Token数量。
     *
     * @param text 待估算的输入文本
     * @return 估算的Token数，最小为0
     */
    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int chineseCount = 0;
        int otherCount = 0;

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseCount++;
            } else if (!Character.isWhitespace(c)) {
                otherCount++;
            }
        }

        double tokens = (chineseCount * AgentConstants.CONTEXT_CHINESE_TOKEN_RATIO)
                + (otherCount / 4.0);

        return Math.max(1, (int) Math.ceil(tokens));
    }

    /**
     * 检查字符是否为CJK（中/日/韩）字符。
     *
     * @param c 待检查的字符
     * @return 如果字符在CJK统一表意文字区块中则返回true
     */
    private static boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    /**
     * 检查添加新内容是否会超出Token预算。
     *
     * @param currentTokens 当前已累积的Token数
     * @param newContent    待添加的内容
     * @param budget        最大Token预算
     * @return 如果添加内容会超出预算则返回true
     */
    public static boolean wouldExceedBudget(int currentTokens, String newContent, int budget) {
        int additionalTokens = estimate(newContent);
        return (currentTokens + additionalTokens) > budget;
    }

    /**
     * 截断文本以适应给定的Token预算。
     *
     * @param text       待截断的输入文本
     * @param maxTokens  允许的最大Token数
     * @return 适应Token预算的截断文本
     */
    public static String truncateToFit(String text, int maxTokens) {
        if (text == null || text.isBlank()) {
            return text;
        }

        int estimatedTokens = estimate(text);
        if (estimatedTokens <= maxTokens) {
            return text;
        }

        double ratio = (double) maxTokens / estimatedTokens;
        int maxChars = (int) (text.length() * ratio);

        return text.substring(0, Math.min(maxChars, text.length()));
    }
}
