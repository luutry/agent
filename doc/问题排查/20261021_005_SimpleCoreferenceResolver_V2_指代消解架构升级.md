# 【Bug记录】SimpleCoreferenceResolver 规则误替换严重 & V2 架构升级（规则触发+LLM主力+多层校验）
> 编号：005
> 发生时间：2026-10-21 14:00
> 模块归属：Query预处理层 - 指代消解模块
> 严重等级：P1
> 影响范围：所有多轮对话场景的指代消解结果；误替换导致语义扭曲，阻断下游意图分类和工具调用

## 1. 现象 & 复现步骤
【现象】
V1 版 `SimpleCoreferenceResolver` 使用简单 `String.replace` 进行代词替换，存在多项严重误替换：
1. `"他们的项目"` → `"张三们项目"`（"他" 抢先匹配 "他们"，替换后语义完全扭曲）
2. `"因此需要重新评估"` → `"张三需要重新评估"`（"其/此" 误匹配为代词）
3. `"关于张三的部门"` 被当作实体 → 后续追加 `"关于张三的部门的绩效"`（实体提取噪声未清理）
4. 多代词只替换一半（`break` 逻辑导致仅替换第一个匹配代词）
5. 无元信息返回，下游无法感知是否成功消解

【复现步骤】
1. 用户输入 `"他们的考勤和绩效"`，历史为 `["查看研发部门的人员名单"]`
2. V1 调用 `resolved.replace("他们", "研发部门")` → 输出 `"研发部门的考勤和绩效"`（语义丢失复数含义）
3. 用户输入 `"因此需要评估"`，历史为 `["张三的绩效是A"]`
4. V1 的 `PRONOUN_PATTERN` 匹配 "其" → 替换为 "张三" → 输出 `"张三需要评估"`（完全错误）
5. 预期：误匹配场景应返回原文，不做任何替换

## 2. 日志/报错信息
无异常堆栈，属于逻辑缺陷。通过单元测试暴露：
```
// V1 测试：resolve_withPronounAndHistory_replacesEntity
String query = "他的绩效怎么样";
List<String> history = List.of("查看张三的简历", "他是研发部门的工程师");
String result = resolver.resolve(query, history);
// V1 结果："他的绩效怎么样" → "张三的绩效怎么样" (碰巧正确，但换场景就出错)
// V1 结果："其他国家的制度" → "张三他国家的制度" (严重错误)
```

## 3. 根因分析（Root Cause）
**代码逻辑缺陷 + 架构设计不足**，具体分为 5 个根因：

1. **代词检测误触发**：`PRONOUN_PATTERN` 使用简单子串匹配，无法区分独立代词和复合词中的代词字符（如"其他"中的"他"、"因此"中的"此"）
2. **替换优先级错误**：遍历 `PRONOUNS` 集合（无序），短代词 "他" 可能抢先匹配长代词 "他们" 的前缀
3. **实体提取含噪声**：`extractEntities` 用 `lastIndexOf(suffix)` + 前固定宽度截取，无法清理前导动词（"查看张三" → 提取为 "查看张三" 而非 "张三"）
4. **break 逻辑截断**：`for` 循环中 `break` 只替换第一个匹配代词，多代词场景只处理一半
5. **无元信息返回**：接口返回裸 `String`，下游无法判断是否成功消解、消解置信度、消解路径

## 4. 修复方案
- 改造思路：**V2 三层架构**
  - Tier 1 规则触发器：Compound word 排除集 + 独立代词检测，彻底消除误触发
  - Tier 2 高置信快速路径：仅处理 "该员工/该部门/刚才那些" 等无歧义模式，<2ms
  - Tier 3 LLM 主力消解：结构化 prompt + JSON 输出 + 6 层校验（JSON结构→实体存在性→置信度阈值→文本完整性→超时控制→Throwable兜底），200-500ms
  - 核心原则：**宁可不错，不可错改**；任何校验失败均降级返回原文

- 修改文件清单：
  - 新建：`ResolveMethod.java`（消解路径枚举）、`ResolveResult.java`（消解结果值对象）、`ResolvePolicy.java`（配置类）、`LlmCoreferenceResolver.java`（LLM消解引擎）
  - 改造：`CoreferenceResolver.java`（接口返回 ResolveResult）、`SimpleCoreferenceResolver.java`（重写为编排器）、`QueryRouter.java`（消费 ResolveResult）、`ProcessedQuery.java`（增加消解元信息字段）、`AgentMetricType.java`（新增3个消解指标）、`LlmConfig.java`（注册 LlmCoreferenceResolver Bean）、`application.yml`（新增 resolve-policy 配置段）
  - 测试：`SimpleCoreferenceResolverTest.java`（24个用例重写）、`QueryRouterTest.java`（适配新构造函数）、`AgentMetricTest.java`（指标计数更新）

