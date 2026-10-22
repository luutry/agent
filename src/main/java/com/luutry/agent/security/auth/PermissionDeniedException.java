package com.luutry.agent.security.auth;

import com.luutry.agent.common.exception.AgentException;
import com.luutry.agent.common.exception.ErrorCodes;

/**
 * 权限拒绝专用异常
 * <p>
 * 用途：当用户尝试访问无权限的数据或功能时抛出，携带具体的拒绝原因。
 * 处理问题：通用AgentException无法区分"权限拒绝"和"其他业务错误"，
 * 前端/调用方需要明确知道是权限问题以做相应UI提示。
 * 处理方式：继承AgentException，固定使用PERMISSION_DENIED错误码，
 * 额外携带reason字段说明具体拒绝原因（如"无权查看该部门数据"）。
 * </p>
 */
public class PermissionDeniedException extends AgentException {

    private final String reason;

    public PermissionDeniedException(String reason) {
        super(ErrorCodes.PERMISSION_DENIED);
        this.reason = reason;
    }

    public PermissionDeniedException(String reason, Throwable cause) {
        super(ErrorCodes.PERMISSION_DENIED, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
