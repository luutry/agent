package com.luutry.agent.preprocessor.resolver;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 指代消解结果值对象
 * <p>
 * 用途：封装消解后的文本及丰富的元信息，供下游（QueryRouter/Planner）根据
 * resolved/confidence/method 做出差异化决策。
 * 设计原则：宁可不错，不可错改。下游应优先检查 {@link #isResolved()} 为 true
 * 且 {@link #getConfidence()} 高于业务阈值时，才使用消解后文本。
 * </p>
 */
@Getter
@Builder
public class ResolveResult {

    /** 消解后的查询文本（未成功消解时与原文相同） */
    private final String resolvedText;

    /** 是否成功执行了消解替换 */
    private final boolean resolved;

    /** 消解置信度 [0.0, 1.0]，规则快速路径固定为 1.0 */
    private final double confidence;

    /** 产生此结果的消解路径 */
    private final ResolveMethod method;

    /** 被替换的代词 → 对应实体映射（不可变） */
    private final Map<String, String> resolvedPairs;

    /** 构建未消解结果（原文透传） */
    public static ResolveResult unresolved(String originalText) {
        return ResolveResult.builder()
                .resolvedText(originalText)
                .resolved(false)
                .confidence(1.0)
                .method(ResolveMethod.DEGRADED)
                .resolvedPairs(Collections.emptyMap())
                .build();
    }

    /** 构建规则快速路径结果 */
    public static ResolveResult ruleResult(String resolvedText, Map<String, String> pairs) {
        return ResolveResult.builder()
                .resolvedText(resolvedText)
                .resolved(true)
                .confidence(1.0)
                .method(ResolveMethod.RULE_FAST_PATH)
                .resolvedPairs(pairs != null ? Collections.unmodifiableMap(pairs) : Collections.emptyMap())
                .build();
    }

    /** 构建LLM消解结果 */
    public static ResolveResult llmResult(String resolvedText, double confidence, Map<String, String> pairs) {
        return ResolveResult.builder()
                .resolvedText(resolvedText)
                .resolved(true)
                .confidence(Math.max(0.0, Math.min(1.0, confidence)))
                .method(ResolveMethod.LLM_RESOLUTION)
                .resolvedPairs(pairs != null ? Collections.unmodifiableMap(pairs) : Collections.emptyMap())
                .build();
    }

    /** 构建省略补全结果：当前查询极短（纯人名），从上一轮继承谓语 */
    public static ResolveResult ellipsisResult(String resolvedText) {
        return ResolveResult.builder()
                .resolvedText(resolvedText)
                .resolved(true)
                .confidence(0.9)
                .method(ResolveMethod.ELLIPSIS_RESOLUTION)
                .resolvedPairs(Collections.emptyMap())
                .build();
    }

    /** 获取不可变的代词-实体映射 */
    public Map<String, String> getResolvedPairs() {
        return Collections.unmodifiableMap(resolvedPairs);
    }
}
