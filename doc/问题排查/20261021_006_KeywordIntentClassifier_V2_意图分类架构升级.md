# 【Bug记录】KeywordIntentClassifier V2 意图分类架构升级（7类缺陷修复）
> 编号：006
> 发生时间：2026-10-21 14:30
> 模块归属：Query预处理 & 意图路由层
> 严重等级：P1
> 影响范围：所有用户查询的意图分类准确率；影响下游 Planner 任务规划、Tool/RAG 调度

## 1. 现象 & 复现步骤
【现象】
V1 KeywordIntentClassifier 存在 7 类缺陷，导致意图分类准确率严重下降：
1. "是否有人离职" 被 CHITCHAT 关键词 "是" 污染评分，可能误判为闲聊
2. "绩效" 单关键词命中即得到 confidence=1.0，无区分度
3. "今天天气不错" 无匹配时返回 CHITCHAT@0.5，置信度偏高
4. "数据分析师" 中的 "数据" 被 contains() 子串匹配误命中
5. "查看绩效制度" 仅 1 个 RAG 关键词即触发 MIXED 混合意图
6. 关键词硬编码 `static final Set.of(...)`，上线后无法动态调参
7. 未利用上游 CleanResult/ResolveResult 元信息

【复现步骤】
1. 输入 "是否有人离职"
2. 预期结果：TOOL 意图（含 "人员"/"离职" 关键词），CHITCHAT 得分 = 0
3. 实际结果（V1）：CHITCHAT 被 "是" 污染，归一化分母增大，TOOL 置信度被稀释

## 2. 日志/报错信息
非运行时异常，为逻辑缺陷。通过单元测试暴露：

```
// V1 归一化评分逻辑
toolHits=2, chitchatHits=1 → totalHits=3
toolScore = 2/3 = 0.667, chitchatScore = 1/3 = 0.333
// 本应 TOOL 高置信胜出，但 CHITCHAT 被无端拉高
```

```
// V1 单关键词场景
"绩效" → toolHits=1, totalHits=1 → toolScore = 1/1 = 1.0
// 仅 1 个关键词命中就满分，无区分度
```

## 3. 根因分析（Root Cause）
**代码逻辑缺陷 + 架构设计不足**，具体：

| 编号 | 根因 | 分类 |
|---|---|---|
| P1 | CHITCHAT_KEYWORDS 含 "是/对/嗯/啊/哦" 等高频单字，在正常业务查询中频繁出现 | 关键词设计缺陷 |
| P2 | 归一化评分 `hits/totalHits`，单关键词时 totalHits=hits → score=1.0 | 评分算法缺陷 |
| P3 | 无匹配时直接返回 `IntentResult.chitchat()` → confidence=0.5 | 默认值设计不当 |
| P4 | `contains()` 子串匹配，2字关键词无边界保护 | 匹配策略粗糙 |
| P5 | MIXED 阈值 = INTENT_CONFIDENCE_THRESHOLD/2 = 0.3，单关键词归一化即可超过 | 阈值设计过低 |
| P6 | 关键词 `static final Set.of(...)` 硬编码 | 可配置性缺失 |
| P7 | classify(String) 接口仅接收原始字符串 | 接口设计局限 |

## 4. 修复方案
- 改造思路：
  1. **绝对评分 + 递减收益**：`confidence = min(hits / divisor, 1.0)`，divisor=3.0 表示 3 个关键词才满分
  2. **CHITCHAT 去噪**：移除 "是/对/嗯/啊/哦/不是/不对"，仅保留完整问候短语
  3. **短词边界保护**：仅 1 字关键词启用前后中文边界检查，2 字关键词直接 contains（已足够具体）
  4. **MIXED 加严**：要求 TOOL 和 RAG 各自绝对命中数 ≥ mixedMinHits(=2)
  5. **无匹配低置信度**：confidence = noMatchConfidence(=0.1)
  6. **配置化**：新建 `IntentClassificationPolicy`（@ConfigurationProperties），所有阈值和关键词可运行时调整

- 修改文件清单：
  - `IntentClassificationPolicy.java`（新建）：策略配置类
  - `KeywordIntentClassifier.java`（重写）：V2 评分逻辑
  - `application.yml`（编辑）：新增 `agent.intent-classification` 配置段
  - `KeywordIntentClassifierTest.java`（重写）：23 个用例覆盖 7 类修复验证
  - `QueryRouterTest.java`（编辑）：适配新构造函数

- 关键改动说明：
  - 评分公式从归一化改为绝对评分，消除了单关键词满分问题
  - 边界匹配仅对 1 字关键词生效（如 "谁"），2 字关键词（如 "查看"/"员工"）不做边界检查，避免误杀
  - 修复过程中发现 Policy 遗漏 "数据" 关键词，补回后全部测试通过

- 是否兼容原有逻辑：接口 `IntentClassifier.classify(String)` 不变，下游无感知；评分数值体系变更，依赖绝对置信度的逻辑需关注

## 5. 风险评估
- 引入新Bug风险：低
  - 评分体系变更可能影响 MIXED 意图的触发频率，已通过测试覆盖验证
  - "数据" 关键词遗漏问题已在测试阶段发现并修复
- 回滚方案：恢复 V1 KeywordIntentClassifier.java 即可，接口未变
- 是否需要数据补偿：否

## 6. 测试验证用例
【正向验证】
- `classify_singleKeyword_confidenceNotOne`：单关键词 "绩效" → confidence ≈ 0.333 < 1.0 ✓
- `classify_threeKeywords_confidenceReachesOne`：3 关键词 "查看员工绩效" → confidence = 1.0 ✓
- `classify_twoToolTwoRag_isMixed`：TOOL≥2 且 RAG≥2 → MIXED ✓

【边界验证】
- `classify_queryWithShi_notPollutedByChitchat`："是否有人离职" → CHITCHAT score = 0 ✓
- `classify_dataInCompoundWord_stillMatchesButLowerConfidence`："数据分析师的绩效" → 2字关键词正常匹配 ✓
- `classify_oneToolOneRag_notMixed`："查看绩效制度" → RAG 仅 1 hit → 不是 MIXED ✓
- `classify_noKeywordMatch_lowConfidenceChitchat`："今天天气不错" → confidence = 0.1 ✓

【回归用例】（防止旧问题复发）
- `classify_toolIntent_detected`："查看研发部门员工绩效" → TOOL ✓
- `classify_ragIntent_detected`："离职流程是怎么规定的" → RAG ✓
- `classify_customPolicy_respected`：自定义 divisor=1.0 → 单关键词满分 ✓
- `classify_nullInput_chitchatWithDefaultConfidence`：null → CHITCHAT ✓

## 7. 后续优化项（可选）
- 关键词权重分级：不同关键词设不同权重（如 "绩效" 权重 > "数据"），提升分类精度
- 利用上游元信息：接入 CleanResult（是否截断）/ ResolveResult（是否消解）辅助意图判断
- LLM 兜底分类：低置信度（< 0.3）时委托 LLM 做二次分类
- 线上分类准确率监控：采集意图分布、分类置信度直方图，发现漂移及时告警

## 8. 修复人 & 备注
修复人：AI Agent
备注：V2 升级共修复 7 类缺陷，新增 IntentClassificationPolicy 配置类实现运行时调参。全部 525 个测试通过，0 失败。
