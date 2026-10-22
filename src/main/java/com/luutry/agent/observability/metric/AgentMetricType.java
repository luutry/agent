package com.luutry.agent.observability.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent指标类型枚举
 * <p>
 * 用途：定义Agent系统需要采集的所有指标类型，供指标采集器按类型上报和统计。
 * 处理问题：各模块上报指标时使用字符串标识容易拼写错误且无法统一管理；
 * 需要集中定义指标类型，方便后续对接Prometheus/Micrometer等监控系统。
 * 处理方式：每个枚举值包含code（标识符）、description（中文描述）和unit（度量单位）；
 * 各模块通过AgentMetricRecorder接口上报指标值时引用此枚举。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AgentMetricType {

    TASK_COMPLETION_RATE("task_completion_rate", "任务完成率", "ratio"),
    TOOL_CALL_ROUNDS("tool_call_rounds", "工具调用轮次", "count"),
    HALLUCINATION_RATE("hallucination_rate", "幻觉率", "ratio"),
    END_TO_END_RT("end_to_end_rt", "端到端响应时间", "ms"),
    RAG_RECALL_COUNT("rag_recall_count", "RAG召回数量", "count"),
    TOKEN_CONSUMED("token_consumed", "Token消耗量", "count"),
    COREF_TRIGGER_RATE("coref_trigger_rate", "指代消解触发率", "ratio"),
    COREF_LLM_HIT_RATE("coref_llm_hit_rate", "LLM消解命中率", "ratio"),
    COREF_DEGRADE_RATE("coref_degrade_rate", "消解降级率", "ratio");

    private final String code;
    private final String description;
    private final String unit;
}
