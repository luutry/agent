# 【Bug记录】DefaultQueryCleaner 代码审查多缺陷修复

> 编号：003
> 发生时间：2026-10-21 10:00
> 模块归属：Query预处理层 / 输入清洗（DefaultQueryCleaner）
> 严重等级：P1
> 影响范围：所有用户查询入口，QPS 2000 高频路径；涉及安全（日志注入/ReDoS）、功能（emoji 损坏/中文空白残留）、可观测性缺失

## 1. 现象 & 复现步骤

【现象】
对 `DefaultQueryCleaner` 做代码审查，发现 11 项已知问题（P1–P11）+ 5 项补充问题（P12–P16），涵盖：
1. 无异常兜底，正则异常将导致整条链路崩溃（违反 Agent 铁律 #1）
2. 超长输入先走 6 次全量正则再截断，存在 ReDoS CPU 放大风险
3. 控制字符正则误删 ZWJ/ZWNJ，破坏 emoji 序列和多语言文本
4. C1 控制符、行/段分隔符、双向控制符未清理，存在日志注入风险
5. 全角空格 `\u3000`、NBSP `\u00A0` 不被 `\s+`/`trim` 处理，中文查询残留
6. `substring` 按 UTF-16 code unit 截断，可能切断 emoji 代理对
7. 半角标点 `???`/`!!!`/`...` 未被合并，注释与实现不一致
8. 重复标点阈值 `{2,}` 导致 2 个不合并、3 个才合并，边界语义不直观
9. 无 `@AgentTrace` 注解，预处理第一步在链路追踪中不可见
10. 各步骤未拆为独立方法，可测试性差

【复现步骤】
1. 提交超长输入（>1MB）到 `/chat` 接口
2. 预期结果：快速拒绝或截断处理
3. 实际结果：6 次正则在 1MB 上全量扫描，CPU 飙升
4. 提交包含 emoji ZWJ 序列的查询（如 👨‍👩）
5. 预期结果：emoji 序列完整保留
6. 实际结果：ZWJ 被移除，emoji 解体
7. 提交包含全角空格的中文查询
8. 预期结果：全角空格被归一化
9. 实际结果：`\u3000` 残留在输出中

## 2. 日志/报错信息

无运行时异常日志（属于代码审查发现的潜在缺陷）。关键风险点：

```
// 风险1：无 try-catch，正则异常直接传播
public String clean(String rawQuery) {
    // 6 次正则全量遍历...
    // 最后才截断 → ReDoS 攻击面
}

// 风险2：CONTROL_CHAR_PATTERN 包含 \u200B-\u200F，覆盖 ZWJ(\u200D)/ZWNJ(\u200C)
// 风险3：substring 按 char 索引截断，可能切断 surrogate pair
// 风险4：缺少 C1(\u0080-\u009F)、\u2028/\u2029、双向控制符(\u202A-\u202E)
```

## 3. 根因分析（Root Cause）

**代码逻辑缺陷 + 安全设计不足**：

1. **异常隔离缺失**：`clean()` 方法无 try-catch Throwable 包裹，违反 Agent 铁律 #1（预处理链路最外层必须 catch Throwable，失败降级返回原始输入）。Java regex 引擎在特殊输入下可能抛出 `StackOverflowError` 或 `OutOfMemoryError`。

2. **管道顺序不当**：先做 6 次全量正则遍历，最后才截断。超长输入（如攻击者提交 1MB 文本）导致正则扫描量 ≥6MB/请求，QPS 2000 下构成 ReDoS 攻击面。

3. **字符集覆盖不全**：
   - 控制字符正则 `\u200B-\u200F` 误删 ZWJ/ZWNJ（emoji 序列和多语言拼写必需）
   - 遗漏 C1 控制符 `\u0080-\u009F`、行/段分隔符 `\u2028-\u2029`、双向控制符 `\u202A-\u202E`
   - 空白正则 `\s+` 在 Java 默认模式下不包含 `\u3000`（全角空格）和 `\u00A0`（NBSP）

4. **截断不安全**：`substring(0, MAX_QUERY_LENGTH)` 按 UTF-16 code unit 索引截断，若截断点恰好位于 surrogate pair 中间，产生孤立代理对，下游 JSON 序列化/LLM 调用可能异常。

5. **标点字符集不完整**：只含全角标点，半角 `?!.,;:` 未纳入；注释写 "???"→"?" 但实现不处理半角。

6. **可观测性缺失**：`clean()` 方法未标注 `@AgentTrace`，预处理管道第一步在链路追踪中完全不可见。

## 4. 修复方案

- **改造思路**：
  1. 入口先做硬截断（`MAX_QUERY_LENGTH * 4 = 8000`），再执行正则，限制正则输入上界
  2. 整个 `clean()` 方法 try-catch Throwable 兜底，异常降级返回硬截断后的输入
  3. 控制字符正则精确化：排除 ZWJ/ZWNJ，补入 C1、`\u2028-\u2029`、双向控制符
  4. 空白正则显式包含 `\u3000`、`\u00A0`
  5. 标点字符集补齐半角，重复标点阈值改为 `+`（≥2 即合并）
  6. `safeTruncate` 使用 code point 安全截断，检测 surrogate pair 边界回退
  7. 各步骤抽为 package-private 方法，提升可测试性
  8. 添加 `@AgentTrace(stage="query_clean")` 接入可观测链路

