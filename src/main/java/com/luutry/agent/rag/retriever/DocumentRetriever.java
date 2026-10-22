package com.luutry.agent.rag.retriever;

import com.luutry.agent.rag.model.TextChunk;

import java.util.List;

/**
 * 文档检索器接口，用于搜索文本切片。
 * <p>
 * 用途：定义切片检索策略的统一规范。
 * 处理问题：不同检索算法（BM25、稠密向量、混合）需要统一接口，
 * 以便在RAG管道中互换使用。
 * 处理方式：每种实现提供自己的评分机制；
 * 混合检索器组合多种策略。
 * </p>
 */
public interface DocumentRetriever {

    /**
     * 检索与给定查询相关的切片。
     *
     * @param query 搜索查询文本
     * @param topK  最大返回结果数
     * @return 带评分的文本切片列表，按相关性降序排列
     */
    List<TextChunk> retrieve(String query, int topK);

    /**
     * 向检索器索引中添加一个切片。
     *
     * @param chunk 待索引的文本切片
     */
    void addChunk(TextChunk chunk);

    /**
     * 向检索器索引中添加多个切片。
     *
     * @param chunks 待索引的文本切片列表
     */
    void addChunks(List<TextChunk> chunks);

    /**
     * 获取已索引切片的总数。
     *
     * @return 切片数量
     */
    int size();

    /**
     * 清除所有已索引的切片。
     */
    void clear();
}
