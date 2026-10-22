"""
步骤1：将训练数据中的查询文本转为 Embedding 向量。
输入：data/train.jsonl（纯文本 + 标签）
输出：data/train_embedded.json（文本 + 标签 + 1024维向量）

运行方式：python 01_embed_data.py
"""

import json       # Python 内置 JSON 处理（类似 Java 的 Jackson）
import time       # 计时用（类似 Java 的 System.currentTimeMillis()）
import requests   # HTTP 客户端（类似 Java 的 OkHttp）

# ==================== 配置区（按需修改）====================

# DashScope API 配置（和项目中 application.yml 一致）
API_KEY = "sk-ws-H.PXLEHII.IrR8.MEUCIQDACVS-2Md9R80_Y6-c3Is8tWXOqtkFwapBA6VDmNIucQIgTvc0egzj7mhaf-uYj0V2mLaOioKt1Cdqn5y3XiBckfk"
BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
MODEL = "text-embedding-v3"

# 文件路径
INPUT_FILE = "data/train.jsonl"
OUTPUT_FILE = "data/train_embedded.json"

# ==================== 核心函数 ====================

def call_embedding_api(text: str) -> list[float]:
    """
    调用 DashScope Embedding API，将文本转为 1024 维向量。
    类似 Java 中的：embeddingService.embed(text)
    
    参数：text - 要向量化的文本
    返回：float 列表，长度 1024
    """
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": MODEL,
        "input": text
    }

    # 发送 HTTP POST 请求（类似 Java 的 httpClient.post(...)）
    response = requests.post(BASE_URL, headers=headers, json=payload, timeout=30)

    # 检查是否成功（类似 Java 的 if (response.getStatusCode() != 200)）
    if response.status_code != 200:
        raise Exception(f"API 调用失败: {response.status_code} - {response.text}")

    # 解析 JSON 响应（类似 Java 的 objectMapper.readTree(...)）
    result = response.json()
    embedding = result["data"][0]["embedding"]

    return embedding


def main():
    print(f"读取训练数据: {INPUT_FILE}")

    # 读取 JSONL 文件（每行一个 JSON 对象）
    samples = []
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:  # 跳过空行
                samples.append(json.loads(line))  # json.loads 类似 Jackson 的 readValue

    print(f"共 {len(samples)} 条训练数据")

    # 逐条调用 Embedding API
    start_time = time.time()
    for i, sample in enumerate(samples):
        query = sample["query"]
        print(f"[{i+1}/{len(samples)}] 向量化: {query}")

        try:
            # 调用 API 获取向量
            vec = call_embedding_api(query)
            # 将向量存入样本
            sample["embedding"] = vec
            print(f"  → 成功，维度: {len(vec)}")
        except Exception as e:
            print(f"  → 失败: {e}")
            sample["embedding"] = None

        # API 限流保护：每次调用间隔 0.2 秒
        time.sleep(0.2)

    elapsed = time.time() - start_time
    print(f"\n向量化完成，耗时: {elapsed:.1f}秒")

    # 统计成功/失败数
    success = sum(1 for s in samples if s["embedding"] is not None)
    print(f"成功: {success}, 失败: {len(samples) - success}")

    # 保存结果（类似 Java 的 objectMapper.writerWithDefaultPrettyPrinter().writeValue(...)）
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(samples, f, ensure_ascii=False, indent=2)

    print(f"结果已保存: {OUTPUT_FILE}")


# Python 的入口点（类似 Java 的 public static void main(String[] args)）
if __name__ == "__main__":
    main()
