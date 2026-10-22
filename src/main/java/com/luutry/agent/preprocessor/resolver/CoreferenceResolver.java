package com.luutry.agent.preprocessor.resolver;

import java.util.List;

/**
 * 指代消解接口（V2）。
 * <p>
 * 利用对话历史上下文，解析用户查询中的代词和引用。
 * 返回 {@link ResolveResult} 包含消解后文本及元信息（是否消解、置信度、消解路径、代词-实体映射），
 * 下游根据 resolved/confidence/method 做差异化决策。
 * </p>
 * <p>
 * 核心原则：宁可不错，不可错改。消解失败或置信度不足时，降级返回原文。
 * </p>
 */
public interface CoreferenceResolver {

    /**
     * 利用对话历史解析查询中的指代。
     *
     * @param query        当前用户查询，可能包含代词
     * @param historyTurns 近期对话轮次，用于上下文（最新在前）
     * @return 消解结果，包含消解后文本和元信息；不会返回 null
     */
    ResolveResult resolve(String query, List<String> historyTurns);
}
