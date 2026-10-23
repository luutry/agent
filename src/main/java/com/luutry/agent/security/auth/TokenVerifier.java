package com.luutry.agent.security.auth;

/**
 * SSO 访问令牌校验器。
 * <p>
 * 用途：把不透明的 Bearer 令牌校验并解析为可信身份主体，是身份认证的唯一入口。
 * 处理问题：HTTP 无状态，userId 若由请求体明文传入即可被任意伪造，无法作为权限判定依据（R2）。
 * 处理方式：真实实现对接 OIDC/OAuth2 授权服务器（本地验签 JWT 或调用 introspection），
 * 从 sub 声明取 userId；当前阶段用 MockTokenVerifier 隔离，替换真实实现时调用方不改动（R11）。
 * </p>
 */
public interface TokenVerifier {

    /**
     * 校验令牌并解析出身份主体。
     *
     * @param token 去除 "Bearer " 前缀后的原始令牌字符串
     * @return 校验通过的身份主体，userId 非空
     * @throws AuthenticationException 令牌缺失、非法、验签失败或缺少 sub 声明时抛出
     */
    AuthenticatedPrincipal verify(String token);
}
