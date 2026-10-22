package com.luutry.agent.security.auth;

/**
 * 用户上下文ThreadLocal持有器
 * <p>
 * 用途：在请求链路中存储和获取当前用户的UserContext，实现跨层透传。
 * 处理问题：Controller → Planner → Tool/Memory/RAG多层调用中，
 * 如果通过参数传递用户信息会导致方法签名污染、耦合度高。
 * 处理方式：使用ThreadLocal在请求入口set用户上下文，各层随时get获取；
 * 请求结束时必须调用clear()防止线程复用导致上下文泄露。
 * </p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 设置当前线程的用户上下文
     *
     * @param userContext 待设置的用户上下文
     */
    public static void set(UserContext userContext) {
        CONTEXT.set(userContext);
    }

    /**
     * 获取当前线程的用户上下文
     *
     * @return 当前用户上下文，未设置时返回null
     */
    public static UserContext get() {
        return CONTEXT.get();
    }

    /**
     * 清除用户上下文，防止线程复用时内存泄露
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
