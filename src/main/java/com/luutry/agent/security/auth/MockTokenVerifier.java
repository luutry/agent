package com.luutry.agent.security.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * TokenVerifier 的 Mock 实现：解析 JWT 结构的 payload 段读取 sub，不做验签。
 * <p>
 * 用途：SSO 授权服务器尚未接入时提供可运行的身份解析，令牌结构与真实 OIDC JWT 保持一致，
 * 后续替换为真实验签实现时调用方无需改动（R11）。
 * 处理问题：真实实现需从授权服务器拉取 JWKS 并验证签名/时效/受众；当前阶段先隔离。
 * 处理方式：按 "." 拆分令牌，Base64URL 解码第二段（payload），读取 sub 声明作为 userId。
 * </p>
 * TODO(doc/TODO/019_真实OIDC令牌验签接入.md): 替换为 spring-security-oauth2-resource-server 本地验签
 */
@Component
@ConditionalOnProperty(name = "agent.sso.verifier", havingValue = "mock", matchIfMissing = true)
public class MockTokenVerifier implements TokenVerifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SUB_CLAIM = "sub";

    @Override
    public AuthenticatedPrincipal verify(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("令牌为空");
        }
        String[] segments = token.split("\\.");
        if (segments.length < 2) {
            throw new AuthenticationException("令牌格式非法，非 JWT 结构");
        }
        String payload;
        try {
            payload = new String(
                    Base64.getUrlDecoder().decode(padBase64Url(segments[1])),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 只记原因不记令牌内容，避免令牌进入日志（R4）
            throw new AuthenticationException("令牌 payload 非合法 Base64URL");
        }
        JsonNode sub;
        try {
            sub = OBJECT_MAPPER.readTree(payload).get(SUB_CLAIM);
        } catch (Exception e) {
            throw new AuthenticationException("令牌 payload 非合法 JSON");
        }
        if (sub == null || sub.asText().isBlank()) {
            throw new AuthenticationException("令牌缺少 sub 声明");
        }
        return new AuthenticatedPrincipal(sub.asText());
    }

    /** Base64URL 无填充，Java 解码器要求长度为 4 的倍数，这里补齐 '='。 */
    private String padBase64Url(String raw) {
        int remainder = raw.length() % 4;
        if (remainder == 0) {
            return raw;
        }
        return raw + "====".substring(remainder);
    }
}
