package com.luutry.agent.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 会话票据的生成与格式校验。
 * <p>
 * 用途：会话标识由服务端签发，前端只负责回传。
 * 处理问题：客户端可自造 sessionId，若不做格式约束，带冒号的恶意值会污染 Redis key 空间。
 * 处理方式：统一前缀 + 32 位 UUID；校验仅允许字母、数字、下划线、连字符，长度 ≤ 64。
 * </p>
 */
public final class SessionIds {

    private static final String PREFIX = "sess_";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private SessionIds() {
    }

    /**
     * 生成新的会话标识。
     *
     * @return 形如 sess_&lt;32位十六进制&gt; 的标识
     */
    public static String newId() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验会话标识格式（拒绝空值、超长与非法字符）。
     *
     * @param sessionId 待校验的会话标识
     * @return 合法时返回 true
     */
    public static boolean isValid(String sessionId) {
        return sessionId != null && VALID.matcher(sessionId).matches();
    }
}
