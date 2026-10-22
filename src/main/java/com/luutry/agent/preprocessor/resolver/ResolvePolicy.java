package com.luutry.agent.preprocessor.resolver;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 指代消解策略配置
 * <p>
 * 用途：集中管理指代消解模块的所有可调阈值，由 application.yml 中 agent.resolve-policy 段驱动。
 * 处理方式：上线后根据消解触发率、LLM 消解准确率、平均耗时等指标，动态调整各阈值，
 * 避免硬编码导致调参需改代码重新部署。
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.resolve-policy")
public class ResolvePolicy {

    /**
     * LLM消解最低可接受置信度。
     * LLM返回的消解结果置信度低于此值时，降级返回原文。
     * 建议范围：0.80 ~ 0.95，越高越保守。
     */
    private double minLlmConfidence = 0.85;

    /**
     * LLM调用超时时间（毫秒）。
     * 超时后降级返回原文，不阻塞主流程。
     * 根据 QPS 2000 × 触发率 10% 估算，并发 LLM 调用约 200/s。
     */
    private int llmTimeoutMs = 1500;

    /**
     * 传递给LLM的最大历史字符数。
     * 超过此长度时截断较早的历史轮次，控制 LLM prompt token 消耗。
     */
    private int maxHistoryChars = 1000;

    /**
     * 规则快速路径：高置信度代词模式。
     * 仅当 query 中匹配这些模式时才走规则快速路径直接替换，
     * 其余代词（他/她/它等模糊代词）一律走 LLM 消解。
     */
    private String[] highConfidencePatterns = {
            "该员工", "该部门", "该团队",
            "刚才那些", "刚才的"
    };
}
