# 优化升级 #003：意图分类权重存储迁移 — 从 application.yml 到独立 classpath 资源文件

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档编号 | OPT-20261022-003 |
| 优化类型 | 架构优化 / 存储迁移 |
| 影响范围 | 预处理 → 意图路由 → 权重加载 |
| 优先级 | P1（配置可维护性） |
| 日期 | 2026-10-22 |
| 关联文档 | OPT-20261021-002（Embedding线性头架构升级）、Python训练环境搭建与权重导出完整指南 |

---

## 一、问题背景

### 1.1 原始方案

OPT-20261021-002 中，权重矩阵通过 `IntentClassificationPolicy`（`@ConfigurationProperties`）绑定到 `application.yml`：

```yaml
agent:
  intent-classification:
    weights: []                  # 空 = 冷启动用种子余弦相似度
    bias: [0.0, 0.0, 0.0, 0.0] # [TOOL, RAG, MIXED, CHITCHAT]
```

训练完成后，将 4×1024 的权重矩阵填入 `weights` 配置项。

### 1.2 问题分析

训练产出的权重矩阵包含 **4 × 1024 = 4096 个浮点数**，直接写入 `application.yml` 存在以下问题：

| 问题 | 影响 |
|------|------|
| **配置膨胀** | 4096 个浮点数使 yml 文件膨胀至约 70KB，严重影响可读性 |
| **维护困难** | 权重数据与业务配置混在一起，修改配置时容易误操作 |
| **版本管理不友好** | 每次重新训练后 diff 巨大，难以 review |
| **职责混淆** | 训练产出的数据文件与人工配置的业务参数性质不同 |
| **启动性能** | Spring 解析大量 YAML 列表数据增加启动时间 |

---

## 二、方案选型

### 2.1 候选方案对比

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A: 独立 classpath 资源文件 | JSON 格式存 `src/main/resources/`，专用 Loader 加载 | 职责分离、打包内嵌、格式清晰 | 重新训练需重新打包 |
| B: 外部文件路径 | 通过 `file:` 路径加载外部 JSON | 无需重打包即可热更新 | 部署复杂、路径配置风险 |
| C: 二进制格式 | `.bin` 或 `.dat` 存储浮点数组 | 体积最小、读取最快 | 不可读、不可 diff |
| D: Base64 编码 | 编码后嵌入 yml | 无需新文件 | 不可读、调试困难、yml 仍然膨胀 |

### 2.2 方案A选定

**独立 classpath 资源文件** 的核心优势：

1. **职责分离**：训练数据（机器产出）与业务配置（人工维护）物理隔离
2. **格式清晰**：JSON 结构化存储，含 metadata（训练样本数、维度、准确率等）
3. **打包内嵌**：随 jar 一起分发，无需额外部署配置
4. **版本管理友好**：独立文件，git diff 清晰
5. **开发体验好**：IDE 可直接查看、校验 JSON 格式

---

## 三、架构设计

### 3.1 变更前后对比

**变更前**：

```
application.yml
  └─ IntentClassificationPolicy (@ConfigurationProperties)
       ├─ weights: List<List<Double>>   ← 4096个浮点数
       ├─ bias: List<Double>
       └─ seeds, thresholds, keywords...

EmbeddingIntentClassifier
  └─ 从 policy.getWeights() 读取权重
```

**变更后**：

```
src/main/resources/intent-weights.json   ← 独立文件
  └─ { weights, bias, metadata }

IntentWeightsLoader (@Component)
  └─ 启动时从 classpath 加载 JSON
  └─ 解析权重矩阵 + 偏置向量
  └─ 加载失败时降级为空权重（走种子路径）

EmbeddingIntentClassifier
  └─ 从 weightsLoader.getWeights() 读取权重

IntentClassificationPolicy
  └─ 仅保留业务配置：seeds, thresholds, keywords...
```

### 3.2 数据流

```
Python 训练流程                    Java 运行时
─────────────────                  ─────────────
03_export.py
  │
  ├─→ src/main/resources/
  │     intent-weights.json ──→ IntentWeightsLoader ──→ EmbeddingIntentClassifier
  │                              (启动时加载一次)        (classify 时使用)
  └─→ output/
        weights.yml (备份)
```

### 3.3 降级策略

```
IntentWeightsLoader 加载流程：
  ├─ 正常 → 从 classpath:intent-weights.json 加载权重矩阵
  ├─ 文件不存在 → WARN 日志，空权重，走种子余弦相似度路径
  ├─ JSON 解析失败 → WARN 日志，空权重，走种子余弦相似度路径
  └─ 直接构造（测试用）→ 传入权重矩阵，跳过文件加载
```

---

## 四、核心实现

### 4.1 新增文件

| 文件 | 路径 | 职责 |
|------|------|------|
| `IntentWeightsLoader` | `preprocessor/router/` | 从 classpath JSON 加载权重矩阵，支持降级 |
| `intent-weights.json` | `src/main/resources/` | 训练产出的权重数据（4×1024 矩阵 + 偏置 + metadata） |
| `IntentWeightsLoaderTest` | `test/.../preprocessor/router/` | IntentWeightsLoader 单元测试（6 用例） |

### 4.2 修改文件

| 文件 | 改动要点 |
|------|---------|
| `IntentClassificationPolicy` | 移除 `weights`、`bias` 字段（-15 行） |
| `EmbeddingIntentClassifier` | 新增 `IntentWeightsLoader` 依赖，权重读取改为 `weightsLoader.getWeights()` |
| `LlmConfig` | `intentClassifier()` Bean 方法增加 `IntentWeightsLoader` 参数 |
| `application.yml` | 移除 `weights: []` 和 `bias: [0.0, ...]`，增加注释说明 |
| `python/03_export.py` | 新增 JSON 输出到 `../src/main/resources/intent-weights.json` |
| `EmbeddingIntentClassifierTest` | 适配新构造器签名，移除 `policy.setWeights/setBias` |
| `QueryRouterTest` | 适配新构造器签名 |

