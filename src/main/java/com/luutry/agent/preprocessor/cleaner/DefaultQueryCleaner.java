package com.luutry.agent.preprocessor.cleaner;

import com.luutry.agent.observability.trace.AgentTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 默认查询清洗器实现。
 * <p>
 * 职责：移除控制字符、规范化空白（含全角空格/NBSP）、合并连续重复标点、
 * 移除首部标点噪声、PII 脱敏、语义边界截断。
 * </p>
 * <p>
 * 管道顺序（不可调整）：
 * 1. 空值/空白快速返回
 * 2. ReDoS 防护硬截断（hardLimit = warnThreshold × multiplier，在所有正则之前）
 * 3. 移除不可见字符（C0/C1/零宽/双向控制符/BOM/软连字符/word joiner，含 ZWJ/ZWNJ）
 * 4. 全角字母数字归一化为半角（PII 与工号能被识别的先决条件）
 * 5. 规范化空白（含全角空格 \u3000、NBSP \u00A0）
 * 6. 合并连续重复标点（全角+半角，≥2 个相同标点合并为 1 个）
 * 7. 移除首部标点噪声
 * 8. PII 脱敏（在截断前执行，防止截断破坏手机号/身份证等完整模式）
 * 9. 语义边界截断（SENTENCE → WORD → CODE_POINT 三级回退）
 * </p>
 * <p>
 * 安全设计：
 * <ul>
 *   <li>入口先做硬截断（hardLimitChars），防止超长输入导致正则 CPU 放大（ReDoS 防护）</li>
 *   <li>整个 clean() 方法 try-catch Throwable 兜底，异常降级返回"数字已脱敏"的硬截断输入，
 *       遵循 Agent 铁律 R8：预处理链路失败绝不能把业务搞挂，也不能把 PII 放出去</li>
 *   <li>零宽/格式字符全部移除（含 ZWJ/ZWNJ）：查询不需要显示级连字，
 *       保留它们只会被用来拆断 PII 数字串或关键词</li>
 *   <li>截断按 code point 安全执行，不切断 surrogate pair</li>
 * </ul>
 * </p>
 * <p>
 * 配置驱动：所有阈值由 {@link CleanPolicy} 管理，支持通过 application.yml 动态调整，
 * 上线后根据查询长度分布指标（P95/P99）驱动优化。
 * </p>
 */
@Slf4j
@Component
public class DefaultQueryCleaner implements QueryCleaner {

