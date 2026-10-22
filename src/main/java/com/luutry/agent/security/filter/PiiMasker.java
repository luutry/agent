package com.luutry.agent.security.filter;

/**
 * PII脱敏接口
 * <p>
 * 用途：定义敏感信息脱敏的标准契约，不同PII类型实现各自的脱敏规则。
 * 处理问题：各类敏感信息的脱敏规则不同（手机号中间4位*、身份证中间*等），
 * 需要统一接口以便工厂类按类型分发，也方便后续扩展新脱敏规则。
 * 处理方式：实现mask方法，输入原始值，输出脱敏后的字符串；
 * 对于过短或格式不合法的输入，返回原始值（不强制脱敏）。
 * </p>
 */
public interface PiiMasker {

    /**
     * 根据特定规则对敏感值进行脱敏处理
     *
     * @param value 原始敏感值
     * @return 部分字符被'*'替换后的脱敏值
     */
    String mask(String value);

    /**
     * 获取此脱敏器处理的PII类型
     *
     * @return PII类型
     */
    PiiType getType();
}
