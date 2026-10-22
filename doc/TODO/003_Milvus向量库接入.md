# 003 Milvus 向量库接入

| 字段 | 内容 |
|---|---|
| 状态 | 待开始 |
| 优先级 | P1 |
| 关联阶段 | Phase 6 之后 |
| 影响模块 | `rag.vectorstore`、`memory.longterm` |
| 前置依赖 | Milvus 集群、网络策略、Embedding 维度确定（见 004） |

## 背景

`VectorStore` 接口已在 Phase 6 定义，当前实现为 `InMemoryVectorStore`：
内存 `Map` + 余弦相似度暴力扫描，仅适用于单测与演示。

## 为什么暂缓

Milvus 集群未部署；Embedding 维度未最终确定，而维度决定 collection schema。

## 目标

新增 `MilvusVectorStore`，保持 `VectorStore` 接口不变，
**切换只改装配，不改调用方**（AGENTS.md R11）。

## 设计要点

### Collection

| 名称 | 用途 | 关键字段 |
|---|---|---|
| `policy_chunks` | 制度文档切片 | `chunk_id`, `doc_id`, `vector`, `dept_scope`, `effective_from`, `effective_to`, `chapter_path` |
| `memory_vectors` | 长期记忆 | `memory_id`, `user_id`, `vector`, `created_at`, `expire_at` |

### 索引

- 类型：HNSW，`M=16`，`efConstruction=200`
- 度量：COSINE（向量入库前先做 L2 归一化）
- 标量字段建倒排索引：`doc_id`、`user_id`、`dept_scope`

### 过滤表达式

```text
dept_scope in ["D001", "D002"]
and effective_from <= 1700000000
and (effective_to is null or effective_to >= 1700000000)
```

权限与时效过滤**必须在检索阶段完成**，不能召回后再过滤
（既浪费配额，也会泄漏存在性信息）。

## 验收标准

- [ ] `agent.vectorstore.mode=milvus` 启动成功，`memory` 模式仍可用
- [ ] `VectorStore` 接口的所有单测在两种实现下都通过
- [ ] 10 万 chunk 下 topK=10 检索 P95 < 50ms
- [ ] 权限过滤生效：越权部门的数据不出现在结果中
- [ ] 时效过滤生效：过期制度不返回
- [ ] 提供 collection 重建脚本（维度变更时使用）

## 风险

- **维度绑定**：Embedding 维度变了必须重建 collection，且要重灌全量数据
- **删除与更新**：Milvus 的 upsert 语义与关系库不同，需明确以 `chunk_id` 为主键
- **一致性**：文档更新后向量未同步会产生「答旧制度」，需要版本号字段
- **成本**：全量重灌耗时较长，需支持增量

## 相关代码位置

- `com.luutry.agent.rag.vectorstore.VectorStore`
- 新增 `com.luutry.agent.rag.vectorstore.milvus.MilvusVectorStore`
- 配置：`agent.vectorstore.milvus.*`
