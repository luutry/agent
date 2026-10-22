package com.luutry.agent.critique;

import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.critique.model.CritiqueResult;
import com.luutry.agent.planner.model.AgentResponse;
import com.luutry.agent.preprocessor.model.ProcessedQuery;

/**
 * 反思校验服务接口，用于验证Agent回答质量。
 * <p>
 * 用途：定义Agent响应生成后校验的契约。
 * 处理问题：Agent可能产生幻觉、冲突或不完整的回答；
 * 反思层在返回给用户之前检测这些问题。
 * 处理方式：接受Agent响应、使用的聚合上下文和原始Query；
 * 返回包含校验结果、置信度和具体问题的CritiqueResult。
 * </p>
 */
public interface SelfCritiqueService {

    /**
     * 根据聚合上下文和原始Query校验Agent的回答。
     *
     * @param response  待校验的Agent响应
     * @param context   可用的聚合上下文
     * @param query     原始处理后的Query
     * @return CritiqueResult，包含校验结果、置信度、问题列表和可选的修正回答
     */
    CritiqueResult critique(AgentResponse response, AggregatedContext context, ProcessedQuery query);
}
