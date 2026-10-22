package com.luutry.agent.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务执行状态枚举
 * <p>
 * 用途：标识Planner子任务在ReAct循环中的生命周期状态。
 * 处理问题：Planner需要跟踪每个子任务的执行进度，判断是否完成、失败或需要重试。
 * 处理方式：任务创建时为PENDING，开始执行转为RUNNING，成功完成转为SUCCESS，
 * 执行异常转为FAILED，被取消转为CANCELLED；状态转换由Planner引擎驱动。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum TaskState {

    PENDING("pending", "等待执行"),
    RUNNING("running", "执行中"),
    SUCCESS("success", "执行成功"),
    FAILED("failed", "执行失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;
}
