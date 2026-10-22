package com.luutry.agent.tool.exception;

import com.luutry.agent.common.exception.AgentException;
import com.luutry.agent.common.exception.ErrorCodes;
import lombok.Getter;

/**
 * 工具执行异常
 * <p>
 * 用途：工具执行过程中发生错误时抛出的专用异常，携带工具名称和原始异常信息。
 * 处理问题：通用AgentException无法区分是哪个工具执行失败，
 * ToolExecutor/Dispatcher需要捕获后记录具体工具名和失败原因用于日志和重试决策。
 * 处理方式：继承AgentException，固定使用TOOL_EXECUTION_FAILED错误码，
 * 额外携带toolName字段标识失败的工具名称；
 * ToolExecutor捕获此异常后根据重试策略决定是否重试。
 * </p>
 */
@Getter
public class ToolExecutionException extends AgentException {

    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super(ErrorCodes.TOOL_EXECUTION_FAILED, message);
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(ErrorCodes.TOOL_EXECUTION_FAILED, message);
        this.toolName = toolName;
        initCause(cause);
    }

    /**
     * 创建指定工具的超时异常
     *
     * @param toolName 超时的工具名称
     * @return 带超时错误码的ToolExecutionException
     */
    public static ToolExecutionException timeout(String toolName) {
        ToolExecutionException ex = new ToolExecutionException(toolName,
                "Tool execution timed out: " + toolName);
        return ex;
    }
}
