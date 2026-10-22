package com.luutry.agent.observability.metric;

import java.util.Map;

/**
 * Agent指标采集器接口
 * <p>
 * 用途：定义Agent系统运行时指标的统一上报契约，各模块在关键节点调用此接口上报指标数据。
 * 处理问题：任务完成率、工具调用轮次、幻觉率、端到端RT等指标散落在各模块中，
 * 缺少统一采集入口，无法集中统计和监控。
 * 处理方式：定义record方法，接收指标类型、数值和标签（如userId、traceId等维度）；
 * 当前阶段提供日志输出实现（Slf4jMetricRecorder），后续可替换为Micrometer/Prometheus实现。
 * </p>
 */
public interface AgentMetricRecorder {

    /**
     * 记录带可选标签的指标值
     *
     * @param metricType 待记录的指标类型
     * @param value      指标值
     * @param tags       附加维度标签（如 userId、traceId）
     */
    void record(AgentMetricType metricType, double value, Map<String, String> tags);

    /**
     * 记录不带标签的指标值
     *
     * @param metricType 待记录的指标类型
     * @param value      指标值
     */
    default void record(AgentMetricType metricType, double value) {
        record(metricType, value, Map.of());
    }
}
