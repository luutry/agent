package com.luutry.agent.preprocessor.resolver;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指代消解方法枚举
 * <p>
 * 用途：标识最终消解结果由哪条路径产生，供下游可观测和差异化决策。
 * 处理方式：
 * - RULE_FAST_PATH：规则快速路径，高置信度直接替换（如"该员工"→具体人名）
 * - LLM_RESOLUTION：LLM主力消解，处理模糊代词（如"他/她/这个"）
 * - DEGRADED：降级返回原文，LLM超时/校验失败/异常时触发
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ResolveMethod {

    /** 规则快速路径：高置信度模式直接替换 */
    RULE_FAST_PATH("rule_fast_path", "规则快速路径"),

    /** LLM主力消解：结构化prompt + 多层校验 */
    LLM_RESOLUTION("llm_resolution", "LLM消解"),

    /** 降级：返回原文，不做任何替换 */
    DEGRADED("degraded", "降级返回原文"),

    /** 省略补全：当前查询极短（纯人名），从上一轮继承谓语 */
    ELLIPSIS_RESOLUTION("ellipsis_resolution", "省略补全");

    private final String code;
    private final String description;
}
