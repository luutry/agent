# 【Bug记录】KeywordIntentClassifier 根本缺陷 — 关键词投票器非意图分类器，升级为 Embedding + 线性头
> 编号：007
> 发生时间：2026-10-21 10:00
> 模块归属：Query预处理 & 意图路由层
> 严重等级：P1
> 影响范围：所有意图分类场景，导致系统性误判（共享关键词污染、句式结构误分类）

## 1. 现象 & 复现步骤
【现象】
V2 `KeywordIntentClassifier` 存在根本性设计缺陷——**关键词投票器**而非意图分类器。
核心问题：把意图当成"哪类关键词出现得多"，但意图的本质是"答案依赖什么外部源"。

具体表现：
1. **共享关键词系统性误判**："绩效怎么算"（问制度→应 RAG）和"看看张三绩效"（要数据→应 TOOL）共享"绩效"关键词，分类结果取决于哪边关键词更多，而非语义。
2. **句式结构误分类**：V2 假设"怎么/如何→RAG"，但"怎么查看员工绩效"实际要的是数据（TOOL），句式与依赖源正交。
3. **大面积误杀**：任何同时包含 TOOL 和 RAG 关键词的查询（如"查看员工绩效制度"），分类结果不可预测。

【复现步骤】
1. 发送查询 "绩效怎么算"
2. 预期结果：RAG（问的是制度规则）
3. 实际结果：可能为 TOOL（因为"绩效"在 TOOL 列表，"怎么"在 RAG 列表，取决于哪边 hits 多）
4. 发送查询 "怎么查看员工绩效"
5. 预期结果：TOOL（需要查数据库）
6. 实际结果：可能为 RAG（因为"怎么"触发 RAG 模式分类）

## 2. 日志/报错信息
非异常类 Bug，而是逻辑缺陷。代码审查发现的根本问题：

```java
// V2 核心逻辑（问题代码）
int toolHits = countKeywordHits(query, toolKeywords);  // "绩效" → TOOL +1
int ragHits = countKeywordHits(query, ragKeywords);     // "怎么" → RAG +1
// 最终取决于 toolHits vs ragHits 大小，而非查询的真实语义依赖
```

根因公式：
- V2 假设：意图 = argmax(关键词命中数)
- 正确理解：意图 = 答案依赖什么外部源（Tool数据库 / RAG知识库 / 混合 / 闲聊）
- 句式（怎么/如何/看看）决定"问什么类型的问题"，不决定"答案在哪里"，两轴正交

## 3. 根因分析（Root Cause）
**根本原因：概念建模错误** — 将"意图分类"简化为"关键词计数比较"。

详细分析：
1. **意图的本质**：用户查询需要从哪里获取答案。TOOL = 人事数据库，RAG = 制度文档库，MIXED = 两者都需要，CHITCHAT = 不需要外部源。
2. **关键词投票的局限**：无法区分"语义相同但依赖不同"的查询。"绩效怎么算"（RAG）和"绩效是多少"（TOOL）在关键词层面高度重叠，但在 embedding 语义空间中天然可分。
3. **句式正交性**：怎么/如何/看看 是"问题类型轴"（方法/原因/事实），不是"依赖源轴"。两个轴正交，任何基于句式的模式分类都在回答错误的问题。

## 4. 修复方案
- 改造思路：从关键词投票升级为 **Embedding + 线性头** 架构
  - 查询文本 → Embedding(1024d) → W·x+b(4×1024) → softmax → [TOOL, RAG, MIXED, CHITCHAT] 概率分布
  - 三级降级：权重矩阵 → 种子余弦相似度 → 关键词兜底
  - Agent 铁律 #1：最外层 catch Throwable，失败降级到关键词分类器

- 修改文件清单：

