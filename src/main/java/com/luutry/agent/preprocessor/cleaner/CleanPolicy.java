package com.luutry.agent.preprocessor.cleaner;

import com.luutry.agent.common.constant.AgentConstants;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 查询清洗策略配置。
 * <p>
 * 用途：将清洗阈值、截断倍数等参数外置为 Spring Boot 配置属性，
 * 支持上线后根据长度分布指标动态调整，无需修改代码重新部署。
 * </p>
 * <p>
 * 配置项前缀：{@code agent.clean-policy}，对应 application.yml 中的：
 * <pre>
 * agent:
 *   clean-policy:
 *     warn-threshold-chars: 2000
 *     hard-reject-threshold-chars: 2000
 *     hard-limit-multiplier: 4
 *     semantic-boundary-chars: 50
 * </pre>
 * </p>
 * <p>
 * 默认值直接引用 {@link AgentConstants}，避免 yml 与常量两套数值漂移；
 * 启动时 {@link #validate()} 会修正非法配置（0/负数/上下限颠倒），
 * 防止一次手滑让清洗器静默把所有查询清空。
 * </p>
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.clean-policy")
public class CleanPolicy {

    /**
     * WARN 截断阈值（字符数）。
     * 清洗后文本超过此长度，CleanResult.truncated = true。
     */
    private int warnThresholdChars = AgentConstants.MAX_QUERY_LENGTH;

    /**
     * HARD 拒答阈值（字符数）。
     * 原始输入超过此长度，CleanResult.hardRejected = true，由上游 Controller 拒答。
     */
    private int hardRejectThresholdChars = AgentConstants.MAX_QUERY_LENGTH;

    /**
     * ReDoS 防护硬截断倍数。
     * 硬截断上限 = warnThresholdChars × 此倍数。
     */
    private int hardLimitMultiplier = AgentConstants.HARD_LIMIT_MULTIPLIER;

    /**
     * 语义边界回退搜索窗口（字符数）。
     * 截断时从 WARN 阈值位置向前搜索句子/词边界的最大距离。
     */
    private int semanticBoundaryChars = 50;

    /**
     * 修正非法配置：非正阈值回退默认值，HARD 不得小于 WARN，窗口不得超出阈值。
     * 只告警不抛异常，避免一个配置项打挂启动（R8）。
     */
    @PostConstruct
    void validate() {
        if (warnThresholdChars <= 0) {
            log.warn("clean-policy.warnThresholdChars={} 非法，回退默认 {}", warnThresholdChars,
                    AgentConstants.MAX_QUERY_LENGTH);
            warnThresholdChars = AgentConstants.MAX_QUERY_LENGTH;
        }
        if (hardRejectThresholdChars < warnThresholdChars) {
            log.warn("clean-policy.hardRejectThresholdChars={} 小于 warn={}，已抬到 warn",
                    hardRejectThresholdChars, warnThresholdChars);
            hardRejectThresholdChars = warnThresholdChars;
        }
        if (hardLimitMultiplier <= 0) {
            log.warn("clean-policy.hardLimitMultiplier={} 非法，回退默认 {}",
                    hardLimitMultiplier, AgentConstants.HARD_LIMIT_MULTIPLIER);
            hardLimitMultiplier = AgentConstants.HARD_LIMIT_MULTIPLIER;
        }
        if (semanticBoundaryChars <= 0 || semanticBoundaryChars > warnThresholdChars) {
            log.warn("clean-policy.semanticBoundaryChars={} 非法，回退默认 50", semanticBoundaryChars);
            semanticBoundaryChars = 50;
        }
    }
}
