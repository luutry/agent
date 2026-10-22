# ROADMAP — 分阶段开发计划

> 总原则：自底向上，每阶段必须可独立运行 + 单测通过。
> 收到「阶段测试通过，继续下一阶段」才进入下一阶段。
> 当前所有外部数据源（MySQL / 外部 HR API / Milvus）均为 Mock，真实接入见 `doc/TODO/`。

## 一、阶段总表

| # | 阶段 | 核心目标 | 预估文件 | 依赖 |
|---|---|---|---|---|
| 1 | 基础设施 | 启动、配置、异常、常量、LLM Client、Repository 接口 | ~18 | 无 |
| 2 | 安全 & 权限 | 数据权限、功能权限、PII 脱敏、注入防护 | ~10 | 1 |
| 3 | 可观测 | Trace 上下文、埋点切面、指标、任务日志 | ~10 | 1 |
| 4 | Tool 工具中心 | 注册、调度、校验、5 个内置工具、超时重试熔断 | ~20 | 1,2,3 |
| 5 | Memory | 短期、摘要、长期、隔离 | ~12 | 1,3 |
| 6 | RAG | 解析、切片、混合检索、Rerank、引用、时效过滤 | ~15 | 1,3 |
| 7 | 预处理 & 路由 | 清洗、消解、改写、意图、复合拆分 | ~10 | 1 |
| 8 | Planner | ReAct 循环、子任务状态、轮次控制 | ~12 | 4,5,6,7 |
| 9 | 聚合 & 反思 | 上下文合并、冲突检测、忠实度校验 | ~10 | 4,5,6,8 |
| 10 | Controller & 集成 | 对话接口、端到端、链路验证 | ~8 | 全部 |

合计约 **125 个文件**。单阶段交付不得超过预估文件数 +20%，超出需先说明理由。

---

## 二、阶段详情

### Phase 1 基础设施

**交付**
- Spring Boot 3 启动类、`application.yml`、`application-mock.yml`
- 统一响应 `R<T>`、错误码枚举、`BizException`、全局异常处理
- `AgentConstants`：R7 全部限额、超时、预算
- `LlmClient` 接口 + `MockLlmClient` + `PromptTemplateLoader`
- Repository 接口：`EmployeeRepository`、`OrgUnitRepository`、`PerformanceRepository`、`PolicyDocumentRepository`
- Mock 实现 + fixture JSON（放在 `resources/mock/`）
- `HrApiClient` 接口 + `MockHrApiClient`

**验收**
- `mvn spring-boot:run -Dspring-boot.run.profiles=mock` 启动成功
- `/actuator/health` 返回 UP
- Mock 数据可读：按部门查员工返回 fixture 内容
- 每个 Mock 有单测

### Phase 2 安全 & 权限

**交付**
- `DataScopeResolver`：输入 `userId` + 资源类型，输出可访问部门 ID 集合
  （组织树向下展开 + 汇报关系并集）
- `PermissionChecker`：功能权限（查看 / 明细 / 导出位预留但不实现）
- `PiiMasker`：工号、手机号、身份证、薪资、绩效字段脱敏；支持按角色决定掩码粒度
- `PromptInjectionFilter`：识别指令注入模式，**打标不删改**
- 权限上下文 `UserContext`（userId / deptIds / roles），用 ThreadLocal + MDC

**验收**
- 越权查询返回空集，而不是报错或返回数据
- 脱敏单测覆盖每个字段、每种角色
- 绩效字段在任何脱敏模式下都不返回原文
- 注入样本被标记，query 仍能正常走后续流程

### Phase 3 可观测

**交付**
- `TraceContext`：traceId / sessionId / userId / 各阶段耗时
- `AgentTraceAspect`：在预处理、检索、工具、Planner 节点埋点
- Micrometer 指标：QPS、RT、工具轮次、token 消耗、降级次数
- `AgentTaskLog` 实体 + Repository（Mock 落盘），记录任务级摘要

**验收**
- 一次完整调用能在日志中串出全链路
- 指标可在 `/actuator/prometheus` 看到
- 日志中不含原始 PII（用正则断言测试）

### Phase 4 Tool 工具中心

**交付**
- `ToolDescriptor`：名称、描述、入参 JSON Schema、出参、权限标签
- `ToolRegistry`、`ToolExecutor`：参数校验 → 权限白名单 → 超时 → 只读重试 → 熔断
- 5 个内置工具：
  - `EmployeeSearchTool`：多条件检索
  - `TeamMetricsTool`：部门指标聚合
  - `EmployeeProfileTool`：档案与履历
  - `PerformanceQueryTool`：**只返回聚合值与等级，不返回明细原文**
  - `OrgTreeTool`：组织树查询
- 工具结果统一 `ToolResult`（含 `source`、`masked` 标记）

**验收**
- 每个工具正向 / 边界 / 异常用例齐全
- 超时、熔断、重试各有独立测试
- 越权调用被 `DataScope` 拦截
- 绩效工具返回结果中不含任何员工级绩效明细

### Phase 5 Memory

**交付**
- `ShortTermMemory`：Redis，保留最近 N 轮，TTL 30 分钟
- `SummaryMemory`：达到阈值触发压缩，**保留实体与结论**，丢弃寒暄
- `LongTermMemory`：向量 + TTL + 遗忘（访问频次衰减）
- `MemoryIsolation`：按 userId 隔离，跨用户不可读

