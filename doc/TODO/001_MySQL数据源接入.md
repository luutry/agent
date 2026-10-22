# 001 MySQL 数据源接入

| 字段 | 内容 |
|---|---|
| 状态 | 待开始 |
| 优先级 | P0 |
| 关联阶段 | Phase 1 之后 |
| 影响模块 | `repository` |
| 前置依赖 | 表结构确认、数据库账号、网络策略开通 |

## 背景

当前四个 Repository 全部为 Mock 实现，数据来自 `resources/mock/*.json`：

- `MockEmployeeRepository`
- `MockOrgUnitRepository`
- `MockPerformanceRepository`
- `MockPolicyDocumentRepository`

接口契约已在 Phase 1 定稿，见 `doc/ARCHITECTURE.md` 第 5 节。

## 为什么暂缓

数据库尚未提供，表结构与字段含义未确认。按 AGENTS.md 第 5 节，不自行假设表结构。

## 目标

新增 MyBatis 实现，通过配置切换，**调用方代码零改动**。

```yaml
agent:
  datasource:
    mode: mock   # mock | mysql
```

## 实施要点

1. 新增 `MybatisEmployeeRepository` 等实现类，装配按 `agent.datasource.mode` 切换。
2. 权限过滤必须落在 SQL：`WHERE dept_id IN (:deptIds)`，禁止查完再过滤。
3. 列表查询禁止 N+1：关联数据用批量查询或 JOIN。
4. 绩效表默认只返回聚合结果，明细查询需单独接口且受更严权限控制。
5. 连接池、超时、慢查询阈值全部配置化，写入 `application-mysql.yml`。
6. 建立表结构与 Java 实体的映射层，**不要为了适配表结构去改 Mock 的字段名**。

## 验收标准

- [ ] `agent.datasource.mode=mysql` 启动成功，`mock` 仍可用
- [ ] 四个 Repository 的既有单测在两种实现下都通过
- [ ] 越权查询返回空集（SQL 层过滤，可通过慢查询日志验证）
- [ ] 10 万员工量级下，列表查询 P95 < 200ms
- [ ] 绩效明细不出现在任何默认查询结果中

## 风险

- **表结构与 Mock 不一致**：需要独立映射层，不要反向改 Mock
- **PII 字段是否加密存储**：需与 DBA 确认，若加密则读取时要解密后再脱敏
- **历史数据质量**：部门 ID 为空、离职员工未标记等，需要清洗策略
- **数据权限的树形展开**：若在 SQL 里递归可能慢，考虑先查部门集合再 IN

## 相关代码位置

- `com.luutry.agent.repository.mock.*` → 新增 `com.luutry.agent.repository.mybatis.*`
- `com.luutry.agent.config.DataSourceConfig`
