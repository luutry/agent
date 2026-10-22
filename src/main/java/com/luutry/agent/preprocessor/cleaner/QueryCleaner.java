package com.luutry.agent.preprocessor.cleaner;

/**
 * 查询输入清洗器接口。
 * <p>
 * 用途：定义查询清洗的标准契约，实现类负责：
 * 移除控制字符、规范化空白、合并重复标点、PII 脱敏、语义边界截断。
 * </p>
 * <p>
 * 返回值 {@link CleanResult} 包含丰富的元信息，供下游做出差异化决策：
 * <ul>
 *   <li>truncated：是否被截断（下游可提示用户、切换摘要检索）</li>
 *   <li>hardRejected：是否超过 HARD 阈值（上游决定是否拒答）</li>
 *   <li>boundary：截断发生在哪种边界（NONE/SENTENCE/WORD/CODE_POINT/HARD_LIMIT）</li>
 *   <li>filteredToEmpty：清洗后是否为空（下游走闲聊/无效分支）</li>
 *   <li>originalCodePoints：原始 code point 数（可观测指标采集）</li>
 *   <li>originalHash：原始输入 hash（缓存 key 用原文 hash 而非截断后文本）</li>
 * </ul>
 * </p>
 */
public interface QueryCleaner {

    /**
     * 清洗原始用户输入。
     * <p>
     * 管道顺序：
     * 1. 空值/空白快速返回
     * 2. ReDoS 防护硬截断（hardLimit = warnThreshold × multiplier）
     * 3. 移除控制字符（C0/C1/零宽/双向控制符/BOM）
     * 4. 规范化空白（含全角空格/NBSP）
     * 5. 合并连续重复标点
     * 6. 移除首部标点噪声
     * 7. PII 脱敏（在截断前执行，防止截断破坏 PII 模式）
     * 8. 语义边界截断（SENTENCE → WORD → CODE_POINT 三级回退）
     * </p>
     *
     * @param rawQuery 原始用户输入，可能包含噪声
     * @return 清洗结果，包含清洗后文本和截断元信息；不会返回 null
     */
    CleanResult clean(String rawQuery);
}
