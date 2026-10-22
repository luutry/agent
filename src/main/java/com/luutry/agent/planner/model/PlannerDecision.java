package com.luutry.agent.planner.model;

/**
 * 规划器决策类型枚举。
 * 表示规划器在每个ReAct步骤中决定执行的动作。
 */
public enum PlannerDecision {

    /** 调用业务工具（员工检索、团队指标等） */
    CALL_TOOL("call_tool"),

    /** 从RAG知识库检索文档 */
    CALL_RAG("call_rag"),

    /** 从记忆模块读取相关记忆 */
    READ_MEMORY("read_memory"),

    /** 直接回答用户，不调用外部服务 */
    DIRECT_ANSWER("direct_answer"),

    /** 结束任务并生成最终响应 */
    FINISH("finish");

    private final String code;

    PlannerDecision(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
