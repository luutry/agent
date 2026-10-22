package com.luutry.agent.planner.model;

/**
 * 任务执行状态枚举。
 * 表示AgentTask的生命周期状态。
 */
public enum TaskStatus {

    /** 任务已创建但尚未开始 */
    PENDING("pending"),

    /** 任务正在执行中 */
    RUNNING("running"),

    /** 任务已成功完成 */
    COMPLETED("completed"),

    /** 任务因错误或超时而失败 */
    FAILED("failed"),

    /** 任务在完成前被取消 */
    CANCELLED("cancelled");

    private final String code;

    TaskStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 检查该状态是否为终态。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
