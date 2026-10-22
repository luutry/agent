# 优化升级 #001：Pipeline Envelope 管道信封模式引入

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档编号 | OPT-20261020-001 |
| 优化类型 | 架构优化 / 数据流修复 |
| 影响范围 | Controller → PlannerService → ReActEngine → ContextAggregator → SelfCritique |
| 优先级 | P0（阻塞核心业务流程） |
| 日期 | 2026-10-20 |
| 关联Bug | BUG-20261020-003（ContextAggregator传空列表导致Self-Critique误判REJECT） |

---

## 一、问题描述

### 1.1 现象

用户发送"查看产品部团队人员结构"，ReAct引擎成功调用 `team_metrics` 工具并返回团队数据（总人数45、平均绩效3.6、离职率8.5%），但最终返回给用户的回答被替换为32字的拒绝话术：

> "抱歉，当前信息不足以回答您的问题，请提供更多细节或换个方式提问。"

### 1.2 运行时日志证据

```
rounds=2                        → ReAct执行2轮（CALL_TOOL → FINISH）
sources=[TOOL]                  → 数据来源为Tool（工具确实返回了数据）
verdict=REJECT                  → Self-Critique判定为拒绝
answer length=32                → 回答被替换为拒绝话术（32字符）
```

### 1.3 根因定位

**`AgentController.java` 第86-91行**：步骤4向 `ContextAggregator.aggregate()` 传入4个空列表：

```java
// 步骤4：从Agent响应步骤构建聚合上下文
AggregatedContext aggregatedContext = contextAggregator.aggregate(
        Collections.emptyList(),   // ← 空的 ToolResult 列表
        Collections.emptyList(),   // ← 空的 RetrievalResult 列表
        Collections.emptyList(),   // ← 空的 MemoryEntry 列表
        Collections.emptyList()    // ← 空的 ConversationTurn 列表
);
```

### 1.4 根因链路

```
ReActEngine 内部执行流程：
  ToolDispatcher.dispatch() → 获得 ToolResult（原始对象）
  formatToolResult(result)  → 格式化为 String（observation）
  ToolResult 原始对象在这里被丢弃，只保留 String

AgentResponse 携带的数据：
  answer (String)             → 格式化文本
  steps[].observation (String) → 格式化文本
  dataSources (枚举)           → 仅标签

ContextAggregator.aggregate() 需要的入参：
  List<ToolResult>            → 原始对象
  List<RetrievalResult>       → 原始对象
  List<MemoryEntry>           → 原始对象
  List<ConversationTurn>      → 原始对象

Controller 拿不到原始对象 → 只能传 emptyList()
```

### 1.5 影响

| 影响维度 | 具体表现 |
|---------|---------|
| 用户体验 | 工具成功返回数据但用户看到的是拒绝话术 |
| Self-Critique | 因缺少上下文，`detectHallucination()` 误判为幻觉 |
| ContextAggregator | 4步Pipeline（转换→排序→去重→裁剪）完全空转 |
| 数据完整性 | Tool/RAG/Memory原始数据在执行后被丢弃 |

---

## 二、方案对比与选型

### 2.1 候选方案

| 方案 | 描述 | 改动量 | 可观测性 | 扩展性 |
|------|------|--------|---------|--------|
| A: ReActStep增加原始数据字段 | 在ReActStep中增加`ToolResult rawToolResult`等字段 | 小 | 低 | 低 |
| B: AgentResponse增加收集列表 | 在AgentResponse.Builder中增加`addToolResult()`等方法 | 中 | 中 | 中 |
| **C: Pipeline Envelope 管道信封** | **创建独立信封对象贯穿整个Pipeline** | **中** | **高** | **高** |

### 2.2 方案A分析（排除）

**做法**：在 `ReActStep` 中增加 `ToolResult rawToolResult` 等字段。

**排除原因**：
- ReActStep 语义是"执行步骤记录"，不应承载数据传递职责
- 违反单一职责原则：Step既要记录执行轨迹，又要传递原始数据
- 无法携带 ConversationTurn（不属于任何Step）
- 无法记录Pipeline级别的执行元数据（各阶段耗时等）

### 2.3 方案B分析（排除）

**做法**：在 `AgentResponse.Builder` 中增加 `addToolResult()` 等方法。

**排除原因**：
- AgentResponse 是"最终响应"，在Planner完成后才构建
- 数据收集发生在ReActEngine执行期间，早于AgentResponse构建
- 无法携带Controller层需要的会话历史（ConversationTurn）
- 将数据收集职责耦合到响应模型中，违反关注点分离

### 2.4 方案C选定（Pipeline Envelope）

**选定原因**：

