# CONVENTIONS — 编码规范

> 写代码时读。与 AGENTS.md 的铁律冲突时，以 AGENTS.md 为准。

## 1. 语言与依赖

- JDK 21，Spring Boot 3.x，Maven。
- 使用 Java 21 特性：`record` 承载不可变数据、`sealed` 限定动作类型、
  `switch` 模式匹配、虚拟线程处理阻塞 IO（工具调用、HTTP）。
- 依赖版本统一走 `spring-boot-dependencies` BOM，禁止在子模块写版本号。
- **新增任何依赖必须先说明理由并等确认**；禁止 SNAPSHOT。
- 禁止引入：HanLP 全量包、PyTorch Java、任何需要本地编译的 native 库。

## 2. 包结构

```
com.luutry.agent
├── AgentApplication
├── common/          constant / exception / model / util
├── config/          @Configuration、@ConfigurationProperties
├── llm/             LlmClient、PromptTemplate、TokenCounter
├── repository/      entity / mapper（真实接入时）/ mock
├── external/        HrApiClient / mock
├── security/        UserContext、DataScopeResolver、PiiMasker、PromptInjectionFilter
├── observability/   TraceContext、Aspect、Metrics
├── tool/            registry / executor / builtin
├── memory/          short / summary / longterm
├── rag/             parser / chunker / retriever / rerank / vectorstore
├── preprocessor/    cleaner / resolver / rewriter / router
├── planner/         ReActState、Planner、Action
├── aggregate/       ContextAggregator
├── critique/        SelfCritique
└── web/             AgentController、请求响应模型
```

依赖方向：`web → planner → (tool | rag | memory) → repository/external`。
**禁止反向依赖**，`common` 不得依赖任何业务包。

## 3. 命名

| 类型 | 规则 | 示例 |
|---|---|---|
| 接口 | 领域名，不加 `I` 前缀 | `EmployeeRepository` |
| 实现 | 领域名 + 技术特征 | `MybatisEmployeeRepository`、`InMemoryVectorStore` |
| Mock | `Mock` 前缀 | `MockEmployeeRepository` |
| 请求 / 响应 | `XxxReq` / `XxxResp` | `ChatReq`、`ChatResp` |
| 内部命令 | `XxxCommand` / `XxxResult` | `ToolCommand`、`ToolResult` |
| 枚举 | 全大写下划线 | `QueryIntent.RAG`、`ActionType.WRITE` |
| 常量 | `UPPER_SNAKE`，集中在 `AgentConstants` | `MAX_TOOL_ROUNDS` |
| 布尔 | `isXxx` / `hasXxx` / `canXxx` | `isMasked` |
| 测试 | `XxxTest`，方法 `shouldXxx_whenYyy` | `shouldReject_whenDeptOutOfScope` |

禁止缩写除 `Id`、`Dto`、`Url` 之外的词；禁止拼音命名。

## 4. 注释与 JavaDoc

- 类级 JavaDoc **≤ 3 行**：这个类做什么、为什么存在。
- 方法级 JavaDoc 只在三种情况写：公共 API、复杂算法、有非显然副作用。
- **注释写「为什么」，不写「是什么」**。`// 设置 name` 这类一律删除。
- 禁止整段注释掉的代码，直接删。
- TODO 必须带文档路径：`// TODO(doc/TODO/003_Milvus向量库接入.md): 当前用内存实现`。

## 5. 常量与配置

- 所有限额、超时、路径、正则放 `AgentConstants` 或 `@ConfigurationProperties`。
- 配置项命名：`agent.<模块>.<键>`，例如 `agent.planner.max-rounds`。
- 敏感配置（数据库密码、API Key）从环境变量注入，**不写进 yml 明文**。
- 禁止在方法体里出现裸数字（`3`、`500`）和裸字符串（`"年假"` 除外，业务词入词表）。

## 6. 异常与降级

- 业务异常用 `BizException(ErrorCode)`，禁止用 `RuntimeException` 表达业务失败。
- 全局 `@RestControllerAdvice` 统一转换，对外只暴露错误码 + 安全文案，不暴露堆栈。
- **边界层**（预处理、检索、工具、记忆）最外层 `catch (Throwable)`：
  - 降级返回原始输入 / 空结果 / 保守默认值
  - 必须记 metric + 结构化日志，不允许静默吞掉
- 禁止 `e.printStackTrace()`、禁止 `catch (Exception e) {}` 空实现。
- 工具调用失败与「无数据」必须区分：前者可重试，后者直接回答「未查到」。

## 7. 日志与埋点

- SLF4J 参数化：`log.info("... {}", value)`，禁止字符串拼接。
- 必打节点：请求进入、意图结果、Planner 决策、工具出入参（脱敏后）、
  RAG 召回数量、降级、超限、异常。
- **禁止打印原始 query**：打 hash 或脱敏文本。
- 每个日志点带 `traceId`（MDC 注入）。
- 高频日志用 `debug`，`info` 只保留状态变化。

## 8. 测试

- JUnit 5 + AssertJ + Mockito。
- 每个 public 方法至少：1 个正向 + 1 个边界；每个 `throw` 至少 1 个异常用例。
- 只 mock 外部依赖，**禁止 mock 被测类自身**。
- 测试数据：小样本用 `@BeforeEach` 构造；大数据集放 `src/test/resources/mock/`。
- 禁止 `Thread.sleep` 等待异步，用同步返回或 `Awaitility`（如已引入）。
- 断言要给失败信息：`assertThat(actual).as("越权部门应返回空").isEmpty()`。
- 命名体现场景，不写 `test1`、`testMethod`。

## 9. Mock 规范

- Mock 类必须实现与真实实现**完全相同**的接口。
- Mock 数据从 `resources/mock/*.json` 加载，便于非开发人员修改。
- Mock 内部**不得包含业务分支**（如按角色返回不同结果），否则测试会失真。
- 装配用 `@Profile("mock")` 或 `@ConditionalOnProperty(prefix="agent", name="datasource", havingValue="mock")`。
- 真实实现就绪后，只新增实现类 + 改装配，**不改调用方**。

## 10. TODO 规范

- 一个待办一份文档：`doc/TODO/NNN_标题.md`，编号递增，不复用。
- 文档必含：背景、为什么暂缓、目标、验收标准、影响模块、前置依赖、风险。
- 代码处留 `// TODO(doc/TODO/NNN_标题.md): 原因`，否则视为未记录。
- 待办完成后把文档状态改为「已完成」，**不要删除**（保留决策痕迹）。

## 11. 提交前自检

- [ ] 依赖方向正确，没有反向依赖
- [ ] 没有裸数字 / 裸字符串常量
- [ ] 边界层有 Throwable 兜底且记了指标
- [ ] 日志无 PII，query 已脱敏或 hash
- [ ] 新增方法有正向 + 边界 + 异常测试
- [ ] TODO 已建文档
- [ ] 没有注释掉的死代码
