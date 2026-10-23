package com.luutry.agent.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luutry.agent.common.exception.ErrorCodes;
import com.luutry.agent.common.model.vo.ApiResponse;
import com.luutry.agent.security.auth.AuthenticatedPrincipal;
import com.luutry.agent.security.auth.AuthenticationException;
import com.luutry.agent.security.auth.TokenVerifier;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.security.auth.UserDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * SSO 认证过滤器：在请求入口用 Bearer 令牌建立可信身份，供数据权限前置过滤使用（R2）。
 * <p>
 * 用途：把身份认证从 Controller 上移到统一入口，令牌校验通过后才装配 UserContext。
 * 处理问题：userId 若由请求体明文传入即可被伪造，无法作为权限判定依据。
 * 处理方式：解析 Authorization Bearer 令牌 → TokenVerifier 校验取 userId →
 * UserDirectory 解析权限 → 写入 UserContextHolder；请求结束在 finally 中 clear，防线程复用泄露。
 * 仅拦截 /api/agent/** 且放行健康检查端点。
 * </p>
 */
@Slf4j
@Component
public class SsoAuthenticationFilter extends OncePerRequestFilter {

    /** 受保护路径前缀，只有 Agent 对话 API 需要认证。 */
    static final String PROTECTED_PREFIX = "/api/agent/";
    /** 健康检查端点无需认证，供前端探活。 */
    static final String HEALTH_PATH = "/api/agent/health";

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TokenVerifier tokenVerifier;
    private final UserDirectory userDirectory;

    public SsoAuthenticationFilter(TokenVerifier tokenVerifier, UserDirectory userDirectory) {
        this.tokenVerifier = tokenVerifier;
        this.userDirectory = userDirectory;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PROTECTED_PREFIX) || path.equals(HEALTH_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "缺少或非法的 Authorization Bearer 令牌");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        AuthenticatedPrincipal principal;
        try {
            principal = tokenVerifier.verify(token);
        } catch (AuthenticationException e) {
            // 不回显令牌内容，只给通用原因（R4）
            log.warn("SSO token verification failed: {}", e.getMessage());
            writeUnauthorized(response, "登录令牌无效或已过期，请重新登录");
            return;
        }

        UserContext userContext = userDirectory.resolve(principal.userId());
        UserContextHolder.set(userContext);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(ErrorCodes.AUTH_FAILED.getCode(), message);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
