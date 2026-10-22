# 011 period 时间维度与 Mock 数据口径

> 来源：`doc/链路追踪/20261022_003_链路V2复审.md` 的 N6（period 未用）与 N9（Mock 数据不自洽）。
> 状态：待开始　优先级：P1　关联阶段：真实数据源接入（TODO-001/002/005）之后

## 背景

### N6 period 槽位缺失
- 查询「本月各部门离职率」中的「本月」被静默忽略：`ProcessedQuery`/`QueryUnit` 无 period 槽位，
  `TeamMetricsTool` 只读 `deptId`，`turnoverRate` 是 `MockHrDataProvider` 里的静态字段，与时间无关。
- 当前缓解：`DataTemplateRenderer` 命中时间限定词（本月/上月/今年/…）时，在答案追加
  「（当前快照，不支持按时间筛选）」，避免让用户误以为是本月数据（R5 隐性编造前提）。
- 这是权宜之计，不是真支持时间维度。

### N9 Mock 数据口径不自洽
- 部门级 `currentStaff`（技术部 15）与员工明细条数（技术部 5 条）不一致；
  `averageTenureYears`、`performanceDistribution`、`employmentTypeDistribution` 按明细样本算，却代表整个部门。
- 当前处理：在 `MockHrDataProvider.initEmployees()` 注明「明细是样本、部门级字段才权威」，
  并把 `totalEmployees`（明细记录数）移出 FactSet（见 `FactExtractor.NON_BUSINESS_METRICS`）。

## 待办项

1. **抽取 period 槽位**：在预处理管道识别时间表达（本月/上季度/近 12 个月…），归一化为时间窗，
   挂到 `QueryUnit`；抽取规则未定义，需产品确认口径（自然月？财月？滚动窗？）。
2. **period 下推工具**：`TeamMetricsTool` 等接收时间窗参数；不支持时显式返回 `periodSupported=false`，
   渲染器据此声明，取代当前的关键词快照声明。
3. **Mock 数据一致化**：真实数据接入后，保证明细与部门级计数一致；若明细为抽样，聚合指标不得直接从抽样算，
   须用部门级权威值或标注抽样口径。
4. **口径命名字段化**：`quota`（编制）/`onDuty`（在职）等以字段而非中文标签区分，杜绝「人数」歧义（配合 N2）。

## 约束

- 表结构、时间窗口径、字段语义未定义前不得猜测实现（AGENTS.md §5）。
- 代码处已留 TODO 注释：`DataTemplateRenderer.TIME_WORDS`、`MockHrDataProvider.initEmployees()`。
