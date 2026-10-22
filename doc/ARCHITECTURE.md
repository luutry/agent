# ARCHITECTURE — 架构与接口契约

> 设计时读。模块边界发生变更时必须同步更新本文档。

## 1. 系统定位

企业 HR 管理者通过自然语言完成：人员检索、团队盘点、员工档案查阅、
人力指标看板、绩效流程查询、人事制度问答。

**只读**：不写任何人事业务数据。
**单一职责**：回答「是什么 / 有多少 / 怎么规定的」，不替管理者做决策。

### 非目标

- 不做流式输出（见 `doc/TODO/007_流式输出.md`）
- 不做导出（见 `doc/TODO/008_导出功能.md`）
- 不做审批、不做发起流程、不做任何写操作
- 不做多租户（单企业部署）
- 不接入 SSO 细节（由上游网关完成鉴权，本服务消费 `UserContext`）

## 2. 逻辑架构

```
                       ┌──────────────────────────┐
   POST /api/agent/chat│      AgentController      │
                       └────────────┬─────────────┘
                                    ▼
                       ┌──────────────────────────┐
                       │  SecurityFilter           │  UserContext / 注入检测
                       └────────────┬─────────────┘
                                    ▼
   ┌──────────────────────────────────────────────────────────┐
   │ Preprocessor: clean → resolve → rewrite → intent → split  │
   └────────────────────────────┬─────────────────────────────┘
                                ▼
   ┌──────────────────────────────────────────────────────────┐
   │ Planner (ReAct, ≤ 5 rounds)                               │
   │   ├─ ToolCenter  → Repository(Mock) / HrApiClient(Mock)   │
   │   ├─ RagPipeline → VectorStore(Mock，目标 Milvus)          │
   │   └─ Memory      → Redis / VectorStore                    │
   └────────────────────────────┬─────────────────────────────┘
                                ▼
   ┌──────────────────────────────────────────────────────────┐
   │ ContextAggregator  Tool > RAG > Memory，去重，token 裁剪   │
   └────────────────────────────┬─────────────────────────────┘
                                ▼
   ┌──────────────────────────────────────────────────────────┐
   │ SelfCritique  冲突检测 / 忠实度校验 / 信息不足则拒答        │
   └────────────────────────────┬─────────────────────────────┘
                                ▼
                          PiiMasker → Response

   旁路：Observability（trace / metrics / task log）贯穿全链路
```

## 3. 模块职责

| 模块 | 职责 | 不做什么 |
|---|---|---|
| preprocessor | 清洗、指代消解、改写、意图、复合拆分 | 不检索、不调工具 |
| planner | 决策下一步动作、子任务状态、轮次控制 | 不做数据加工 |
| tool | 工具注册、参数校验、权限校验、超时熔断、执行 | 不做自然语言生成 |
| rag | 文档解析、切片、混合检索、重排、引用 | 不做权限判定（依赖 security） |
| memory | 短期、摘要、长期记忆的读写与隔离 | 不做业务推理 |
| aggregate | 多源结果合并、优先级、去重、裁剪 | 不做忠实度校验 |
| critique | 冲突检测、引用校验、拒答决策 | 不重新检索 |
| security | 数据权限、功能权限、脱敏、注入防护 | 不做业务查询 |
| observability | trace、指标、任务日志 | 不参与业务分支 |

## 4. 三条主链路

### 4.1 制度问答（RAG）

```
query → 意图=RAG → RagPipeline.retrieve(topK=10)
      → Rerank → 取 top3 → ContextAggregator
      → SelfCritique 校验引用 → 输出（带 [RAG:文档#章节]）
```

### 4.2 人事数据查询（Tool）

```
query → 意图=TOOL → Planner 选择工具 → SlotExtractor 补参数
      → ToolExecutor：参数校验 → DataScope 注入 → 执行（超时 3s）
      → ToolResult（已脱敏）→ ContextAggregator
      → SelfCritique 校验数据完整性 → 输出（带 [Tool]）
```

### 4.3 混合（RAG + Tool）

```
query → 意图=MIXED → TaskDecomposer 拆子任务
      → Planner 逐个执行（可并行，虚拟线程）
      → 数据类结果优先，制度类结果作为解释
      → 若冲突：以 Tool 为准，并显式标注冲突
```

## 5. 核心接口契约

