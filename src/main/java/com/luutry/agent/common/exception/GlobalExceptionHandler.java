package com.luutry.agent.common.exception;

import com.luutry.agent.common.model.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 用途：统一拦截Controller层抛出的所有异常，转换为标准ApiResponse格式返回。
 * 处理问题：避免各Controller重复编写try-catch和错误响应逻辑，防止异常堆栈泄露给前端。
 * 处理方式：
 * 1. AgentException → 提取ErrorCode，返回对应HTTP状态码和业务错误信息；
 * 2. MethodArgumentNotValidException → 参数校验失败，返回400；
 * 3. 其他未知异常 → 记录日志，返回500通用错误，屏蔽内部异常细节。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentException(AgentException e, HttpServletRequest request) {
        log.error("Agent exception at [{}]: code={}, message={}", request.getRequestURI(),
                e.getErrorCode().getCode(), e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.fail(e.getErrorCode());
        HttpStatus status = HttpStatus.OK;
        if (e.getErrorCode() instanceof ErrorCodes errorCodes) {
            status = errorCodes.getHttpStatus();
        }
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation failed: {}", message);
        return new ResponseEntity<>(ApiResponse.fail(ErrorCodes.PARAM_ERROR.getCode(), message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error at [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return new ResponseEntity<>(ApiResponse.fail(ErrorCodes.SYSTEM_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
