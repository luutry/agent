# 004 Embedding 模型接入

| 字段 | 内容 |
|---|---|
| 状态 | 待开始 |
| 优先级 | P1 |
| 关联阶段 | Phase 6 之后 |
| 影响模块 | `rag`、`memory.longterm` |
| 前置依赖 | 模型选型、部署方式（本地 / 服务化）、维度确认 |

## 背景

`EmbeddingModel` 接口已定义，当前为 `MockEmbeddingModel`：
对文本做字符 hash 映射到固定维度，**只保证维度正确、不保证语义**，
仅用于验证链路连通与单测。

## 为什么暂缓

Embedding 服务未部署；模型选型未定（中文语义效果、维度、推理成本三者需权衡）。

## 目标

新增真实实现，具备批量、缓存、超时、降级能力，且**维度与 Milvus schema 绑定**。

## 设计要点

1. **维度锁定**：维度一旦确定写入 `AgentConstants.EMBEDDING_DIMENSIONS`，
   变更需同时重建 Milvus collection 并重灌数据。
2. **归一化**：输出统一做 L2 归一化，检索统一用 COSINE。
3. **批量**：单次最多 64 条，超出自动分批。
4. **缓存**：Redis，key = `sha256(text) + modelVersion`，TTL 7 天。
5. **超时与降级**：单次 2s；失败返回零向量并标记 `degraded=true`，
   由检索层决定「跳过向量通道，只用 BM25」。
6. **版本化**：模型升级必须换 `modelVersion`，新旧向量不可混用。

## 验收标准

- [ ] 同一文本两次调用返回相同向量（确定性）
- [ ] 归一化后模长为 1（误差 < 1e-5）
- [ ] 批量接口在 64 条、65 条、1000 条三种输入下行为正确
- [ ] 超时触发降级，检索仍能用 BM25 返回结果
- [ ] 缓存命中率可观测

## 风险

- **语义漂移**：不同批次模型可能不一致，必须版本化
- **长文本截断**：超过模型最大长度会被截断，需在切片阶段控制
- **成本**：高频 query 全部向量化开销大，缓存是必需项
- **与 Milvus 的维度强绑定**：改维度等于重建库，需提前规划

## 相关代码位置

- `com.luutry.agent.rag.embedding.EmbeddingModel`
- 新增 `com.luutry.agent.rag.embedding.HttpEmbeddingModel`
- 降级装饰器 `com.luutry.agent.rag.embedding.DegradableEmbeddingModel`
