# 【Bug记录】hasSeeds() 检查种子列表非空但未校验 Embedding 是否已计算，导致种子不可用时误判 CHITCHAT

> 编号：008
> 发生时间：2026-10-21 19:00
> 模块归属：Query预处理 → 意图路由层 → EmbeddingIntentClassifier
> 严重等级：P1
> 影响范围：Embedding API 不可用时，所有查询被错误分类为 CHITCHAT（闲聊）

## 1. 现象 & 复现步骤

【现象】
用户输入 "帮我看看绩效制度"，期望返回 RAG（制度问答）或 MIXED，实际返回 CHITCHAT（闲聊）。

【复现步骤】
1. 启动应用，DashScope Embedding API 可用或不可用均可触发
2. 发送查询 "帮我看看绩效制度"
3. 观察返回意图为 CHITCHAT

【预期行为】
- Embedding API 正常时：种子向量计算成功 → 余弦相似度命中 RAG 种子 "绩效制度是什么" → 返回 RAG
- Embedding API 异常时：降级到 KeywordIntentClassifier → "绩效" 命中 TOOL，"制度" 命中 RAG → 至少返回 MIXED 或 RAG

## 2. 根因分析

### 2.1 缺陷一：`hasSeeds()` 检查条件错误

```java
// 旧代码（缺陷）
private boolean hasSeeds() {
    return !policy.getToolSeeds().isEmpty() || !policy.getRagSeeds().isEmpty()
            || !policy.getMixedSeeds().isEmpty() || !policy.getChitchatSeeds().isEmpty();
}
```

该方法检查的是**种子列表是否非空**（配置中有种子文本），而非**种子向量是否已计算**（embedding 字段非 null）。

当 DashScope API 失败时：
- `embedSeedList()` 逐个 catch 异常，种子 embedding 全为 null
- `hasSeeds()` 仍返回 true（列表有数据）
- 进入 `classifyBySeedSimilarity()` → `avgCosineSimilarity()` 遍历种子，全部 `seed.getEmbedding() == null` → `validCount = 0` → 返回 0.0
- 四个类别相似度全为 0.0 → `softmax([0,0,0,0])` = `[0.25, 0.25, 0.25, 0.25]`
- 最大概率 0.25 < `confidenceThreshold(0.5)` → 判定 CHITCHAT（低置信度）

### 2.2 缺陷二：`ensureSeedsEmbedded()` 失败不标记，导致雪崩

```java
// 旧代码（缺陷）
private synchronized void ensureSeedsEmbedded() {
    if (seedsEmbedded) return;
    try {
        // ... 计算种子 embedding
        seedsEmbedded = true;  // 只在成功时标记
    } catch (Throwable t) {
        log.warn("...");
        // 失败时 seedsEmbedded 仍为 false → 下次 classify 又会重试
    }
}
```

API 持续不可用时，每次 `classify()` 调用都会触发全量种子 Embedding 计算（16 个种子 × 每次 1 个 API 调用 = 16 次无效请求），形成雪崩效应。

### 2.3 调用链路图

```
classify("帮我看看绩效制度")
  ├─ embedWithTimeout() → DashScope API 失败 → 返回 null
  │   └─ [正常降级] → fallbackClassifier.classify() → 返回正确结果 ✅
  │
  └─ [但 API 返回了向量，种子初始化失败]
      ├─ ensureSeedsEmbedded() → 种子 API 全部失败 → catch → seedsEmbedded 仍 false
      ├─ hasWeights() → false
      ├─ hasSeeds() → true ← BUG：列表非空但 embedding 全 null
      ├─ classifyBySeedSimilarity()
      │   └─ avgCosineSimilarity × 4 → 全 0.0（validCount=0）
      │   └─ softmax([0,0,0,0]) = [0.25, 0.25, 0.25, 0.25]
      └─ buildResultFromProbs → max=0.25 < 0.5 → CHITCHAT ❌
```

## 3. 修复方案

### 3.1 修复 `hasSeeds()`：检查 embedding 是否真正可用

```java
// 新代码（修复后）
private boolean hasSeeds() {
    return hasEmbeddedSeed(policy.getToolSeeds())
            || hasEmbeddedSeed(policy.getRagSeeds())
            || hasEmbeddedSeed(policy.getMixedSeeds())
            || hasEmbeddedSeed(policy.getChitchatSeeds());
}

private boolean hasEmbeddedSeed(List<SeedSample> seeds) {
    if (seeds == null || seeds.isEmpty()) return false;
    for (SeedSample seed : seeds) {
        if (seed.getEmbedding() != null) return true;
    }
    return false;
}
```

### 3.2 修复 `ensureSeedsEmbedded()`：finally 块标记防雪崩

```java
private synchronized void ensureSeedsEmbedded() {
    if (seedsEmbedded) return;
    try {
        // ... 计算种子 embedding
    } catch (Throwable t) {
        log.warn("...");
    } finally {
        // 无论成功失败都标记完成，防止失败后反复重试导致雪崩
        seedsEmbedded = true;
    }
}
```

### 3.3 修复后降级路径

```
classify("帮我看看绩效制度")
  ├─ embedWithTimeout() → DashScope API 失败 → 返回 null
  │   └─ [降级] → fallbackClassifier.classify()
  │       → "绩效" 命中 TOOL(+1)，"制度" 命中 RAG(+1)
  │       → toolScore=1/3, ragScore=1/3 → 平局 → TOOL 胜出（先判定）
  │       → 或 MIXED（如果 toolHits≥2 && ragHits≥2）
  │
  └─ [API 正常但种子初始化失败]
      ├─ ensureSeedsEmbedded() → 失败 → finally: seedsEmbedded=true（不重试）
      ├─ hasWeights() → false
      ├─ hasSeeds() → false ← 修复：检查 embedding 非 null
      └─ [降级] → fallbackClassifier.classify() → 关键词匹配 ✅
```

## 4. 影响面评估

| 维度 | 评估 |
|------|------|
| 影响范围 | 所有经过 EmbeddingIntentClassifier 的查询（当 Embedding API 不可用时） |
| 触发条件 | DashScope Embedding API 部分失败（查询成功但种子初始化失败） |
| 严重程度 | P1 — 核心分类功能失效，所有查询返回 CHITCHAT |
| 回归风险 | 低 — 修复仅改变条件判断，不改变算法逻辑 |

## 5. 测试验证

- 527 个单元测试全部通过
- 修复后 `hasSeeds()` 在种子 embedding 全 null 时返回 false → 正确降级到关键词分类器
- 修复后 `ensureSeedsEmbedded()` 失败标记 `seedsEmbedded=true` → 不会反复重试

## 6. 教训总结

1. **状态检查必须验证最终态**：`hasSeeds()` 应检查"种子向量是否可用"而非"种子列表是否非空"。配置有数据 ≠ 运行时已计算。
2. **初始化失败必须标记完成**：`finally` 块中设置标志位，防止失败后雪崩重试。
3. **降级路径需要端到端测试**：单元测试覆盖了"API 完全失败"和"API 完全成功"，但没覆盖"查询 API 成功但种子 API 失败"的中间态。
