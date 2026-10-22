# Pipeline Envelope（管道信封）原理通俗讲解

> 如果你不熟悉这个设计模式，读完本文就能理解它做了什么、为什么这样做、以及我们项目里是怎么用的。

---

## 一、先讲一个生活中的例子

### 快递信封

想象你在公司里要走一个审批流程：

```
你（发起）→ 部门经理审批 → 财务审批 → 总经理审批 → 行政执行
```

**问题来了**：总经理审批的时候，他怎么知道你当初填的申请表内容？

**方案 A（最差）**：每个环节的人自己拿一张白纸记笔记，下一个人来了再口述给他。
→ 口述过程中信息丢失、变形、遗漏。

**方案 B（一般）**：每个人处理完后，把结果写回你的原始申请表，传给下一个人。
→ 申请表越来越挤，格式混乱，而且有些人往上面贴便利贴，有些人直接改原文。

**方案 C（最好）**：你拿出一个**大信封**，把你的申请表放进去。每经过一个人，他把审批结果也放进这个信封。下一个人打开信封，能看到前面所有人的材料。

```
┌─────────────────── 信封 ───────────────────┐
│                                             │
│  [你的申请表]                                │
│  [部门经理的审批意见]                         │
│  [财务的审批意见]                            │
│  [总经理的审批意见]                           │
│                                             │
└─────────────────────────────────────────────┘
```

**这个"大信封"就是 Pipeline Envelope。**

---

## 二、我们的项目里，"信封"解决了什么问题？

### 2.1 先看修复前的"灾难现场"

我们的 Agent 有一条流水线，处理用户提问：

```
用户提问 → QueryRouter → PlannerService → ReActEngine → ContextAggregator → SelfCritique → 返回回答
```

其中 `ReActEngine` 会调用工具（比如查团队人数），拿到**原始数据对象** `ToolResult`：

```java
// ReActEngine 内部：
ToolResult result = toolDispatcher.dispatch(request);
// result 里面有：{totalHeadcount=45, avgPerformance=3.6, turnoverRate=8.5%}

// 但是！它立刻把原始对象格式化成了文字：
String observation = formatToolResult(result);
// observation = "totalHeadcount: 45\navgPerformance: 3.6\nturnoverRate: 8.5%"

// 原始 ToolResult 对象？丢了！没人保存它！
```

然后到了 `ContextAggregator`（上下文聚合器），它需要原始对象来做排序、去重、裁剪：

```java
// ContextAggregator 的接口签名：
AggregatedContext aggregate(
    List<ToolResult> toolResults,       // ← 我需要原始对象！
    List<RetrievalResult> ragResults,    // ← 我也需要！
    List<MemoryEntry> memoryEntries,     // ← 我也要！
    List<ConversationTurn> recentTurns   // ← 我也要！
);
```

**Controller 拿到的是 AgentResponse，里面只有文字，没有原始对象。**

怎么办？当时的代码：

```java
// AgentController.java（修复前）—— 硬着头皮传空列表
AggregatedContext aggregatedContext = contextAggregator.aggregate(
    Collections.emptyList(),   // ← 空的！
    Collections.emptyList(),   // ← 空的！
    Collections.emptyList(),   // ← 空的！
    Collections.emptyList()    // ← 空的！
);
```

**结果**：ContextAggregator 收到空数据 → 输出空上下文 → SelfCritique 发现"回答里有具体数字但上下文是空的" → 判定为"幻觉" → 把正常回答替换成拒绝话术。

**用户看到的就是**：工具明明查到了数据，但系统说"信息不足"。

### 2.2 问题的本质

用一张图说清楚：

```
数据流断裂示意：

ReActEngine                    AgentResponse               Controller
┌─────────────┐               ┌──────────────┐            ┌──────────────┐
│ 拿到 ToolResult│──格式化──→ │ answer: 文字   │──传递──→  │ 只有文字      │
│ 原始对象 ✅   │              │ steps: 文字   │           │ 没有原始对象 ❌│
│ 丢弃原始对象 ❌│              │ sources: 标签 │           │              │
└─────────────┘               └──────────────┘            └──────┬───────┘
                                                                  │
                                                                  ▼
                                                           需要传给 ContextAggregator
                                                           但拿不出原始对象
                                                           只能传 emptyList() 😭
```

---

## 三、信封是怎么解决的

### 3.1 核心思路：让数据"坐信封"走完全程

```
Controller 创建一个信封
       │
       ▼  ┌─────────────────────── 信封 ───────────────────────┐
       │  │  toolResults:      []  （空的，等着被填充）          │
       │  │  retrievalResults: []                               │
       │  │  memoryEntries:    []                               │
       │  │  conversationTurns:[]                               │
       │  │  stageRecords:     []  （记录每个阶段的执行信息）     │
       │  └────────────────────────────────────────────────────┘
       │
       ▼
QueryRouter → 处理完，往信封里记一条：stageRecord("preprocess", 5ms, 成功)
       │
       ▼
PlannerService → 把信封带着，传给 ReActEngine
       │
       ▼
ReActEngine → 调用工具，拿到 ToolResult
       │        ① 把原始 ToolResult 放进信封：envelope.addToolResult(result)
       │        ② 格式化文字留给 observation（和以前一样）
       │
       ▼
回到 Controller → 打开信封：envelope.getToolResults()
       │           里面有真实的 ToolResult 对象！✅
       │
       ▼
ContextAggregator.aggregate(
    envelope.getToolResults(),       // ← 有数据了！
    envelope.getRetrievalResults(),  // ← 有数据了！
    envelope.getMemoryEntries(),     // ← 有数据了！
    envelope.getConversationTurns()  // ← 有数据了！
)
       │
       ▼
SelfCritique → 收到完整上下文 → 准确校验 → 不再误判 ✅
```

