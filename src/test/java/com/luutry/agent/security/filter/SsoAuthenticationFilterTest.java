package com.luutry.agent.security.filter;

import com.luutry.agent.security.auth.AuthenticatedPrincipal;
import com.luutry.agent.security.auth.AuthenticationException;
import com.luutry.agent.security.auth.TokenVerifier;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.security.auth.UserDirectory;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SsoAuthenticationFilter 单元测试：覆盖缺令牌、令牌非法、令牌有效与健康检查放行。
 */
@ExtendWith(MockitoExtension.class)
class SsoAuthenticationFilterTest {

    @Mock
    private TokenVerifier tokenVerifier;

    @Mock
    private UserDirectory userDirectory;

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private SsoAuthenticationFilter filter() {
        return new SsoAuthenticationFilter(tokenVerifier, userDirectory);
    }

    private MockHttpServletRequest chatRequest() {
        return new MockHttpServletRequest("POST", "/api/agent/chat");
    }

    @Test
    @DisplayName("缺少 Authorization 头返回 401 且不进入后续链路")
    void missingHeader_returns401() throws Exception {
        MockHttpServletRequest req = chatRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest());
        verifyNoInteractions(tokenVerifier);
    }

    @Test
    @DisplayName("非 Bearer 前缀返回 401")
    void nonBearerScheme_returns401() throws Exception {
        MockHttpServletRequest req = chatRequest();
        req.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest());
        verifyNoInteractions(tokenVerifier);
    }

    @Test
    @DisplayName("令牌校验失败返回 401")
    void invalidToken_returns401() throws Exception {
        when(tokenVerifier.verify("bad-token")).thenThrow(new AuthenticationException("令牌格式非法"));
        MockHttpServletRequest req = chatRequest();
        req.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("令牌有效时装配 UserContext 并放行，请求结束后清理")
    void validToken_setsContextAndContinues() throws Exception {
        when(tokenVerifier.verify("good-token")).thenReturn(new AuthenticatedPrincipal("manager_001"));
        when(userDirectory.resolve("manager_001"))
                .thenReturn(UserContext.builder().userId("manager_001").build());

        MockHttpServletRequest req = chatRequest();
        req.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicReference<String> seenUserId = new AtomicReference<>();
        FilterChain chain = (r, s) -> {
            UserContext ctx = UserContextHolder.get();
            seenUserId.set(ctx != null ? ctx.getUserId() : null);
        };

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        assertEquals("manager_001", seenUserId.get());
        // 请求结束后必须清理，防止线程复用泄露
        assertNull(UserContextHolder.get());
    }

    @Test
    @DisplayName("健康检查端点跳过认证直接放行")
    void healthPath_isSkipped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/agent/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        assertNotNull(chain.getRequest());
        verifyNoInteractions(tokenVerifier);
    }
}
