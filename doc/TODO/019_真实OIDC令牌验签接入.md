# TODO-019 真实 OIDC 令牌验签接入

| 字段 | 内容 |
|---|---|
| 编号 | 019 |
| 状态 | 待开始 |
| 优先级 | P0 |
| 关联 | 优化升级 20261023_006（单点登录 SSO 身份认证接入）；与 TODO-010（数据权限真实模型）同为"真实数据源接入"批次 |
| 触发来源 | sessionId/userId 客户端可伪造，需以 SSO 令牌作为唯一可信身份源（R2） |

## 背景

20261023_006 已把身份认证上移到 `SsoAuthenticationFilter`，并定义 `TokenVerifier` 接口隔离令牌校验。
当前实现是 `MockTokenVerifier`：只按 JWT 结构 Base64URL 解码 payload 读取 `sub`，**不验签、不校验时效/受众/签发者**。
它能让链路在 SSO 授权服务器尚未接入时跑通，但绝不能用于生产——任何人都能自造一个 `{"sub":"manager_001"}` 的令牌冒充管理员。

## 残留待办项

1. **接入真实 OIDC/OAuth2 授权服务器**：新增 `OidcJwtTokenVerifier implements TokenVerifier`，
   用 `spring-boot-starter-oauth2-resource-server`（或 Nimbus JOSE）从 IdP 的 JWKS 端点拉取公钥本地验签，
   校验 `iss`/`aud`/`exp`/`nbf`，通过后从 `sub` 取 userId。
2. **装配切换**：真实实现用 `@ConditionalOnProperty(name="agent.sso.verifier", havingValue="oidc")`，
   Mock 用 `havingValue="mock"`（当前 `matchIfMissing=true`）；两者互斥，业务代码不改（R11）。
3. **令牌获取链路**：前端从 IdP 登录（授权码 + PKCE）拿到 access_token，替换当前 `buildMockToken` 自造令牌逻辑。
4. **登出/令牌吊销**：如需即时失效，评估 introspection 或短时效 + refresh 策略。
5. **密钥与配置**：JWKS URI、issuer、audience 等进 `application.yml`（属敏感配置，改动需明确指令，R10）。

## 代码锚点

- `src/main/java/com/luutry/agent/security/auth/TokenVerifier.java`：令牌校验契约（替换点）
- `src/main/java/com/luutry/agent/security/auth/MockTokenVerifier.java`：`TODO(doc/TODO/019_...)` 当前不验签的 Mock
- `src/main/java/com/luutry/agent/security/filter/SsoAuthenticationFilter.java`：调用方，替换实现后不改动
- `src/main/resources/static/index.html`：`buildMockToken` 前端自造令牌，待替换为 IdP 登录

## 验收标准

- [ ] 篡改签名/过期/错误受众的令牌一律 401，无法通过伪造 payload 冒充他人
- [ ] `agent.sso.verifier=oidc` 时装配真实验签器，`=mock` 或缺省时装配 Mock，二者不共存
- [ ] `SsoAuthenticationFilter` 与 `AgentController` 代码不随实现替换而改动（R11）
- [ ] 公钥/issuer 等敏感配置不进日志（R4）
