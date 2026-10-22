package com.luutry.agent.planner.task;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.planner.model.AgentTask;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.SubTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认任务分解器实现。
 * 为简单查询创建单个AgentTask，
 * 为复合查询创建多个子任务。
 */
@Component
public class DefaultTaskDecomposer implements TaskDecomposer {

    @Override
    public List<AgentTask> decompose(ProcessedQuery processedQuery) {
        if (processedQuery == null || processedQuery.getFinalQuery().isBlank()) {
            return Collections.emptyList();
        }

        // 如果是复合查询且包含子任务，创建子任务
        if (processedQuery.isComposite() && !processedQuery.getSubTasks().isEmpty()) {
            return decomposeComposite(processedQuery);
        }

        // 单个任务
        AgentTask task = new AgentTask(processedQuery.getFinalQuery());
        task.setIntent(processedQuery.getIntent());
        return List.of(task);
    }

    /**
     * 将复合查询分解为多个子任务。
     */
    private List<AgentTask> decomposeComposite(ProcessedQuery processedQuery) {
        List<SubTask> subTasks = processedQuery.getSubTasks();
        int maxSubTasks = AgentConstants.PLANNER_MAX_SUBTASKS;

        // 创建父任务
        AgentTask parentTask = new AgentTask(processedQuery.getFinalQuery());

        List<AgentTask> childTasks = new ArrayList<>();
        int limit = Math.min(subTasks.size(), maxSubTasks);

        for (int i = 0; i < limit; i++) {
            SubTask subTask = subTasks.get(i);
            AgentTask childTask = new AgentTask(subTask.getQuery());
            // 复合查询的每个子任务按自己的意图执行，避免 MIXED 顶层意图让所有子任务都跑 Tool+RAG
            childTask.setIntent(subTask.getIntent());
            childTask.setParentTaskId(parentTask.getId());
            childTasks.add(childTask);
            parentTask.addSubTask(childTask);
        }

        return childTasks;
    }
}