1. **数据流贯通**：信封在Pipeline入口创建，贯穿所有阶段，各阶段写入/读取各自关心的数据
2. **解耦**：各阶段通过信封通信，不需要知道上下游的具体实现
3. **可观测性**：信封自带traceId + stageRecords，直接支持全链路追踪
4. **扩展性**：新增Pipeline阶段（如缓存层、审计层），只需往信封加字段
5. **测试性**：单元测试可以构造信封、注入假数据，验证各阶段输入输出
6. **安全审计**：信封记录完整数据流，满足企业合规要求
7. **模式成熟**：Pipeline Envelope是集成框架（Spring Integration / Akka Streams）中的经典模式

---

## 三、实施方案详细设计

### 3.1 新增文件

| 文件 | 路径 | 行数 | 职责 |
|------|------|------|------|
| PipelineEnvelope | `core/context/model/PipelineEnvelope.java` | 288 | 管道信封主体，携带artifacts和stage记录 |
| EnvelopeStageRecord | `core/context/model/EnvelopeStageRecord.java` | 57 | 阶段执行记录（record类） |
| PipelineEnvelopeTest | `test/.../context/model/PipelineEnvelopeTest.java` | 273 | 信封单元测试（16个用例） |

### 3.2 修改文件

| 文件 | 改动内容 |
|------|---------|
| ReActEngine.java | +58/-13：新增带envelope参数的execute()重载，3个执行方法收集原始artifacts |
| PlannerService.java | +24/-2：新增带envelope参数的plan()重载，传递envelope给ReActEngine |
| AgentController.java | +43/-10：创建envelope，注入MemoryManager，从envelope提取数据给ContextAggregator |
| AgentControllerTest.java | +20/-4：适配新构造函数，更新mock匹配4参数plan() |

### 3.3 数据流（修复后）

```
Controller 创建 PipelineEnvelope(userId, sessionId)
       │
       ▼
QueryRouter → envelope.recordStage("preprocess", duration, success, null)
       │
       ▼
PlannerService.plan(query, userId, sessionId, envelope)
       │  envelope.recordStage("plan", duration, success, null)
       ▼
ReActEngine.execute(task, intent, userId, sessionId, envelope)
       │
       ├─ CALL_TOOL:
       │    ToolDispatcher.dispatch() → ToolResult
       │    envelope.addToolResult(result)        ← 收集原始对象
       │    formatToolResult(result) → observation
       │
       ├─ CALL_RAG:
       │    RagService.retrieve() → List<RetrievalResult>
       │    results.forEach(envelope::addRetrievalResult) ← 收集原始对象
       │    format → observation
       │
       └─ READ_MEMORY:
            MemoryManager.retrieveRelevant() → List<MemoryEntry>
            entries.forEach(envelope::addMemoryEntry) ← 收集原始对象
            format → observation
       │
       ▼
Controller 从信封提取数据：
  envelope.getToolResults()        → List<ToolResult>（非空）
  envelope.getRetrievalResults()   → List<RetrievalResult>（非空）
  envelope.getMemoryEntries()      → List<MemoryEntry>（非空）
  memoryManager.getSessionContext() → List<ConversationTurn>
       │
       ▼
ContextAggregator.aggregate(toolResults, ragResults, memoryEntries, turns)
  → 4步Pipeline正常执行（转换→排序→去重→裁剪）
  → 输出 AggregatedContext（包含结构化ContextSegment列表）
       │
       ▼
SelfCritiqueService.critique(response, aggregatedContext, query)
  → 拥有完整上下文，可准确校验
  → 不再因空上下文而误判REJECT
```

### 3.4 向后兼容

所有修改保持向后兼容：

```java
// ReActEngine：旧签名委托到新签名
public void execute(AgentTask task, QueryIntent intent, String userId, String sessionId) {
    execute(task, intent, userId, sessionId, null);  // envelope=null时不收集
}

// PlannerService：旧签名委托到新签名
public AgentResponse plan(ProcessedQuery pq, String userId, String sessionId) {
    return plan(pq, userId, sessionId, null);  // envelope=null时不收集
}
```

现有测试（PlannerServiceTest等）无需修改即可通过。

---

## 四、解决的问题

### 4.1 核心问题修复

| 问题 | 修复前 | 修复后 |
|------|--------|--------|
| ContextAggregator传空列表 | 4个emptyList()，空转 | 从envelope获取真实artifacts |
| Self-Critique误判REJECT | 无上下文→幻觉检测→误杀 | 有完整上下文→准确校验 |
| Tool原始数据丢失 | 格式化为String后丢弃 | 原始对象保留在envelope中 |
| 会话历史缺失 | 不获取ConversationTurn | 从MemoryManager获取并加入envelope |

### 4.2 附加价值

