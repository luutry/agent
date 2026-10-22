package com.luutry.agent.security.filter;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * 敏感信息类型枚举
 * <p>
 * 用途：定义系统中需要脱敏的个人敏感信息（PII）类型，每种类型关联对应的正则模式和脱敏策略。
 * 处理问题：不同PII类型（手机号、身份证、邮箱等）有不同的脱敏规则，需要统一管理；
 * 同时提供正则模式用于自动扫描文本中的PII信息。
 * 处理方式：每个枚举值包含正则Pattern（用于在文本中定位PII）和描述信息；
 * PiiMaskerFactory根据PiiType选择对应的脱敏规则。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum PiiType {

    PHONE("phone", "手机号", Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)")),
    ID_CARD("id_card", "身份证号", Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)")),
    EMAIL("email", "邮箱", Pattern.compile("(?<![\\w@.])[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}(?![\\w@.])")),
    NAME("name", "姓名", null),
    BANK_CARD("bank_card", "银行卡号", Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)"));

    private final String code;
    private final String description;
    private final Pattern pattern;
}