### 3.2 对应到真实代码

**修复后的 AgentController.java**：

```java
// 1. 创建信封
PipelineEnvelope envelope = PipelineEnvelope.builder()
        .userId(request.getUserId())
        .sessionId(request.getSessionId())
        .build();

// 2. 把信封传给 PlannerService（它会传给 ReActEngine）
AgentResponse agentResponse = plannerService.plan(
        processedQuery, request.getUserId(), request.getSessionId(),
        envelope    // ← 信封在这里传入
);

// 3. ReActEngine 执行过程中，自动往信封里放数据：
//    - 调用工具 → envelope.addToolResult(toolResult)
//    - 调用RAG  → envelope.addRetrievalResult(ragResult)
//    - 调用记忆 → envelope.addMemoryEntry(memoryEntry)

// 4. 从信封里取出数据，给 ContextAggregator
AggregatedContext aggregatedContext = contextAggregator.aggregate(
        envelope.getToolResults(),          // ← 不再是 emptyList()！
        envelope.getRetrievalResults(),     // ← 不再是 emptyList()！
        envelope.getMemoryEntries(),        // ← 不再是 emptyList()！
        envelope.getConversationTurns()     // ← 不再是 emptyList()！
);
```

**修复后的 ReActEngine.java**（关键改动）：

```java
// 调用工具时：
private String executeToolCall(String toolName, PipelineEnvelope envelope) {
    ToolResult result = toolDispatcher.dispatch(request);

    // ★ 关键：把原始对象放进信封！
    if (envelope != null) {
        envelope.addToolResult(result);
    }

    // 格式化文字照常返回（给 ReActStep 的 observation 用）
    return formatToolResult(result);
}
```

---

## 四、信封还额外做了什么好事

信封不只是搬运数据，它还自带"行程记录"：

```java
// 每个阶段执行完，往信封里记一条：
envelope.recordStage("preprocess", 5, true, null);     // 预处理：5ms，成功
envelope.recordStage("plan", 160, true, null);          // 规划：160ms，成功
envelope.recordStage("react", 150, true, null);         // ReAct执行：150ms，成功
envelope.recordStage("aggregate", 2, true, null);       // 聚合：2ms，成功
envelope.recordStage("critique", 10, true, null);       // 校验：10ms，成功
```

最后打印日志时：

```
Chat response sent: verdict=PASS, answer length=256,
  envelope=PipelineEnvelope{
    traceId='a1b2c3d4-...',
    userId='manager1',
    sessionId='session-abc',
    toolResults=1,          ← 收集了1个工具结果
    retrievalResults=0,
    memoryEntries=0,
    conversationTurns=2,    ← 收集了2轮对话
    stageRecords=5,         ← 记录了5个阶段
    elapsedMs=327           ← 总耗时327ms
  }
```

**一眼就能看出**：这次请求调了什么工具、收了多少数据、每个阶段花了多久。

---

## 五、为什么不用更"简单"的方案？

### 5.1 为什么不让 ReActEngine 直接返回原始对象？

你可能会想：让 ReActEngine 把 ToolResult 放到 AgentResponse 里不就行了？

**问题**：
- AgentResponse 是"最终答卷"，在 Planner 完成后才构建
- 数据收集发生在 ReAct 执行期间，比 AgentResponse 构建更早
- AgentResponse 还要传给前端，里面塞太多内部对象不合适
- 信封是"内部物流单"，AgentResponse 是"给客户的包裹"，职责不同

### 5.2 为什么不在 ReActStep 里加字段？

**问题**：
- ReActStep 是"执行步骤记录"，语义是"第N轮做了什么"
- 让它同时承担"数据传递"职责，一个类管两件事，不干净
- ConversationTurn 不属于任何 Step，没法放进去

### 5.3 信封的优势总结

| 对比维度 | 直接塞 AgentResponse | 塞 ReActStep | Pipeline Envelope |
|---------|---------------------|-------------|-------------------|
| 数据收集时机 | 晚于收集时机 ❌ | 可以 ✅ | 可以 ✅ |
| 职责单一 | 一般 ⚠️ | 违反 ❌ | 好 ✅ |
| 阶段耗时记录 | 不支持 ❌ | 不支持 ❌ | 原生支持 ✅ |
| 会话历史 | 不适合 ❌ | 放不下 ❌ | 可以 ✅ |
| 扩展新阶段 | 改 AgentResponse | 改 ReActStep | 加字段即可 ✅ |
| 可观测性 | 低 | 低 | 高 ✅ |

---

## 六、一句话总结

**Pipeline Envelope 就是一个"快递信封"，在流水线起点创建，每经过一个环节，环节把产生的数据放进去，下一个环节可以从信封里取前面所有环节的数据。**

它解决的核心问题：**流水线上下游之间的数据传递断层** —— 上游生产了数据，下游需要消费，但中间没有通道。

在我们项目中的具体作用：让 ReActEngine 执行工具时产生的原始数据（ToolResult、RetrievalResult、MemoryEntry），能完好无损地传递到 ContextAggregator 和 SelfCritique，不再因为"数据丢失"导致误判。
