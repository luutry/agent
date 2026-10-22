package com.luutry.agent.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 标准错误码枚举实现
 * <p>
 * 用途：预定义Agent系统通用的标准错误码集合，覆盖通用异常、参数校验、权限、限流、工具执行等场景。
 * 处理问题：各模块各自定义错误字符串，导致错误码散乱、重复、不一致。
 * 处理方式：通过枚举集中管理所有标准错误码，每个枚举值包含code、message和对应的HTTP状态码；
 * 后续各阶段模块可扩展新的枚举值，但必须实现ErrorCode接口。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCodes implements ErrorCode {

    SUCCESS("000000", "Success", HttpStatus.OK),
    SYSTEM_ERROR("100001", "System error", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAM_ERROR("100002", "Invalid parameter", HttpStatus.BAD_REQUEST),
    DATA_NOT_FOUND("100003", "Data not found", HttpStatus.NOT_FOUND),
    AUTH_FAILED("200001", "Authentication failed", HttpStatus.UNAUTHORIZED),
    PERMISSION_DENIED("200002", "Permission denied", HttpStatus.FORBIDDEN),
    DATA_SCOPE_EXCEEDED("200003", "Data scope exceeded", HttpStatus.FORBIDDEN),
    TOOL_EXECUTION_FAILED("300001", "Tool execution failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TOOL_TIMEOUT("300002", "Tool execution timeout", HttpStatus.GATEWAY_TIMEOUT),
    RATE_LIMIT_EXCEEDED("400001", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    INTENT_UNRECOGNIZED("500001", "Intent unrecognized", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