**验收**
- 隔离测试：A 用户读不到 B 用户记忆
- 压缩后关键实体不丢失（用固定用例断言）
- TTL 到期后可读为空
- 向量检索走 `VectorStore` 接口（Mock 实现）

### Phase 6 RAG

**交付**
- `DocumentParser`：Markdown / Word / PDF → 带层级的纯文本
- `Chunker`：按条款 / 标题切分，chunk 保留 `章节路径`
- `HybridRetriever`：BM25 + 稠密向量，RRF 融合
- `Reranker`：先实现规则版（关键词覆盖 + 位置），预留模型接口
- 去重、token 预算、引用生成、`effectiveFrom/To` 时效过滤
- `VectorStore` 接口 + `InMemoryVectorStore`

**验收**
- 引用可回溯到原文片段（文档名 + 章节 + 字符区间）
- 过期文档被过滤（构造生效期外文档验证）
- 切片不切断条款（构造多级标题文档验证）
- 混合检索在样例集上召回优于单路

### Phase 7 预处理 & 路由

**交付**
- `QueryCleaner`：NFKC、控制字符、零宽字符、空白折叠、按 code point 截断
- `CoreferenceResolver`：规则 + 会话状态，保守消解，不确定不改写
- `QueryRewriter`：术语标准化（受词表约束）+ 扩展通道（不拼回原句）
- `IntentClassifier`：规则优先，输出 `RAG / TOOL / MIXED / CHITCHAT / OUT_OF_DOMAIN`
- `TaskDecomposer`：保守触发（分别 / 各自 / 多个独立问句），补全共享谓语

**验收**
- `我年假还有几天` 不被误判为复合
- `年假、病假分别几天` 拆成两个完整子问题
- 意图分类输入为消解后原句，不含扩展词（断言）
- 截断不产生孤立代理字符

### Phase 8 Planner

**交付**
- `ReActState`：子任务列表、已完成步骤、预算消耗
- `Planner`：循环决策 `TOOL / RAG / MEMORY / ANSWER / CLARIFY`
- 终止条件：答案充分 / 预算耗尽 / 连续两次相同动作 / 无法推进
- 最大轮次取自 `AgentConstants`

**验收**
- 超过 5 轮被拦截并给出可读原因
- 相同工具 + 相同参数连续两次 → 终止
- 子任务状态可查询、可中断、可恢复
- 规划过程可回放（trace 中有每步决策）

### Phase 9 聚合 & 反思

**交付**
- `ContextAggregator`：合并 Tool / RAG / Memory，按可信度排序
  （Tool > RAG > Memory），去重，按 token 预算裁剪
- `SelfCritique`：多源冲突检测、忠实度校验（每个断言要有来源）、
  信息不足时拒答并说明缺什么

**验收**
- Tool 与 RAG 冲突时以 Tool 为准，并在答案中标注冲突
- 无来源的断言被剔除
- 超出 token 预算时按优先级裁剪，不截断引用信息

### Phase 10 Controller & 集成

**交付**
- `AgentController`：`POST /api/agent/chat`（同步，不做流式）
- 完整链路：鉴权 → 权限 → 预处理 → Planner → 聚合 → 反思 → 脱敏 → 返回
- 端到端测试 20 条（覆盖 RAG / Tool / Mixed / 域外 / 越权 / 预算超限）

**验收**
- 20 条端到端用例全绿
- 越权、脱敏、轮次上限、引用完整性各有断言
- 单次请求 P95 < 5s（Mock 数据下）

---

## 三、阶段门

每个阶段必须同时满足才算通过：

1. `mvn -q test` 全绿
2. 正向 / 边界 / 异常三类用例齐全
3. 当前阶段可在 `mock` profile 下独立启动
4. 输出变更清单 + 未决问题 + 新增 TODO
5. 用户明确回复「阶段测试通过，继续下一阶段」

---

## 四、Mock 退场计划

| Mock | 替换为 | 替换时机 | TODO |
|---|---|---|---|
| `MockEmployeeRepository` 等 | MyBatis + Hikari 真实实现 | Phase 4 稳定后 | 001 |
| `MockHrApiClient` | 真实 HTTP 客户端 + 重试 | Phase 4 稳定后 | 002 |
| `InMemoryVectorStore` | Milvus 实现 | Phase 6 稳定后 | 003 |
| `MockEmbeddingModel` | 真实 Embedding 服务 | Phase 6 稳定后 | 004 |
| `MockOrgDataProvider` | 真实组织树与汇报关系 | Phase 2 稳定后 | 005 |
| `MockLlmClient` | 不替换，仅测试用 | — | — |

**退场原则**：替换只新增实现类 + 改装配，不改任何调用方代码。若发现需要改调用方，
说明接口设计有问题，先停下讨论。

---

## 五、全局 Definition of Done

- 所有外部依赖可替换（接口 + 装配），业务层无 mock 分支
- 权限过滤在查询层，有越权测试
- 返回值 / 日志 / 异常 / Prompt 四处无未脱敏 PII
- 绩效数据未进入任何 Prompt
- 所有限额来自 `AgentConstants`，无散落魔数
- 每个模块有独立的单测，可在不启动全链路时验证
- 未完成项已写入 `doc/TODO/` 并在代码处留 TODO 注释
