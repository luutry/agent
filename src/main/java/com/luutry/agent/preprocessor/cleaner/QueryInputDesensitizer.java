package com.luutry.agent.preprocessor.cleaner;

import com.luutry.agent.security.filter.PiiMaskerFactory;
import com.luutry.agent.security.filter.PiiType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询输入脱敏器。
 * <p>
 * 用途：在清洗管道中、截断之前，对用户输入进行 PII 敏感信息脱敏。
 * 处理问题：若先截断再脱敏，可能将手机号/身份证等截断为不完整的模式，
 * 导致正则匹配失败、敏感信息泄漏到下游 LLM 调用中。
 * </p>
 * <p>
 * 与 {@code PiiMaskerFactory.maskText} 的分工：本类额外处理"带分隔符"的号码
 * （138 1234 5678 / 138-1234-5678 / +86 138...），连续正则对它们全部漏检；
 * 正常连续号码仍交给工厂统一处理，避免两套脱敏规则漂移。
 * </p>
 */
@Slf4j
@Component
public class QueryInputDesensitizer {

    /**
     * 带分隔符的数字串：手机号常被写成 "138 1234 5678" / "138-1234-5678" / "+86 138..."，
     * 连续匹配的 PII 正则全部漏检，这里先按"数字个数"识别再整体替换。
     * 前后禁止字母数字邻接，避免把 "E1001" 或邮箱里的数字当成号码。
     */
    private static final Pattern SEPARATED_DIGITS = Pattern.compile(
            "(?<![0-9A-Za-z])(?:\\+?86[\\s\\-－]?)?[0-9](?:[\\s\\-－]?[0-9]){6,20}(?![0-9A-Za-z])");

    /** 提取纯数字（同时剥掉 +86 里的 '+' 等前缀符号） */
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    /**
     * 对查询文本执行 PII 脱敏。
     *
     * @param text 已完成控制字符移除、全角归一化和空白规范化的查询文本
     * @return 脱敏后的文本；若输入为 null 则返回 null
     */
    public String desensitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            String separated = maskSeparatedDigits(text);
            String masked = PiiMaskerFactory.maskText(separated);

            // 可观测：若脱敏前后文本不同，记录日志供审计（只记长度，不记内容）
            if (!text.equals(masked)) {
                log.info("查询输入检测到 PII 并已脱敏, 原始长度={}, 脱敏后长度={}",
                        text.length(), masked.length());
            }

            return masked;
        } catch (Throwable t) {
            // R3 优先于可用性：无法判定 PII 时按 fail-closed 抹除数字，绝不把原文交给下游
            log.warn("查询脱敏异常，按 fail-closed 抹除数字: {}", t.getMessage());
            return failClosedMask(text);
        }
    }

    /**
     * 掩掉带分隔符的号码；只处理"数字个数恰好符合手机号/身份证/银行卡"的串，
     * 日期（8 位）等普通数字保持原样。
     */
    private String maskSeparatedDigits(String text) {
        Matcher matcher = SEPARATED_DIGITS.matcher(text);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String raw = matcher.group();
            String digits = NON_DIGIT.matcher(raw).replaceAll("");
            String masked = maskByDigitCount(digits);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masked != null ? masked : raw));
        }
        if (!found) {
            return text;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按数字个数判定 PII 类型并脱敏；不像任何一类则返回 null（保持原样）。
     */
    private String maskByDigitCount(String digits) {
        if (digits.length() == 13 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.charAt(0) == '1'
                && digits.charAt(1) >= '3' && digits.charAt(1) <= '9') {
            return PiiMaskerFactory.getMasker(PiiType.PHONE).mask(digits);
        }
        if (digits.length() == 18) {
            return PiiMaskerFactory.getMasker(PiiType.ID_CARD).mask(digits);
        }
        if (digits.length() >= 16 && digits.length() <= 19) {
            return PiiMaskerFactory.getMasker(PiiType.BANK_CARD).mask(digits);
        }
        return null;
    }

    /**
     * 兜底脱敏：把所有半角/全角数字替换为 '*'。
     * <p>
     * 覆盖 R3 列举的可数字表征 PII（手机号/身份证/银行卡/工号），
     * 且只做字符遍历，不依赖正则引擎，不会二次抛异常。
     * </p>
     */
    static String failClosedMask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean digit = (c >= '0' && c <= '9') || (c >= '\uFF10' && c <= '\uFF19');
            sb.append(digit ? '*' : c);
        }
        return sb.toString();
    }
}
