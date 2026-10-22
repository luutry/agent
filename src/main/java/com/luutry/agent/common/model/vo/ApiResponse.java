package com.luutry.agent.common.model.vo;

import com.luutry.agent.common.exception.ErrorCode;
import com.luutry.agent.common.exception.ErrorCodes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 所有REST接口的统一API响应包装类。
 * <p>
 * 用途：统一所有API接口的响应格式。
 * 处理问题：客户端期望一致的响应结构，包含状态码、消息和数据体；
 * 没有包装类时，每个接口返回不同格式。
 * 处理方式：泛型包装器包含code（业务错误码）、message（人类可读描述）、
 * data（类型化数据体）。支持基于ErrorCode的失败和简单字符串响应。
 * </p>
 *
 * @param <T> 数据体的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 业务错误码（如"000000"表示成功，"100001"表示系统错误）
     */
    private String code;

    /**
     * 描述结果的人类可读消息
     */
    private String message;

    /**
     * 类型化数据体
     */
    private T data;

    /**
     * 响应生成时的时间戳（距纪元的毫秒数）
     */
    private long timestamp = System.currentTimeMillis();

    /**
     * 不带时间戳的便捷构造方法（使用当前时间）
     */
    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 创建带数据的成功响应。
     *
     * @param data 响应数据体
     * @param <T>  数据体类型
     * @return 包含SUCCESS错误码的ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCodes.SUCCESS.getCode(), ErrorCodes.SUCCESS.getMessage(), data);
    }

    /**
     * 创建无数据的成功响应。
     *
     * @param <T> 数据体类型
     * @return 包含SUCCESS错误码的ApiResponse
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCodes.SUCCESS.getCode(), ErrorCodes.SUCCESS.getMessage(), null);
    }

    /**
     * 根据ErrorCode创建失败响应。
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据体类型（null）
     * @return 包含错误码code和message的ApiResponse
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 使用明确的code和message创建失败响应。
     *
     * @param code    业务错误码
     * @param message 人类可读的错误描述
     * @param <T>     数据体类型（null）
     * @return 包含给定code和message的ApiResponse
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 创建错误请求响应。
     *
     * @param message 错误描述
     * @param <T>     数据体类型（null）
     * @return 包含PARAM_ERROR错误码的ApiResponse
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(ErrorCodes.PARAM_ERROR.getCode(), message, null);
    }

    /**
     * 创建内部服务器错误响应。
     *
     * @param message 错误描述
     * @param <T>     数据体类型（null）
     * @return 包含SYSTEM_ERROR错误码的ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ErrorCodes.SYSTEM_ERROR.getCode(), message, null);
    }

    /**
     * 检查此响应是否表示成功。
     */
    public boolean isSuccess() {
        return ErrorCodes.SUCCESS.getCode().equals(code);
    }
}
