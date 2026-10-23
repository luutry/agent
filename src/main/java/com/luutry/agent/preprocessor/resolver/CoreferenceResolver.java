package com.luutry.agent.preprocessor.resolver;

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
     * <p>
     * 历史不再由调用方传入：实现自行注入 {@code ConversationHistoryService}，
     * 仅在检测到代词/省略、确需上下文时才按 sessionId 拉取（用到再拿）。
     * </p>
     *
     * @param query     当前用户查询，可能包含代词
     * @param sessionId 会话标识，用于按需拉取历史；为空时退化为单轮消解
     * @return 消解结果，包含消解后文本和元信息；不会返回 null
     */
    ResolveResult resolve(String query, String sessionId);
}
