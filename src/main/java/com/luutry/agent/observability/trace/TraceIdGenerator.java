package com.luutry.agent.observability.trace;

import java.util.UUID;

/**
 * TraceId和SpanId生成器
 * <p>
 * 用途：生成全链路追踪所需的唯一标识符（traceId和spanId）。
 * 处理问题：各阶段需要唯一ID来关联日志和指标，手动生成容易重复或格式不一致。
 * 处理方式：基于UUID生成32位无横杠的小写十六进制字符串作为traceId；
 * spanId使用相同算法独立生成，保证同一请求内各span可区分。
 * </p>
 */
public final class TraceIdGenerator {

    private TraceIdGenerator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 生成唯一追踪ID（32位无横杠的十六进制字符串）
     *
     * @return 唯一追踪ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成唯一Span ID（32位无横杠的十六进制字符串）
     *
     * @return 唯一Span ID
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
