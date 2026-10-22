package com.luutry.agent.preprocessor.cleaner;

/**
 * 截断边界类型枚举。
 * <p>
 * 用途：标识 CleanResult 中的文本是在哪种边界被截断的，
 * 供下游（QueryRouter/Controller）根据截断质量决定后续策略。
 * </p>
 * <p>
 * 优先级从高到低（越优先越理想）：
 * NONE → SENTENCE → WORD → CODE_POINT → HARD_LIMIT
 * </p>
 */
public enum TruncationBoundary {

    /** 未发生截断，原始文本完整保留 */
    NONE,

    /**
     * 在句子边界截断（句号/问号/叹号等）。
     * 语义完整性最高，下游可放心使用截断后文本。
     */
    SENTENCE,

    /**
     * 在词/空格边界截断。
     * 语义部分保留，下游可正常使用。
     */
    WORD,

    /**
     * 在 Unicode code point 边界截断。
     * 可能切断词组，但保证不产生孤立代理对。
     */
    CODE_POINT,

    /**
     * 硬截断（ReDoS 防护的安全上限）。
     * 在所有正则操作之前执行，语义完整性不保证。
     */
    HARD_LIMIT
}
