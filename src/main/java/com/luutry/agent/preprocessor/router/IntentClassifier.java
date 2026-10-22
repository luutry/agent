package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.IntentResult;

/**
 * 意图分类器接口。
 * 将用户查询分类为以下意图类别：
 * TOOL、RAG、MIXED 或 CHITCHAT。
 */
public interface IntentClassifier {

    /**
     * 对用户查询进行意图分类。
     *
     * @param query 预处理后的查询文本
     * @return IntentResult，包含分类意图和置信度分数
     */
    IntentResult classify(String query);
}
