package com.luutry.agent.observability.metric;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于SLF4J日志的指标采集器实现
 * <p>
 * 用途：将指标数据通过SLF4J日志输出，作为当前阶段的指标采集实现。
 * 处理问题：当前阶段尚未接入Micrometer/Prometheus等监控系统，
 * 需要一个可独立运行的指标采集实现，保证Phase 3可独立测试。
 * 处理方式：将指标类型、数值、标签格式化为结构化日志输出；
 * 后续阶段替换为MicrometerMetricRecorder，对接Prometheus指标暴露端点。
 * </p>
 */
@Slf4j
@Component
public class Slf4jMetricRecorder implements AgentMetricRecorder {

    @Override
    public void record(AgentMetricType metricType, double value, Map<String, String> tags) {
        if (metricType == null) {
            return;
        }
        log.info("[METRIC] type={}, value={}, unit={}, tags={}",
                metricType.getCode(), value, metricType.getUnit(), tags);
    }
}
