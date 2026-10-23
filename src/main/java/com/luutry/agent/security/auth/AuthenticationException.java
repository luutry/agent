package com.luutry.agent.security.auth;

import com.luutry.agent.common.exception.AgentException;
import com.luutry.agent.common.exception.ErrorCodes;

/**
 * 认证失败专用异常。
 * <p>
 * 用途：令牌缺失、格式非法、验签失败或缺少 sub 声明时抛出，固定携带 AUTH_FAILED 错误码。
 * 与 PermissionDeniedException 区分：本异常表示"不知道你是谁"（401），
 * 后者表示"知道你是谁但无权"（403）。消息不得包含令牌原文（R4）。
 * </p>
 */
public class AuthenticationException extends AgentException {

    public AuthenticationException(String message) {
        super(ErrorCodes.AUTH_FAILED, message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(ErrorCodes.AUTH_FAILED, message, cause);
    }
}
