package com.luutry.agent.rag.rerank;

import com.luutry.agent.rag.model.RetrievalResult;

import java.util.List;

/**
 * 切片重排器接口，用于优化检索结果。
 * <p>
 * 用途：定义检索后重排策略的规范。
 * 处理问题：初次检索可能返回关键词匹配但上下文不最优的结果；
 * 重排引入额外信号（时效性、章节匹配、交叉编码器分数）来优化最终排序。
 * 处理方式：接收已评分的检索结果，返回优化排序后的结果。
 * </p>
 */
public interface ChunkReranker {

    /**
     * 根据查询相关性和额外信号对检索结果重排。
     *
     * @param query   原始搜索查询
     * @param results 初始检索结果
     * @return 重排后的结果（同一列表，重新排序）
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> results);
}
