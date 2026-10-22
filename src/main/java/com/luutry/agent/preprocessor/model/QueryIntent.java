package com.luutry.agent.preprocessor.model;

/**
 * 查询意图类型枚举。
 * 将用户输入分为四类，用于路由分发。
 */
public enum QueryIntent {

    /**
     * 基于Tool的HR数据查询意图。
     * 触发工具调用：员工检索、团队指标、绩效查询。
     */
    TOOL("tool", "通过工具调用查询HR数据"),

    /**
     * 基于RAG的文档检索意图。
     * 触发知识库搜索：HR制度、规章、流程。
     */
    RAG("rag", "通过RAG检索查询制度/规章"),

    /**
     * 混合意图，需要同时调用Tool和RAG。
     * 用户在一次查询中同时要求数据查询和制度解释。
     */
    MIXED("mixed", "组合工具 + RAG查询"),

    /**
     * 闲聊或无效输入。
     * 问候、偏题或无意义查询。
     */
    CHITCHAT("chitchat", "闲聊或无关输入");

    private final String code;
    private final String description;

    QueryIntent(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
