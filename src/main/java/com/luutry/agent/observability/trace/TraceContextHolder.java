package com.luutry.agent.observability.trace;

/**
 * 链路追踪上下文ThreadLocal持有器
 * <p>
 * 用途：在请求链路中存储和获取当前请求的TraceContext，实现跨层透传。
 * 处理问题：Agent各阶段（意图分类、Planner、Tool、RAG等）都需要记录埋点日志，
 * 如果通过方法参数传递traceId/spanId会导致方法签名污染、耦合度高。
 * 处理方式：使用ThreadLocal在请求入口set追踪上下文，各层随时get获取；
 * 请求结束时必须调用clear()防止线程复用导致上下文泄露。
 * </p>
 */
public final class TraceContextHolder {

    private static final ThreadLocal<TraceContext> CONTEXT = new ThreadLocal<>();

    private TraceContextHolder() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 设置当前线程的追踪上下文
     *
     * @param traceContext 待设置的追踪上下文
     */
    public static void set(TraceContext traceContext) {
        CONTEXT.set(traceContext);
    }

    /**
     * 获取当前线程的追踪上下文
     *
     * @return 当前追踪上下文，未设置时返回null
     */
    public static TraceContext get() {
        return CONTEXT.get();
    }

    /**
     * 清除追踪上下文，防止线程复用时内存泄露
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
