"""
步骤2：用 Softmax 回归训练意图分类器。
输入：data/train_embedded.json（带向量的训练数据）
输出：控制台打印评估报告 + 内存中生成 W 矩阵和 b 向量

运行方式：python 02_train.py
"""

import json
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import cross_val_score
from sklearn.metrics import classification_report, confusion_matrix

# ==================== 配置区 ====================

INPUT_FILE = "data/train_embedded.json"

# 类别顺序（必须与项目中 QueryIntent 枚举的索引顺序一致）
# 项目代码中 CLASS_INDEX = [TOOL, RAG, MIXED, CHITCHAT]
LABELS = ["TOOL", "RAG", "MIXED", "CHITCHAT"]

# ==================== 数据加载 ====================

def load_data(filepath: str):
    """
    加载带向量的训练数据。
    返回 X（矩阵，每行是一个 1024 维向量）和 y（数组，每个值是类别索引 0~3）。
    """
    with open(filepath, "r", encoding="utf-8") as f:
        samples = json.load(f)

    X = []  # 特征矩阵
    y = []  # 标签数组

    for sample in samples:
        # 跳过向量化失败的样本
        if sample["embedding"] is None:
            continue

        X.append(sample["embedding"])
        # 将文本标签转为数字索引：TOOL→0, RAG→1, MIXED→2, CHITCHAT→3
        label_index = LABELS.index(sample["label"])
        y.append(label_index)

    # 转为 numpy 数组（类似 Java 的 List<double[]> → double[][]）
    X = np.array(X)  # shape: [N, 1024]
    y = np.array(y)  # shape: [N]

    return X, y


# ==================== 训练 ====================

def train_model(X, y):
    """
    训练 Softmax 回归模型。
    
    参数说明：
    - C=10.0：正则化强度的倒数。C 越大越不过拟合，越小越容易过拟合。
              类似 Java 中没有直接等价物，可以理解为"模型复杂度控制"。
    - max_iter=1000：最大迭代次数，类似训练轮数。
    - multi_class="multinomial"：多分类模式（我们有 4 个类别）。
    - solver="lbfgs"：优化算法，适合中小数据集。
    """
    model = LogisticRegression(
        C=10.0,
        max_iter=1000,
        solver="lbfgs"
    )

    print("开始训练...")
    model.fit(X, y)
    print("训练完成！\n")

    return model


# ==================== 评估 ====================

def evaluate_model(model, X, y):
    """评估模型准确率并打印分类报告。"""

    # 5 折交叉验证（把数据分 5 份，轮流用 4 份训练、1 份测试，取平均准确率）
    # 类似 Java 中没有直接等价物，是 ML 领域的标准评估方法
    cv_scores = cross_val_score(model, X, y, cv=5, scoring="accuracy")
    print(f"5 折交叉验证准确率: {cv_scores.mean():.4f} ± {cv_scores.std():.4f}\n")

    # 在全部训练数据上预测
    y_pred = model.predict(X)

    # 分类报告（精确率、召回率、F1 值）
    print("分类报告:")
    print(classification_report(y, y_pred, target_names=LABELS))

    # 混淆矩阵（看哪些样本被分错了）
    print("混淆矩阵:")
    cm = confusion_matrix(y, y_pred)
    print(cm)
    print(f"\n矩阵含义（行=真实类别，列=预测类别）:")
    print(f"       预测TOOL  预测RAG  预测MIXED  预测CHITCHAT")
    for i, label in enumerate(LABELS):
        row = "  ".join(f"{cm[i][j]:>6}" for j in range(len(LABELS)))
        print(f"实际{label:>10}: {row}")

    return model


# ==================== 主流程 ====================

def main():
    # 加载数据
    print(f"加载数据: {INPUT_FILE}")
    X, y = load_data(INPUT_FILE)
    print(f"数据量: {X.shape[0]} 条, 向量维度: {X.shape[1]}")
    print(f"各类别分布: {dict(zip(LABELS, np.bincount(y, minlength=4)))}\n")

    # 训练
    model = train_model(X, y)

    # 评估
    model = evaluate_model(model, X, y)

    # 提取权重（下一步导出用）
    W = model.coef_       # shape: [4, 1024]，类似 Java 的 double[][]
    b = model.intercept_  # shape: [4]，类似 Java 的 double[]

    print(f"\n权重矩阵 W 维度: {W.shape}")
    print(f"偏置向量 b 维度: {b.shape}")
    print(f"\n提示：运行 03_export.py 导出为 YAML 配置文件")


if __name__ == "__main__":
    main()