```java
// LLM
public interface LlmClient {
    LlmResponse chat(LlmRequest request);          // 同步，带超时与计费埋点
}

// 数据访问（全部按 DataScope 过滤）
public interface EmployeeRepository {
    List<Employee> search(EmployeeQuery query, DataScope scope);
    Optional<Employee> findByEmpNo(String empNo, DataScope scope);
}

// 数据权限
public interface DataScope {
    Set<String> deptIds();
    boolean contains(String deptId);
}

// 工具
public interface Tool {
    ToolDescriptor descriptor();                   // 名称/描述/JSON Schema/权限标签
    ToolResult execute(ToolCommand command, UserContext user);
}

// 向量
public interface VectorStore {
    void upsert(List<VectorRecord> records);
    List<VectorHit> search(float[] vector, int topK, Map<String, Object> filter);
}

public interface EmbeddingModel {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}
```

**契约约束**

1. 所有 Repository 方法必须带 `DataScope` 参数，否则编译期就应拒绝（可用自定义注解 + ArchUnit 校验）。
2. `ToolResult` 必须带 `source` 与 `masked` 标记，供聚合与反思使用。
3. `VectorStore.search` 的 `filter` 支持 `deptScope`、`effectiveAt`，用于权限与时效过滤。

## 6. 数据模型（概念级）

| 实体 | 关键字段 | 敏感级别 |
|---|---|---|
| `org_unit` | deptId, parentId, path, name, managerId | 低 |
| `employee` | empNo, name, deptId, jobLevel, jobTitle, employmentType, hireDate, status, education | 中（含 PII） |
| `performance_record` | empNo, cycle, grade, score, status | **最高**（禁入 Prompt） |
| `policy_document` | docId, title, version, effectiveFrom, effectiveTo, deptScope | 低 |
| `agent_task_log` | traceId, userId, intent, rounds, tokenCost, status | 中（不含 PII） |
| `memory` | sessionId, userId, type, content, vector, expireAt | 中（需脱敏） |

## 7. 权限模型

```
UserContext(userId, deptIds, roles)
        │
        ▼
DataScopeResolver
   ├─ 组织树向下展开：用户管辖部门 + 所有子部门
   └─ 汇报关系：直接/间接下属所在部门
        │
        ▼
DataScope(deptIds)
        │
        ├─→ Repository：WHERE dept_id IN (:deptIds)   ← 前置过滤
        └─→ ToolExecutor：二次校验入参部门是否在 scope 内
```

**两条硬约束**

1. 禁止「先查全量再过滤」：`DataScope` 必须进入 SQL 条件。
2. 工具参数里的部门 ID 必须二次校验，防止模型构造越权参数。

## 8. Mock 策略

| 接口 | Mock 实现 | 数据来源 | 退场 |
|---|---|---|---|
| EmployeeRepository | MockEmployeeRepository | `resources/mock/employees.json` | 001 |
| OrgUnitRepository | MockOrgUnitRepository | `resources/mock/org-units.json` | 005 |
| PerformanceRepository | MockPerformanceRepository | `resources/mock/performance.json` | 001 |
| PolicyDocumentRepository | MockPolicyDocumentRepository | `resources/mock/policies.json` | 001 |
| HrApiClient | MockHrApiClient | 固定样例 | 002 |
| VectorStore | InMemoryVectorStore | 内存 Map + 余弦相似度 | 003 |
| EmbeddingModel | MockEmbeddingModel | 字符 hash → 固定维度向量 | 004 |
| LlmClient | MockLlmClient | 固定 JSON 响应（仅测试） | 不替换 |

## 9. 预算与限额

| 项 | 值 | 位置 |
|---|---|---|
| 工具调用最大轮次 | 5 | `AgentConstants.MAX_TOOL_ROUNDS` |
| 单次上下文 token | 8000 | `AgentConstants.MAX_CONTEXT_TOKENS` |
| query 最大字符 | 500 | `AgentConstants.MAX_QUERY_LENGTH` |
| 单工具超时 | 3s | `AgentConstants.TOOL_TIMEOUT_MS` |
| 整体超时 | 30s | `AgentConstants.REQUEST_TIMEOUT_MS` |
| RAG topK → rerank → 保留 | 10 → 3 | `AgentConstants.RAG_TOP_K` |

## 10. 部署形态

- 单个 Spring Boot 服务，无状态，多副本部署。
- MySQL 8：业务数据 + 任务日志 + 知识库元数据。
- Redis 7：短期记忆、缓存、限流。
- Milvus 2.x：文档向量 + 长期记忆向量（当前 Mock）。
- 会话状态全部外置，实例可随时重启。