### 4.3 IntentWeightsLoader 核心代码

```java
@Slf4j
@Component
public class IntentWeightsLoader {

    private static final String DEFAULT_RESOURCE_PATH = "intent-weights.json";

    @Getter private final List<List<Double>> weights;
    @Getter private final List<Double> bias;
    @Getter private final int trainedSamples;
    @Getter private final int embeddingDim;

    /** 默认从 classpath:intent-weights.json 加载 */
    public IntentWeightsLoader() { this(DEFAULT_RESOURCE_PATH); }

    /** 从指定 classpath 路径加载 */
    public IntentWeightsLoader(String resourcePath) {
        // Jackson ObjectMapper 解析 JSON
        // 提取 weights, bias, metadata.trained_samples, metadata.embedding_dim
        // 异常时降级为空权重
    }

    /** 直接构造（测试用） */
    public IntentWeightsLoader(List<List<Double>> weights, List<Double> bias) { ... }

    public boolean hasWeights() {
        return weights.size() == 4 && !weights.get(0).isEmpty();
    }
}
```

### 4.4 intent-weights.json 格式

```json
{
  "weights": [
    [0.012, -0.003, ...],
    [-0.008, 0.015, ...],
    [0.005, 0.002, ...],
    [-0.010, -0.005, ...]
  ],
  "bias": [0.1, -0.05, 0.02, -0.07],
  "metadata": {
    "trained_samples": 167,
    "embedding_dim": 1024,
    "labels": ["TOOL", "RAG", "MIXED", "CHITCHAT"],
    "model": "LogisticRegression(C=10.0, solver=lbfgs)",
    "accuracy_cv": "95.81% (+/- 3.57%)"
  }
}
```

### 4.5 Spring Bean 注册（变更后）

```java
// LlmConfig.java
@Bean
@Primary
public IntentClassifier intentClassifier(EmbeddingService embeddingService,
                                         IntentClassificationPolicy policy,
                                         IntentWeightsLoader weightsLoader,      // 新增
                                         KeywordIntentClassifier keywordIntentClassifier) {
    return new EmbeddingIntentClassifier(embeddingService, policy, weightsLoader, keywordIntentClassifier);
}
```

---

## 五、训练数据扩充

配合本次权重迁移，同步完成了训练数据扩充：

| 指标 | 变更前 | 变更后 |
|------|--------|--------|
| 总样本数 | 40 | 167 |
| TOOL | 13 | 32 |
| RAG | 14 | 39 |
| MIXED | 5 | 45 |
| CHITCHAT | 8 | 51 |

训练结果（5 折交叉验证）：

```
Average CV Accuracy: 95.81% (+/- 3.57%)
Training Accuracy: 100.00%
```

---

## 六、Python 导出脚本变更

`python/03_export.py` 新增 JSON 输出：

```python
# 输出1: JSON 格式 → Java classpath 资源
json_output_path = os.path.join(java_resources_dir, "intent-weights.json")
with open(json_output_path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
print(f"✅ 权重矩阵已导出为 JSON: {json_output_path}")

# 输出2: YAML 格式 → output/ 备份（保留）
yaml_output_path = os.path.join(output_dir, "weights.yml")
```

---

## 七、测试覆盖

### 7.1 IntentWeightsLoaderTest（6 用例）

| 用例 | 验证点 |
|------|--------|
| `testLoadFromClasspath_success` | 正常加载 intent-weights.json，权重维度 4×1024 |
| `testLoadFromClasspath_fileNotFound` | 文件不存在时降级为空权重 |
| `testDirectConstruction` | 直接构造方式可用 |
| `testEmptyWeights` | 空列表时 `hasWeights()` 返回 false |
| `testInsufficientRows` | 不足 4 行时 `hasWeights()` 返回 false |
| `testEmptyRowDimensions` | 行维度为空时 `hasWeights()` 返回 false |

### 7.2 EmbeddingIntentClassifierTest 适配

- 所有 14 处构造器调用增加 `IntentWeightsLoader` 参数
- 移除所有 `policy.setWeights/setBias` 调用
- 新增 `createEmptyWeightsLoader()` 工厂方法
- 线性头测试改用 `new IntentWeightsLoader(weights, bias)` 构造

### 7.3 测试结果

```
Tests run: 536, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 八、变更清单

| 类型 | 文件 | 操作 |
|------|------|------|
| 新增 | `IntentWeightsLoader.java` | 权重加载器组件 |
| 新增 | `IntentWeightsLoaderTest.java` | 权重加载器测试 |
| 新增 | `src/main/resources/intent-weights.json` | 训练权重数据文件 |
| 修改 | `IntentClassificationPolicy.java` | 移除 weights/bias 字段 |
| 修改 | `EmbeddingIntentClassifier.java` | 增加 IntentWeightsLoader 依赖 |
| 修改 | `LlmConfig.java` | Bean 装配增加 weightsLoader 参数 |
| 修改 | `application.yml` | 移除权重配置，增加注释 |
| 修改 | `python/03_export.py` | 新增 JSON 输出 |
| 修改 | `EmbeddingIntentClassifierTest.java` | 适配新构造器 |
| 修改 | `QueryRouterTest.java` | 适配新构造器 |

---

## 九、后续规划

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P2 | 权重热更新 | 监听文件变更自动重载（开发环境） |
| P2 | 训练 Pipeline 自动化 | 定期从线上日志重新训练并导出 |
| P3 | 多模型支持 | 支持切换不同的权重文件 |