- 关键改动说明：
  1. **代词检测修复**：引入 `PRONOUN_COMPOUND_WORDS` 排除集（其他/因此/其中/其次等16个复合词），先剥离再检测独立代词
  2. **正则匹配顺序**：`PRONOUN_PATTERN` 按长度降序排列（他们|她们|它们|...|他|她|它），长代词优先匹配
  3. **实体噪声清理**：`extractPersonFromDePattern` 和 `extractEntityBySuffix` 均增加动词前缀清理（查看/查询/看看等）
  4. **LLM 6层校验**：JSON结构 → resolved字段 → 实体存在性（子串匹配历史）→ 置信度阈值（≥0.85）→ 文本完整性（长度偏差50%-300%）→ Throwable兜底
  5. **超时控制**：`Executors.newVirtualThreadPerTaskExecutor()` + `Future.get(1500ms)` 防止 LLM 阻塞主流程
  6. **配置化**：`ResolvePolicy` 通过 `agent.resolve-policy` 配置段驱动，支持上线后根据指标动态调整

- 是否兼容原有逻辑：接口签名变更（`String` → `ResolveResult`），不兼容；但 `ResolveResult.getResolvedText()` 提供等价文本，下游仅需适配取值方式

## 5. 风险评估
- 引入新Bug风险：中
  - LLM 调用引入网络依赖，可能超时/异常（已通过 1500ms 超时 + Throwable 兜底降级缓解）
  - 规则快速路径实体提取可能误匹配（已通过动词前缀清理 + 长度校验缓解）
- 回滚方案：`ResolvePolicy` 配置 `min-llm-confidence=1.0` 可完全禁用 LLM 路径；`high-confidence-patterns` 设为空数组可禁用规则快速路径
- 是否需要数据补偿：否（纯计算模块，无持久化数据影响）

## 6. 测试验证用例
【正向验证】24 个用例，分 5 组：
- `BasicTests`（5个）：null/blank/无代词/无历史 → 返回 unresolved
- `FalsePositiveTests`（5个）：其他/因此/其中/其次/混合 → 不误触发
- `RuleFastPathTests`（6个）：该员工→张三/该部门→产品团队/刚才那些→实体/刚才的→实体/无匹配历史→降级/映射对正确性
- `LlmPathTests`（2个）：模糊代词"他"/"他们" → 委托 LLM（mock 失败 → 降级）
- `DegradationTests`（2个）：LLM 超时/异常 → 降级返回原文
- `PronounDetectionEdgeCases`（4个）：独立"他"/"这个"/"那些" → 触发；"X的绩效" → 提取人名

【边界验证】
- `ResolveResult.unresolved()` / `.ruleResult()` / `.llmResult()` 工厂方法正确性
- `ResolveResult.getResolvedPairs()` 返回不可变 Map
- `ResolvePolicy` 默认值与配置绑定

【回归用例】
- `QueryRouterTest` 10 个用例全部通过（适配新构造函数）
- `AgentMetricTest` 指标计数从 6 更新为 9
- 全量 514 个测试通过，0 失败

## 7. 后续优化项（可选）
1. **LLM 结果缓存**：相同 query+history 组合缓存消解结果，减少重复 LLM 调用（预期命中率 30%+）
2. **异步预热**：启动时异步调用 LLM 预热连接池，降低首次请求延迟
3. **指标采集接入**：将 `COREF_TRIGGER_RATE`/`COREF_LLM_HIT_RATE`/`COREF_DEGRADE_RATE` 接入 Micrometer，对接 Prometheus 看板
4. **代词检测增强**：引入分词器（如 HanLP）替代正则，提升中文代词识别准确率
5. **多轮消解链**：当前仅处理单步消解，后续支持链式消解（"他→张三" → "张三的部门→研发部"）

## 8. 修复人 & 备注
修复人：luutry
备注：V1→V2 架构升级，核心变化是「规则全量替换」→「规则触发+LLM主力+多层校验+降级兜底」。514 测试全通过。
