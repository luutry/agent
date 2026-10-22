package com.luutry.agent.llm;

import java.util.List;

/**
 * LLM服务抽象接口。
 * <p>
 * 为 Agent 流水线提供统一的大语言模型调用接口，将业务逻辑与具体 LLM 提供商解耦。
 * </p>
 */
public interface LlmService {

    /**
     * 根据系统提示词和用户消息生成LLM响应。
     *
     * @param systemPrompt 定义LLM角色和约束的系统级指令
     * @param userMessage  用户输入消息
     * @return LLM生成的文本响应
     */
    String generate(String systemPrompt, String userMessage);

    /**
     * 仅使用用户消息生成LLM响应（使用默认系统提示词）。
     *
     * @param userMessage 用户输入消息
     * @return LLM生成的文本响应
     */
    String generate(String userMessage);

    /**
     * 生成结构化回答。
     *
     * @param query        原始用户查询
     * @param observations ReAct引擎收集的上下文观察列表
     * @param intent       分类后的查询意图
     * @return 综合后的回答文本
     */
    String generateAnswer(String query, List<String> observations, String intent);

    /**
     * 估算给定文本的Token数量（近似值）。
     *
     * @param text 待估算的输入文本
     * @return 估算的Token数量
     */
    int estimateTokens(String text);
}