    /**
     * 匹配不可见字符的正则。
     * 包含：C0 控制符（\x00-\x08, \x0B, \x0C, \x0E-\x1F）、DEL(\x7F)、C1 控制符(\u0080-\u009F)、
     * 软连字符(\u00AD)、Arabic letter mark(\u061C)、蒙古文元音分隔符(\u180E)、
     * 零宽字符与双向标记(\u200B-\u200F，含 ZWSP/ZWNJ/ZWJ/LRM/RLM)、行/段分隔符(\u2028-\u2029)、
     * 双向控制符(\u202A-\u202E)、word joiner 与不可见运算符(\u2060-\u2064)、
     * 隔离区双向控制符(\u2066-\u2069)、BOM(\uFEFF)、行间注记符(\uFFF9-\uFFFB)。
     * <p>
     * 这些字符对查询语义零贡献，但足以把手机号拆成两段躲过 PII 正则、
     * 或把"绩效"拆成"绩/效"躲过关键词匹配，因此一律移除。
     * </p>
     */
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F"
                    + "\\u0080-\\u009F"
                    + "\\u00AD\\u061C\\u180E"
                    + "\\u200B-\\u200F"
                    + "\\u2028-\\u2029"
                    + "\\u202A-\\u202E"
                    + "\\u2060-\\u2064"
                    + "\\u2066-\\u2069"
                    + "\\uFEFF"
                    + "\\uFFF9-\\uFFFB]"
    );

    /**
     * 匹配空白字符的正则（增强版）。
     * 除 \s 覆盖的 ASCII 空白外，额外包含：
     * - \u3000（全角表意文字空格，中文输入常见）
     * - \u00A0（NBSP 不间断空格）
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s\\u3000\\u00A0]+");

    /**
     * 匹配连续重复标点的正则。
     * 包含全角（！？。，、；：）和半角（?!.,;:），
     * 至少 2 个相同标点即合并为 1 个。
     */
    private static final Pattern REPEATED_PUNCT_PATTERN = Pattern.compile(
            "([!?！？。，、；：.,;:])\\1+"
    );

    /** 匹配首部标点噪声的正则 */
    private static final Pattern LEADING_PUNCT_PATTERN = Pattern.compile("^[!?！？。，、；：\\s]+");

    /** 句子终止符集合（中英文句号、问号、叹号、分号） */
    private static final String SENTENCE_TERMINATORS = "。？！.?！;；";

    /** 清洗策略配置（由 Spring 注入，支持 application.yml 动态调整） */
    private final CleanPolicy policy;

    /** 查询输入脱敏器（在截断前执行 PII 脱敏） */
    private final QueryInputDesensitizer desensitizer;

    /**
     * 构造函数，注入清洗策略和脱敏器。
     *
     * @param policy       清洗策略配置
     * @param desensitizer 查询输入脱敏器
     */
    public DefaultQueryCleaner(CleanPolicy policy, QueryInputDesensitizer desensitizer) {
        this.policy = policy;
        this.desensitizer = desensitizer;
    }

    @Override
    @AgentTrace(stage = "query_clean")
    public CleanResult clean(String rawQuery) {
        // 步骤0：空值/空白快速返回
        if (rawQuery == null || rawQuery.isBlank()) {
            return CleanResult.builder()
                    .cleanedText("")
                    .truncated(false)
                    .hardRejected(false)
                    .boundary(TruncationBoundary.NONE)
                    .originalCodePoints(0)
                    .filteredToEmpty(true)
                    .originalHash(rawQuery != null ? rawQuery.hashCode() : 0)
                    .build();
        }

        // 记录原始 code point 数量（用于可观测指标）
        int originalCodePoints = rawQuery.codePointCount(0, rawQuery.length());
        int originalHash = rawQuery.hashCode();

        // 步骤1：HARD 拒答阈值检查（仅标记，不拒答）
        boolean hardRejected = rawQuery.length() > policy.getHardRejectThresholdChars();

        // 步骤2：ReDoS 防护硬截断（在所有正则操作之前执行）
        int hardLimitChars = policy.getWarnThresholdChars() * policy.getHardLimitMultiplier();
        boolean hardLimited = rawQuery.length() > hardLimitChars;
        String input = hardLimited
                ? safeCodePointTruncate(rawQuery, hardLimitChars)
                : rawQuery;

        try {
            String result = input;

            // 步骤3：移除不可见字符（C0/C1/零宽/双向控制符/BOM/软连字符/word joiner）
            result = removeControlChars(result);

            // 步骤4：全角字母数字 → 半角（否则全角手机号/工号既漏脱敏也无法取参）
            result = normalizeFullWidthAlnum(result);

            // 步骤5：规范化空白（含全角空格 \u3000、NBSP \u00A0 → 单个空格）
            result = normalizeWhitespace(result);

            // 步骤6：合并连续重复标点（全角+半角，≥2 个相同标点合并为 1 个）
            result = collapseRepeatedPunct(result);

            // 步骤7：移除首部标点噪声
            result = stripLeadingPunct(result);

            // 步骤8：去除首尾空白
            result = result.trim();

            // 步骤9：PII 脱敏（在截断前执行，防止截断破坏完整 PII 模式）
            result = desensitizer.desensitize(result);

            // 步骤10：语义边界截断（SENTENCE → WORD → CODE_POINT 三级回退）
            TruncationResult truncResult = truncateWithSemanticBoundary(
                    result, policy.getWarnThresholdChars(), policy.getSemanticBoundaryChars());
            result = truncResult.text();
            boolean truncated = truncResult.boundary() != TruncationBoundary.NONE;

            // 步骤11：检查清洗后是否为空
            boolean filteredToEmpty = result.isEmpty();

            return CleanResult.builder()
                    .cleanedText(result)
                    .truncated(truncated)
                    .hardRejected(hardRejected)
                    .hardLimited(hardLimited)
                    .boundary(truncResult.boundary())
                    .originalCodePoints(originalCodePoints)
                    .filteredToEmpty(filteredToEmpty)
                    .originalHash(originalHash)
                    .build();

        } catch (Throwable t) {
            // 铁律 R8：预处理失败降级返回硬截断输入；但必须先把数字脱敏，不能放原文
            log.warn("查询清洗异常，降级返回数字脱敏后的输入: {}", t.getMessage());
            return CleanResult.degraded(
                    QueryInputDesensitizer.failClosedMask(input), originalCodePoints, hardLimited);
        }
    }

    // ==================== 清洗步骤方法（包级可见，便于单测） ====================

    /**
     * 移除控制字符。
     * 覆盖 C0/C1 控制符、零宽/格式字符（含 ZWJ/ZWNJ）、BOM、行/段分隔符、双向控制符。
     */
    String removeControlChars(String input) {
        return CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * 全角字母数字归一化为半角（U+FF10-FF19 / FF21-FF3A / FF41-FF5A）。
     * <p>
     * 只转换字母与数字：全角标点（？！，。）保留原样，避免影响下游标点合并与规则拆分。
     * 覆盖常见输入法产出的"１３８１２３４５６７８""Ｅ１００１"。
     * </p>
     */
    String normalizeFullWidthAlnum(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char mapped = c;
            if ((c >= '\uFF10' && c <= '\uFF19')
                    || (c >= '\uFF21' && c <= '\uFF3A')
                    || (c >= '\uFF41' && c <= '\uFF5A')) {
                mapped = (char) (c - 0xFEE0);
                changed = true;
            }
            sb.append(mapped);
        }
        return changed ? sb.toString() : input;
    }

    /**
     * 规范化空白字符。
     * 将连续空白（含全角空格 \u3000、NBSP \u00A0）替换为单个空格。
     * <p>
     * TODO(doc/TODO/015_换行与列表结构在清洗中被抹平.md): 换行被一并压成空格，
     * 序号列表因此失去分隔信号，复合拆分识别不了列表项。
     * </p>
     */
    String normalizeWhitespace(String input) {
        return WHITESPACE_PATTERN.matcher(input).replaceAll(" ");
    }

    /**
     * 合并连续重复标点。
     * 全角和半角标点均处理，至少 2 个相同标点合并为 1 个。
     */
    String collapseRepeatedPunct(String input) {
        return REPEATED_PUNCT_PATTERN.matcher(input).replaceAll("$1");
    }

    /**
     * 移除字符串首部标点噪声。
     */
    String stripLeadingPunct(String input) {
        return LEADING_PUNCT_PATTERN.matcher(input).replaceAll("");
    }

    // ==================== 截断策略方法 ====================

    /**
     * 语义边界截断：SENTENCE → WORD → CODE_POINT 三级回退。
     * <p>
     * 策略：
     * 1. 若文本长度 ≤ maxLen，不截断，返回 NONE
     * 2. 在 [maxLen - searchWindow, maxLen] 范围内搜索句子边界（。？！.?!）
     * 3. 若找到句子边界，在该位置截断，返回 SENTENCE
     * 4. 否则在 [maxLen - searchWindow, maxLen] 范围内搜索词/空格边界
     * 5. 若找到词边界，在该位置截断，返回 WORD
     * 6. 否则在 maxLen 位置按 code point 安全截断，返回 CODE_POINT
     * </p>
     *
     * @param text         待截断文本
     * @param maxLen       目标最大长度
     * @param searchWindow 向前搜索边界的窗口大小
     * @return 截断结果（文本 + 边界类型）
     */
    TruncationResult truncateWithSemanticBoundary(String text, int maxLen, int searchWindow) {
        if (text == null || text.length() <= maxLen) {
            return new TruncationResult(text, TruncationBoundary.NONE);
        }

        // 尝试句子边界
        int sentenceBoundary = findSentenceBoundary(text, maxLen, searchWindow);
        if (sentenceBoundary > 0) {
            return new TruncationResult(text.substring(0, sentenceBoundary), TruncationBoundary.SENTENCE);
        }

        // 尝试词/空格边界
        int wordBoundary = findWordBoundary(text, maxLen, searchWindow);
        if (wordBoundary > 0) {
            return new TruncationResult(text.substring(0, wordBoundary), TruncationBoundary.WORD);
        }

        // 兜底：code point 安全截断
        return new TruncationResult(safeCodePointTruncate(text, maxLen), TruncationBoundary.CODE_POINT);
    }

    /**
     * 在 [from - searchWindow, from) 范围内向前搜索句子边界。
     * 句子边界定义为句子终止符（。？！.?!;；）的位置。
     * 搜索范围为 [searchStart, from)，确保截断后文本长度 ≤ from。
     *
     * @return 句子边界位置（截断点，包含终止符），未找到返回 -1
     */
    int findSentenceBoundary(String text, int from, int searchWindow) {
        int searchStart = Math.max(0, from - searchWindow);
        // 从 from-1 向前搜索，确保截断后长度 ≤ from
        for (int i = Math.min(from - 1, text.length() - 1); i >= searchStart; i--) {
            if (SENTENCE_TERMINATORS.indexOf(text.charAt(i)) >= 0) {
                return i + 1; // 截断点在终止符之后
            }
        }
        return -1;
    }

    /**
     * 在 [from - searchWindow, from] 范围内向前搜索词/空格边界。
     * 使用 BreakIterator 取离 from 最近的边界；边界落在窗口外则放弃，交给 CODE_POINT 兜底。
     *
     * @return 词边界位置，未找到返回 -1
     */
    int findWordBoundary(String text, int from, int searchWindow) {
        int searchStart = Math.max(0, from - searchWindow);
        BreakIterator wordIterator = BreakIterator.getWordInstance(Locale.CHINESE);
        wordIterator.setText(text);

        // preceding 返回 from 之前最近的边界；取最早的那个会把截断点推到窗口左端，白丢一整段内容
        int boundary = wordIterator.preceding(Math.min(from + 1, text.length()));
        return (boundary >= searchStart && boundary > 0) ? boundary : -1;
    }

    /**
     * 安全截断字符串至指定长度，不切断 Unicode 代理对（surrogate pair）。
     * <p>
     * 若截断点恰好位于高位代理（high surrogate）和低位代理（low surrogate）之间，
     * 则回退一个 char 位，避免产生孤立代理对导致下游 JSON/LLM 异常。
     * </p>
     *
     * @param input  待截断字符串
     * @param maxLen 最大字符长度
     * @return 截断后的字符串
     */
    String safeCodePointTruncate(String input, int maxLen) {
        if (input == null || input.length() <= maxLen) {
            return input;
        }
        // 检查截断点是否处于 surrogate pair 中间
        if (maxLen > 0 && Character.isHighSurrogate(input.charAt(maxLen - 1))) {
            return input.substring(0, maxLen - 1);
        }
        return input.substring(0, maxLen);
    }

    /**
     * 截断结果内部记录类。
     *
     * @param text     截断后的文本
     * @param boundary 截断边界类型
     */
    record TruncationResult(String text, TruncationBoundary boundary) {
    }
}