1. **全链路可观测**：每个Pipeline阶段的执行耗时、成功/失败状态记录在envelope中
2. **数据血缘**：每个artifact可追溯到产生它的阶段（哪个ReAct轮次、哪个Tool）
3. **traceId追踪**：每次请求有唯一traceId，支持跨阶段日志关联
4. **日志增强**：`envelope.toString()` 输出完整的artifact统计和耗时信息

---

## 五、方案局限性与潜在风险

### 5.1 线程安全

**现状**：PipelineEnvelope的artifact列表使用 `Collections.synchronizedList`，支持并发写入。

**潜在风险**：
- 如果未来ReActEngine引入并行Tool执行（CompletableFuture），需要确保envelope的线程安全粒度足够
- `getTotalArtifactCount()` 方法读取多个列表的size()，非原子操作，在并发写入时可能不一致

**缓解措施**：
- 当前ReActEngine是串行执行，无并发问题
- 如需并行，可改用 `CopyOnWriteArrayList` 或在收集阶段加锁

### 5.2 内存占用

**现状**：envelope同时持有原始对象和格式化文本（observation），内存占用增加。

**量化评估**：
- 单次请求典型数据：1-3个ToolResult + 0-5个RetrievalResult + 0-3个MemoryEntry
- 每个对象平均1-5KB，总计不超过50KB
- 请求完成后envelope即可GC回收

**结论**：当前规模下内存开销可忽略。若未来单次请求涉及大量文档检索（100+ chunks），需在envelope中增加容量限制。

### 5.3 生命周期管理

**现状**：envelope的生命周期由Controller管理（方法局部变量），请求结束即可回收。

**潜在风险**：
- 如果未来将envelope注入到异步任务（如@Async、消息队列），需确保生命周期正确传递
- 如果envelope被意外缓存或持有，可能导致内存泄漏

**缓解措施**：
- 在envelope的JavaDoc中明确标注"请求级生命周期，不可跨请求复用"
- 考虑增加 `close()` 方法或实现 `AutoCloseable`

### 5.4 接口稳定性

**现状**：PlannerService和ReActEngine各增加一个重载方法。

**潜在风险**：
- 方法数量增加，API表面扩大
- 如果未来需要传递更多上下文（如安全上下文、国际化信息），参数列表可能继续膨胀

**缓解措施**：
- 旧签名保留并委托到新签名，不破坏现有调用方
- 如果参数继续膨胀，考虑将 userId/sessionId 也移入envelope，统一为 `execute(AgentTask, PipelineEnvelope)`

### 5.5 测试覆盖

**现状**：新增16个PipelineEnvelope单元测试，更新AgentControllerTest。

**未覆盖场景**：
- 未编写集成测试验证"envelope从创建到ContextAggregator消费"的完整端到端流程
- 未验证envelope在异常路径下的行为（如ReActEngine抛异常时envelope状态）

**建议后续补充**：
- 集成测试：模拟完整请求，验证ContextAggregator收到非空数据
- 异常路径测试：验证Tool失败时envelope仍正确记录stage

### 5.6 多任务场景

**现状**：PlannerService可能分解出多个AgentTask，所有task共享同一个envelope。

**潜在风险**：
- 多个task的artifacts混在同一个列表中，无法区分来源task
- 如果两个task调用同一个Tool并返回不同结果，envelope中无法区分

**缓解措施**：
- 当前task数量受 `PLANNER_MAX_SUBTASKS=5` 限制，混合问题不严重
- 未来可在 `addToolResult()` 时增加 taskId 标签，支持按task过滤

---

## 六、验证结果

### 6.1 单元测试

```
Tests run: 451, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- 新增 PipelineEnvelopeTest: 16个用例全部通过
- 更新 AgentControllerTest: 7个用例全部通过（适配新构造函数）
- 原有 PlannerServiceTest: 8个用例全部通过（向后兼容）

### 6.2 预期运行时行为

修复后，同样的"查看产品部团队人员结构"请求：

```
rounds=2                        → ReAct执行2轮（不变）
sources=[TOOL]                  → 数据来源为Tool（不变）
envelope.toolResults=1          → 【新增】信封收集到1个ToolResult
verdict=PASS                    → 【修复】Self-Critique有上下文可校验，不再误判
answer length=200+              → 【修复】返回完整的团队数据回答
```

---

## 七、后续优化建议

1. **集成测试**：编写端到端集成测试，模拟HTTP请求验证完整数据流
2. **OpenTelemetry对接**：将envelope的stageRecords导出为OTel Span，实现可视化追踪
3. **envelope参数统一**：考虑将userId/sessionId移入envelope，简化方法签名
4. **容量保护**：在envelope中增加最大artifact数量限制，防止异常场景下内存膨胀
5. **序列化支持**：如需跨进程传递（如微服务间），envelope需实现Serializable
