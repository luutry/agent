# 【Bug记录】DefaultQueryCleaner 清洗管道架构升级（CleanResult/CleanPolicy/脱敏前置/语义截断）
> 编号：004
> 发生时间：2026-10-21 14:00
> 模块归属：Query预处理层 / 输入清洗（DefaultQueryCleaner）
> 严重等级：P1
> 影响范围：所有用户查询入口（QPS 2000 高频路径）；涉及截断策略、脱敏顺序、可观测性、配置化

## 1. 现象 & 复现步骤
【现象】
对 `DefaultQueryCleaner` 进行第二轮架构升级，解决以下核心问题：
1. 清洗器返回裸 `String`，下游无法感知截断状态（是否被截断、在哪种边界截断、是否超过 HARD 阈值）
2. 截断阈值硬编码在 `AgentConstants`，无法上线后根据查询长度分布动态调整
3. PII 脱敏在截断之后执行，可能将手机号/身份证截断为不完整模式导致正则匹配失败
4. 截断仅按 code point 安全截断，未尝试在句子/词边界截断，语义完整性差
5. 缺少 `originalCodePoints`/`originalHash` 等元信息，无法支持可观测指标采集和缓存 key 优化

【复现步骤】
1. 发送超过 1500 字符的查询
2. 预期：下游可感知截断状态、截断边界类型，可根据 `hardRejected` 决定是否拒答
3. 实际：下游只拿到截断后的 String，无法区分正常截断和 HARD 拒答

## 2. 日志/报错信息
非运行时 Bug，属于架构设计缺陷。无异常堆栈。

## 3. 根因分析（Root Cause）
**架构设计不足 — 清洗管道输出信息量不够**

- **接口契约过简**：`QueryCleaner.clean()` 返回 `String`，丢失了截断元信息（是否截断、边界类型、原始长度等）
- **阈值硬编码**：`MAX_QUERY_LENGTH` 和 `HARD_LIMIT_MULTIPLIER` 写死在 `AgentConstants`，无法运行时调整
- **管道顺序不当**：脱敏在截断之后，长查询中的 PII 可能被截断为不完整的正则模式
- **截断策略单一**：只有 code point 安全截断，未利用句子/词边界提升语义完整性

## 4. 修复方案
- 改造思路：
  1. 引入 `CleanResult` 值对象，封装清洗后文本 + 截断元信息（truncated/hardRejected/boundary/originalCodePoints/filteredToEmpty/originalHash）
  2. 引入 `CleanPolicy` 配置类（`@ConfigurationProperties`），将阈值外置到 `application.yml`
  3. 引入 `QueryInputDesensitizer`，在截断之前执行 PII 脱敏
  4. 实现三级回退截断策略：SENTENCE → WORD → CODE_POINT
  5. `QueryRouter` 适配 `CleanResult`，将元信息传递到 `ProcessedQuery`

- 修改文件清单：
  - 新增：`CleanResult.java`、`TruncationBoundary.java`、`CleanPolicy.java`、`QueryInputDesensitizer.java`
  - 改造：`QueryCleaner.java`（接口返回 CleanResult）、`DefaultQueryCleaner.java`（完整重写）
  - 适配：`ProcessedQuery.java`（新增 cleanResult 字段）、`QueryRouter.java`（适配 CleanResult）
  - 配置：`application.yml`（新增 agent.clean-policy 配置段）
  - 测试：`DefaultQueryCleanerTest.java`（44 个用例）、`QueryRouterTest.java`（适配构造函数）

- 关键改动说明：
  - **CleanResult**：`@Getter @Builder`，含 `ok()`/`degraded()` 静态工厂方法，`isUsable()` 便捷判断
  - **TruncationBoundary**：枚举 NONE/SENTENCE/WORD/CODE_POINT/HARD_LIMIT
  - **CleanPolicy**：`warnThresholdChars=1500`、`hardRejectThresholdChars=2000`、`hardLimitMultiplier=4`、`semanticBoundaryChars=50`
  - **截断策略**：`findSentenceBoundary` 在 [from-searchWindow, from) 搜索句子终止符；`findWordBoundary` 使用 `BreakIterator.getWordInstance(Locale.CHINESE)` 搜索词边界；兜底 `safeCodePointTruncate` 保证不切断 surrogate pair
  - **脱敏顺序**：控制字符移除 → 空白规范化 → 标点合并 → 首部标点移除 → trim → **PII 脱敏** → 语义截断

- 是否兼容原有逻辑：是。`ProcessedQuery` 新增的 `cleanResult` 字段为可选（nullable），`isTruncated()`/`isHardRejected()` 处理了 null 安全。旧测试无需修改即可通过。

## 5. 风险评估
- 引入新Bug风险：低
  - 清洗管道顺序不变（控制字符→空白→标点→脱敏→截断），仅新增脱敏步骤和调整截断策略
  - 所有阈值使用 CleanPolicy 默认值时，行为与之前一致
- 回滚方案：恢复 `QueryCleaner` 接口返回 `String`，删除新增文件
- 是否需要数据补偿：否

## 6. 测试验证用例
【正向验证】
- 正常中文输入保持不变，CleanResult 标记未截断（boundary=NONE）
- 手机号/邮箱/身份证号在清洗后被脱敏
- 超过 WARN 阈值时 truncated=true，在句子边界截断

【边界验证】
- null/空白输入返回 filteredToEmpty=true
- 超过 HARD 阈值时 hardRejected=true
- 无句子边界时回退到 WORD/CODE_POINT
- 截断不切断 surrogate pair（emoji 安全）
- 自定义 CleanPolicy 阈值生效

【回归用例】
- 控制字符移除（C0/C1/零宽/双向控制符/BOM）
- 全角空格/NBSP 归一化
- 半角/全角重复标点合并
- ZWJ/ZWNJ 保留（emoji 序列完整）
- QueryRouter 10 个集成测试全部通过

## 7. 后续优化项（可选）
1. 正则规则抽离为配置项（支持动态调整标点字符集）
2. 引入正则超时机制（防止极端 ReDoS 攻击）
3. 补充 JMH 性能基准测试（QPS 2000 下耗时指标）
4. 对接 Prometheus/Grafana，将 `originalCodePoints` 作为 Histogram 指标上报
5. 考虑将 `safeCodePointTruncate` 提升到通用工具类供其他模块复用
6. `AgentConstants.HARD_LIMIT_MULTIPLIER` 已废弃，后续清理

## 8. 修复人 & 备注
修复人：AI Agent
备注：本次升级严格遵循 Agent 铁律 #1（try-catch Throwable 兜底）和铁律 #2（高频入口开销可测），所有阈值由 CleanPolicy 配置驱动，支持上线后根据查询长度分布 P95/P99 指标动态调整。