| 文件 | 操作 | 改动说明 |
|------|------|---------|
| `EmbeddingService.java` | 新建 | Embedding 服务抽象接口，含 `float[] embed(String)` 和嵌套 `EmbeddingException` |
| `DashScopeEmbeddingService.java` | 新建 | 基于 langchain4j `EmbeddingModel` 的 DashScope 实现 |
| `IntentClassificationPolicy.java` | 重写 | V3 配置类：权重矩阵、偏置、种子数据、降级关键词、阈值 |
| `EmbeddingIntentClassifier.java` | 新建 | V3 核心分类器：线性头 + 种子相似度 + 三级降级 |
| `KeywordIntentClassifier.java` | 重写 | 移除 `@Component`，改为纯降级 Bean，构造函数参数化 |
| `LlmConfig.java` | 编辑 | 新增 EmbeddingModel、EmbeddingService、KeywordIntentClassifier、IntentClassifier 四个 Bean |
| `application.yml` | 编辑 | intent-classification 段改为 V3 配置，新增 embedding-model-name |
| `EmbeddingIntentClassifierTest.java` | 新建 | 13 个测试用例覆盖全路径 |
| `KeywordIntentClassifierTest.java` | 重写 | 适配新构造函数 |
| `QueryRouterTest.java` | 编辑 | 适配 EmbeddingIntentClassifier 构造 |

- 关键改动说明：
  1. `EmbeddingIntentClassifier.classify()` 最外层 catch Throwable，任何异常降级到 KeywordIntentClassifier
  2. Softmax 数值稳定性：减去最大值防止 exp() 溢出
  3. 种子数据懒初始化（`ensureSeedsEmbedded()`），首次调用时批量计算并缓存
  4. MIXED 判定：TOOL 和 RAG 概率均 ≥ mixedThreshold(0.3) 时触发

- 是否兼容原有逻辑：是；`KeywordIntentClassifier` 保留为降级 Bean，`IntentClassifier` 接口不变，`QueryRouter` 无感知切换

## 5. 风险评估
- 引入新Bug风险：中
  - Embedding 服务依赖外部 API，网络异常时降级到关键词（已覆盖）
  - Softmax 概率分布在类别数多时可能过于分散（已通过 confidenceThreshold 兜底）
- 回滚方案：移除 `EmbeddingIntentClassifier` Bean，恢复 `KeywordIntentClassifier` 的 `@Component` 注解即可回退到 V2
- 是否需要数据补偿：否；纯分类逻辑变更，无持久化数据影响

## 6. 测试验证用例
【正向验证】
- `classify_linearHead_toolWins`：权重矩阵路径，查询向量 [2,0,0,0] → TOOL 胜出（confidence > 0.6）
- `classify_linearHead_ragWins`：查询向量 [0,2,0,0] → RAG 胜出
- `classify_seedPath_toolQuery`：种子余弦相似度路径，完全匹配种子 → 正确分类
- `classify_seedPath_ragQuery`：RAG 种子完全匹配 → RAG 分类

【边界验证】
- `classify_nullInput_returnsChitchat`：null 输入 → CHITCHAT
- `classify_blankInput_returnsChitchat`：空白输入 → CHITCHAT
- `classify_linearHead_lowConfidence`：均匀分布 [0.1,0.1,0.1,0.1] → softmax = 0.25 → CHITCHAT（低置信度）
- `classify_seedPath_lowConfidence`：低相似度 → 低置信度 CHITCHAT
- `classify_seedPath_mixedDetection`：查询向量同时接近 TOOL 和 RAG 种子 → MIXED
- `classify_linearHead_withBias`：零向量 + 大偏置 → 偏置主导分类

【异常验证】
- `classify_embeddingThrows_fallbackToKeyword`：Embedding 抛异常 → 降级到关键词分类器
- `classify_embeddingReturnsNull_fallbackToKeyword`：Embedding 返回 null → 降级

【回归用例】
- `KeywordIntentClassifierTest` 保留原有降级关键词匹配测试
- `QueryRouterTest` 验证路由层正常消费 IntentResult

## 7. 后续优化项（可选）
1. **权重训练 Pipeline**：收集线上分类日志（查询 + Embedding + 人工标注），离线训练权重矩阵，通过配置热更新
2. **Embedding 超时控制**：当前 `embedWithTimeout` 简化为直接调用，生产环境需加虚拟线程超时
3. **温度参数**：softmax 引入温度系数 T，`softmax(logits/T)`，T<1 使分布更尖锐，T>1 更平滑
4. **在线学习**：增量更新权重矩阵，适应业务语义漂移
5. **可观测埋点**：记录每次分类的路径（LINEAR_HEAD/SEED_SIMILARITY/FALLBACK）、耗时、置信度分布

## 8. 修复人 & 备注
修复人：AI Agent
备注：V2→V3 是架构层面的根本转变，从"人工规则猜意图"到"模型从语义空间学意图"。关键词分类器保留为降级兜底，不删除。
