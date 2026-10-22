package com.luutry.agent.security.filter;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * PII脱敏工厂
 * <p>
 * 用途：根据PiiType获取对应脱敏器，并提供自动扫描文本中所有PII批量脱敏的能力。
 * 处理问题：各模块需要针对不同字段类型调用不同脱敏规则，手动分发容易遗漏；
 * 同时对于自由文本（如LLM返回内容），需要自动识别并批量脱敏所有PII。
 * 处理方式：
 * 1. 内部注册所有内置脱敏策略（手机号/身份证/邮箱/姓名/银行卡）；
 * 2. getMasker(type)返回对应策略实现；
 * 3. maskText(text)遍历所有带正则的PII类型，逐一扫描替换文本中的匹配项。
 * </p>
 */
public final class PiiMaskerFactory {

    private static final Map<PiiType, PiiMasker> MASKERS = new EnumMap<>(PiiType.class);

    static {
        MASKERS.put(PiiType.PHONE, new PhoneMasker());
        MASKERS.put(PiiType.ID_CARD, new IdCardMasker());
        MASKERS.put(PiiType.EMAIL, new EmailMasker());
        MASKERS.put(PiiType.NAME, new NameMasker());
        MASKERS.put(PiiType.BANK_CARD, new BankCardMasker());
    }

    private PiiMaskerFactory() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 根据指定PII类型获取对应的脱敏器
     *
     * @param type PII类型
     * @return 对应的脱敏器实现
     */
    public static PiiMasker getMasker(PiiType type) {
        return MASKERS.get(type);
    }

    /**
     * 扫描文本中所有已知PII模式并进行脱敏。
     * 处理带正则模式的PII类型（手机号、身份证、邮箱、银行卡）。
     *
     * @param text 可能包含PII的输入文本
     * @return 所有检测到的PII均已脱敏的文本
     */
    public static String maskText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (PiiType type : PiiType.values()) {
            if (type.getPattern() == null) {
                continue;
            }
            PiiMasker masker = MASKERS.get(type);
            if (masker == null) {
                continue;
            }
            Matcher matcher = type.getPattern().matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String original = matcher.group();
                String masked = masker.mask(original);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    // ===== 内置脱敏器实现 =====

    /**
     * 手机号脱敏器：138****1234
     */
    static class PhoneMasker implements PiiMasker {
        @Override
        public String mask(String value) {
            if (value == null || value.length() < 7) {
                return value;
            }
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }

        @Override
        public PiiType getType() {
            return PiiType.PHONE;
        }
    }

    /**
     * 身份证号脱敏器：110***********1234
     */
    static class IdCardMasker implements PiiMasker {
        @Override
        public String mask(String value) {
            if (value == null || value.length() < 7) {
                return value;
            }
            int prefixLen = 3;
            int suffixLen = 4;
            int maskLen = value.length() - prefixLen - suffixLen;
            return value.substring(0, prefixLen) + "*".repeat(Math.max(0, maskLen)) + value.substring(value.length() - suffixLen);
        }

        @Override
        public PiiType getType() {
            return PiiType.ID_CARD;
        }
    }

    /**
     * 邮箱脱敏器：l***@gmail.com
     */
    static class EmailMasker implements PiiMasker {
        @Override
        public String mask(String value) {
            if (value == null || !value.contains("@")) {
                return value;
            }
            int atIndex = value.indexOf("@");
            if (atIndex <= 1) {
                return value;
            }
            return value.charAt(0) + "***" + value.substring(atIndex);
        }

        @Override
        public PiiType getType() {
            return PiiType.EMAIL;
        }
    }

    /**
     * 姓名脱敏器：张*（仅显示第一个字）
     */
    static class NameMasker implements PiiMasker {
        @Override
        public String mask(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            if (value.length() == 1) {
                return "*";
            }
            return value.charAt(0) + "*".repeat(value.length() - 1);
        }

        @Override
        public PiiType getType() {
            return PiiType.NAME;
        }
    }

    /**
     * 银行卡号脱敏器：6222***********1234
     */
    static class BankCardMasker implements PiiMasker {
        @Override
        public String mask(String value) {
            if (value == null || value.length() < 8) {
                return value;
            }
            int prefixLen = 4;
            int suffixLen = 4;
            int maskLen = value.length() - prefixLen - suffixLen;
            return value.substring(0, prefixLen) + "*".repeat(Math.max(0, maskLen)) + value.substring(value.length() - suffixLen);
        }

        @Override
        public PiiType getType() {
            return PiiType.BANK_CARD;
        }
    }
}
