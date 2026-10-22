package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.SubTask;

import java.util.List;

/**
 * 任务分解器接口。
 * 将复合用户查询拆分为独立子任务，
 * 供Planner按顺序或并行执行。
 */
public interface TaskDecomposer {

    /**
     * 将复合查询分解为子任务。
     *
     * @param query 用户查询，可能包含多个意图
     * @return SubTask列表；查询非复合时返回空列表
     */
    List<SubTask> decompose(String query);

    /**
     * 检查查询是否为复合查询（包含多个子问题）。
     *
     * @param query 用户查询
     * @return 需要分解时返回true
     */
    boolean isComposite(String query);
}