- **修改文件清单**：
  - `AgentConstants.java`：新增 `HARD_LIMIT_MULTIPLIER = 4`
  - `DefaultQueryCleaner.java`：核心修复
  - `DefaultQueryCleanerTest.java`：测试用例从 8 个扩展到 40 个

- **关键改动说明**：
  - `CONTROL_CHAR_PATTERN`：`[\x00-\x08\x0B\x0C\x0E-\x1F\x7F\u0080-\u009F\u200B\u200E-\u200F\u2028-\u2029\u202A-\u202E\u2066-\u2069\uFEFF]`（排除 ZWJ/ZWNJ）
  - `WHITESPACE_PATTERN`：`[\s\u3000\u00A0]+`
  - `REPEATED_PUNCT_PATTERN`：`([!?！？。，、；：.,;:])\1+`
  - 入口硬截断：`rawQuery.length() > hardLimit ? rawQuery.substring(0, hardLimit) : rawQuery`
  - `safeTruncate`：检测 `Character.isHighSurrogate(charAt(maxLen-1))` 回退

- **是否兼容原有逻辑**：是；无数据变更风险。清洗结果与原有正常输入行为一致，仅修复异常输入和边界场景。

## 5. 风险评估

- **引入新Bug风险**：低
  - 硬截断前置不影响正常输入（≤2000 字符的输入不触发）
  - try-catch 兜底保证异常时降级返回
  - 所有修改均有对应测试用例覆盖

- **回滚方案**：直接回退 `DefaultQueryCleaner.java` 和 `AgentConstants.java` 到修改前版本即可，无数据依赖。

- **是否需要数据补偿**：否

## 6. 测试验证用例

【正向验证】
- `clean_normalInput_returnsAsIs`：正常中文输入保持不变
- `clean_controlChars_removed`：C0 控制字符被移除
- `clean_multipleWhitespace_normalized`：多个空白字符归一化
- `clean_repeatedPunctuation_collapsed`：连续重复全角标点合并

【边界验证】
- `clean_twoRepeatedPunct_collapsed`：2 个相同标点也合并（P4 修复验证）
- `clean_truncateAtSurrogatePair_safeTruncate`：截断点位于 surrogate pair 中间时回退（P5 修复验证）
- `clean_exceedsHardLimit_truncatedFirst`：超过硬截断上限的输入先截断再清洗（P2 修复验证）
- `clean_zwj_preserved`：ZWJ(\u200D) 保留，不破坏 emoji 序列（P7 修复验证）
- `clean_zwnj_preserved`：ZWNJ(\u200C) 保留，不破坏多语言文本（P7 修复验证）
- `clean_fullWidthSpace_normalized`：全角空格 \u3000 被归一化（P6 修复验证）
- `clean_nbsp_normalized`：NBSP \u00A0 被归一化（P6 修复验证）
- `clean_c1ControlChars_removed`：C1 控制字符被移除（P8 修复验证）
- `clean_lineSeparator_removed`：行分隔符 \u2028 被移除（P8 修复验证）
- `clean_bidiControl_removed`：双向控制符 \u202E 被移除（P8 修复验证）
- `clean_halfWidthQuestionMarks_collapsed`：半角 ??? 合并为 ?（P3 修复验证）

【回归用例】（防止旧问题复发）
- `clean_nullInput_returnsEmpty`：null 输入返回空
- `clean_blankInput_returnsEmpty`：空白输入返回空
- `clean_exceedsMaxLength_truncated`：超长输入截断至 MAX_QUERY_LENGTH
- `safeTruncate_nullInput_returnsNull`：safeTruncate null 安全
- `safeTruncate_shortInput_returnsAsIs`：短于阈值不截断
- `clean_multiQuestion_structurePreserved`：多问号查询保留结构供下游分解

## 7. 后续优化项（可选）

1. **正则合并为单次扫描**：当前 6 次 `replaceAll` 各自遍历字符串，可考虑合并为单次扫描（如自定义 `CharSequence` 或 `Matcher` 链），进一步降低高频路径开销。需 JMH 基准测试验证收益。
2. **标点字符集外置配置**：当前标点字符集硬编码在 Pattern 中，后续可考虑抽到 `application.yml` 或 `AgentConstants` 中，支持动态调整。
3. **尾部标点移除**：当前清洗器不处理尾部标点（与下游分解器冲突）。后续若分解器升级（如改用 LLM 意图分类），可重新引入尾部标点归一化。
4. **Soft hyphen `\u00AD` 清理**：HR 场景罕见，当前未处理，后续按需补充。
5. **清洗耗时指标**：在 `@AgentTrace` 基础上，可增加清洗步骤级别的耗时指标（各步骤耗时占比），便于定位性能瓶颈。

## 8. 修复人 & 备注

修复人：luutry
备注：本次为代码审查驱动的预防性修复，非运行时异常触发。审查识别 P1–P16 共 16 项问题，全部修复并通过 40 个单元测试验证。其中 P9（尾部标点移除）因与下游 `RuleBasedTaskDecomposer` 的问号计数逻辑冲突，经分析后决定清洗器只负责归一化，尾部标点语义由下游分解器处理，职责更清晰。
