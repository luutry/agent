package com.luutry.agent.observability.trace;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链路追踪上下文
 * <p>
 * 用途：封装一次Agent请求的全链路追踪信息，贯穿从Query进入到最终响应的完整生命周期。
 * 处理问题：Agent执行涉及多个阶段（意图分类、Planner、RAG、Tool等），
 * 缺少统一的链路标识导致各阶段日志无法关联，排查问题困难。
 * 处理方式：每个请求创建唯一traceId，各阶段创建spanId记录子任务；
 * 通过ThreadLocal在整个请求链路中传递，各模块可随时读取当前trace信息；
 * attributes字段支持各阶段追加自定义属性（如token消耗、工具名等）。
 * </p>
 */
@Data
public class TraceContext {

    /**
     * 整个Agent请求链路的全局唯一追踪ID
     */
    private final String traceId;

    /**
     * 当前活动阶段的Span ID
     */
    private String spanId;

    /**
     * 父Span ID，用于嵌套Span追踪
     */
    private String parentSpanId;

    /**
     * 当前阶段名称（如 "intent_classification"、"planner"、"tool_execution"）
     */
    private String stageName;

    /**
     * 起始时间戳（毫秒）
     */
    private final long startTimeMs;

    /**
     * 自定义属性，用于可扩展的元数据
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public TraceContext(String traceId) {
        this.traceId = traceId;
        this.startTimeMs = System.currentTimeMillis();
    }

    public TraceContext(String traceId, String spanId, String stageName) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.stageName = stageName;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * 将自定义属性放入追踪上下文
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void putAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 从追踪上下文获取自定义属性
     *
     * @param key 属性键
     * @return 属性值，不存在时返回null
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 计算自追踪上下文创建以来的耗时
     *
     * @return 耗时（毫秒）
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
}
