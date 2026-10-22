package com.luutry.agent.preprocessor.rewriter;

import java.util.List;

/**
 * 查询改写器接口。
 * 使用同义词扩展查询、标准化HR术语，
 * 并提取关键词供下游处理使用。
 */
public interface QueryRewriter {

    /**
     * 改写并扩展查询，以提升检索效果。
     *
     * @param query 清洗/消解后的查询
     * @return 扩展后的改写查询
     */
    String rewrite(String query);

    /**
     * 从查询中提取关键词。
     *
     * @param query 输入查询
     * @return 提取的关键词列表
     */
    List<String> extractKeywords(String query);
}
