package com.luutry.agent.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MockTokenVerifier 单元测试：覆盖合法 JWT、非法结构与缺失 sub 等边界。
 */
class MockTokenVerifierTest {

    private final MockTokenVerifier verifier = new MockTokenVerifier();

    /** 构造 header.payload.signature 结构的未验签 JWT，payload 为给定 JSON。 */
    private String jwt(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".mock-signature";
    }

    @Test
    @DisplayName("合法令牌解析出 sub 作为 userId")
    void verify_validToken_returnsSubject() {
        AuthenticatedPrincipal principal = verifier.verify(jwt("{\"sub\":\"manager_001\",\"iss\":\"idp\"}"));
        assertEquals("manager_001", principal.userId());
    }

    @Test
    @DisplayName("payload 无填充也能正确解码")
    void verify_unpaddedPayload_returnsSubject() {
        // {"sub":"u1"} 编码后长度非 4 的倍数，验证补齐逻辑
        AuthenticatedPrincipal principal = verifier.verify(jwt("{\"sub\":\"u1\"}"));
        assertEquals("u1", principal.userId());
    }

    @Test
    @DisplayName("null 令牌抛认证异常")
    void verify_nullToken_throws() {
        assertThrows(AuthenticationException.class, () -> verifier.verify(null));
    }

    @Test
    @DisplayName("空白令牌抛认证异常")
    void verify_blankToken_throws() {
        assertThrows(AuthenticationException.class, () -> verifier.verify("   "));
    }

    @Test
    @DisplayName("非 JWT 结构抛认证异常")
    void verify_nonJwtStructure_throws() {
        assertThrows(AuthenticationException.class, () -> verifier.verify("plain-token-without-dots"));
    }

    @Test
    @DisplayName("缺少 sub 声明抛认证异常")
    void verify_missingSub_throws() {
        assertThrows(AuthenticationException.class, () -> verifier.verify(jwt("{\"iss\":\"idp\"}")));
    }

    @Test
    @DisplayName("sub 为空串抛认证异常")
    void verify_blankSub_throws() {
        assertThrows(AuthenticationException.class, () -> verifier.verify(jwt("{\"sub\":\"\"}")));
    }

    @Test
    @DisplayName("payload 非合法 Base64URL 抛认证异常")
    void verify_invalidBase64Payload_throws() {
        assertThrows(AuthenticationException.class, () -> verifier.verify("header.!!!not-base64!!!.sig"));
    }

    @Test
    @DisplayName("payload 非合法 JSON 抛认证异常")
    void verify_payloadNotJson_throws() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-json".getBytes(StandardCharsets.UTF_8));
        assertThrows(AuthenticationException.class, () -> verifier.verify("h." + payload + ".s"));
    }
}
