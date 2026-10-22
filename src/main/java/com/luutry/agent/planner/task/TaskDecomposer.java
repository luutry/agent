package com.luutry.agent.planner.task;

import com.luutry.agent.planner.model.AgentTask;
import com.luutry.agent.preprocessor.model.ProcessedQuery;

import java.util.List;

/**
 * 任务分解器接口。
 * 将预处理后的查询转换为一个或多个AgentTask，
 * 供ReAct引擎执行。
 */
public interface TaskDecomposer {

    /**
     * 将预处理后的查询分解为可执行任务。
     *
     * @param processedQuery 带意图和子任务的预处理查询
     * @return 待执行的AgentTask列表
     */
    List<AgentTask> decompose(ProcessedQuery processedQuery);
}
