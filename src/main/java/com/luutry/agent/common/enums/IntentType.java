package com.luutry.agent.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 意图分类枚举
 * <p>
 * 用途：标识用户Query经预处理后的意图类型，决定后续路由到哪个处理链路。
 * 处理问题：Agent需要区分不同类型的问题，分别交给RAG、Tool或混合模式处理，避免错误路由。
 * 处理方式：Query预处理层对用户输入做分类，输出IntentType枚举值；
 * Planner根据IntentType决策调用RAG模块、Tool模块或两者组合。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum IntentType {

    RAG("rag", "制度文档问答，路由到RAG检索模块"),
    TOOL("tool", "人事数据查询，路由到Tool工具中心"),
    MIXED("mixed", "混合查询，同时需要RAG和Tool"),
    CHAT("chat", "闲聊或无效提问，不触发业务模块");

    private final String code;
    private final String description;
}
