package com.luutry.agent.common.exception;

import lombok.Getter;

/**
 * 统一业务异常类
 * <p>
 * 用途：Agent系统中所有业务逻辑异常的统一载体，携带结构化错误码信息。
 * 处理问题：原生RuntimeException无法携带业务错误码，调用方难以根据错误类型做差异化处理。
 * 处理方式：继承RuntimeException，内部持有ErrorCode引用，支持传入自定义message覆盖默认描述；
 * 全局异常处理器通过捕获此类型，自动提取ErrorCode转换为标准HTTP响应。
 * </p>
 */
@Getter
public class AgentException extends RuntimeException {

    private final ErrorCode errorCode;

    public AgentException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AgentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public AgentException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
