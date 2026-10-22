package com.luutry.agent.common.exception;

/**
 * 统一错误码接口
 * <p>
 * 用途：定义所有业务错误码的标准契约，供异常类和全局处理器统一使用。
 * 处理问题：不同模块的错误码格式不统一，导致前端/调用方难以解析和分类处理错误。
 * 处理方式：通过接口约束code（字符串编码）和message（人类可读描述），各模块通过枚举实现该接口扩展自己的错误码。
 * </p>
 */
public interface ErrorCode {

    /**
     * 获取错误码
     *
     * @return 错误码字符串
     */
    String getCode();

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    String getMessage();
}
