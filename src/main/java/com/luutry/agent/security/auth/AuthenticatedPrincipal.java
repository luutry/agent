package com.luutry.agent.security.auth;

/**
 * SSO 令牌校验通过后得到的可信身份主体。
 * <p>
 * 只承载"令牌证明了你是谁"，不含权限；权限由 UserDirectory 按 userId 解析。
 * 与请求体传入的 userId 区分：本对象的 userId 来自已验签令牌，是唯一可信身份源。
 * </p>
 *
 * @param userId 令牌 sub 声明对应的用户唯一标识
 */
public record AuthenticatedPrincipal(String userId) {
}
