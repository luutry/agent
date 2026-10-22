package com.luutry.agent.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据来源标识枚举
 * <p>
 * 用途：标记每条数据/信息的来源渠道，用于溯源引用和可信度排序。
 * 处理问题：Agent回答可能融合多个数据源（Tool业务数据、RAG制度文档、会话记忆），
 * 需要明确标注来源以便用户验证，并按可信度优先级排序。
 * 处理方式：各模块返回数据时附带DataSource标签；
 * 上下文聚合管理器根据DataSource做优先级排序：TOOL > RAG > MEMORY。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum DataSource {

    TOOL("tool", "Tool业务数据库查询", 1),
    RAG("rag", "RAG人事制度文档检索", 2),
    MEMORY("memory", "会话记忆", 3);

    private final String code;
    private final String description;

    /**
     * 优先级值，数值越小优先级/可信度越高。
     * TOOL(1) > RAG(2) > MEMORY(3)
     */
    private final int priority;
}
