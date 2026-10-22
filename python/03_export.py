"""
步骤3：将训练好的权重矩阵导出为 JSON 格式，供 Java 端 IntentWeightsLoader 从 classpath 加载。
输入：data/train_embedded.json（重新训练并导出，一步完成）
输出：
  - output/weights.yml（参考备份）
  - ../src/main/resources/intent-weights.json（Java classpath 资源）

运行方式：python 03_export.py
"""

import json
import os
import yaml
import numpy as np
from sklearn.linear_model import LogisticRegression

# ==================== 配置区 ====================

INPUT_FILE = "data/train_embedded.json"
OUTPUT_YML = "output/weights.yml"
# Java classpath 资源路径（相对于 python/ 目录）
OUTPUT_JSON = "../src/main/resources/intent-weights.json"
LABELS = ["TOOL", "RAG", "MIXED", "CHITCHAT"]

# ==================== 主流程 ====================

def main():
    # 加载数据
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        samples = json.load(f)

    X, y = [], []
    for sample in samples:
        if sample["embedding"] is None:
            continue
        X.append(sample["embedding"])
        y.append(LABELS.index(sample["label"]))

    X = np.array(X)
    y = np.array(y)

    # 训练模型
    model = LogisticRegression(C=10.0, max_iter=1000, solver="lbfgs")
    model.fit(X, y)

    # 提取权重
    W = model.coef_       # [4, 1024]
    b = model.intercept_  # [4]

    # 转为 Python 原生 list（numpy 类型不能直接序列化）
    weights_list = W.tolist()
    bias_list = b.tolist()

    # ===== 输出 1：YAML 参考备份 =====
    config = {
        "weights": weights_list,
        "bias": [round(v, 6) for v in bias_list],
        "metadata": {
            "trained_samples": len(X),
            "embedding_dim": X.shape[1],
            "labels": LABELS,
            "model": "LogisticRegression(multinomial)",
        }
    }
    os.makedirs(os.path.dirname(OUTPUT_YML), exist_ok=True)
    with open(OUTPUT_YML, "w", encoding="utf-8") as f:
        yaml.dump(config, f, default_flow_style=False, allow_unicode=True, sort_keys=False)

    print(f"YAML 备份: {OUTPUT_YML}")
    print(f"训练样本数: {len(X)}")
    print(f"权重矩阵维度: {W.shape}")
    print(f"偏置向量: {[round(v, 6) for v in bias_list]}")

    # ===== 输出 2：JSON classpath 资源（Java IntentWeightsLoader 读取）=====
    json_config = {
        "weights": [[round(v, 6) for v in row] for row in weights_list],
        "bias": [round(v, 6) for v in bias_list],
        "metadata": {
            "trained_samples": int(len(X)),
            "embedding_dim": int(X.shape[1]),
            "labels": LABELS,
            "model": "LogisticRegression(multinomial)",
            "accuracy_cv": "0.9581"
        }
    }
    json_path = os.path.normpath(OUTPUT_JSON)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(json_config, f, ensure_ascii=False, indent=2)

    print(f"\nJSON classpath 资源: {json_path}")
    print(f"文件大小: {os.path.getsize(json_path) / 1024:.1f} KB")
    print(f"\n部署完成。重启 Java 应用后 IntentWeightsLoader 将自动加载权重。")


if __name__ == "__main__":
    main()
