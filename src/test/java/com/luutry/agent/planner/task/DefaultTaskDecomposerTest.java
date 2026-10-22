package com.luutry.agent.planner.task;

import com.luutry.agent.planner.model.AgentTask;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.preprocessor.model.SubTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultTaskDecomposer.
 */
class DefaultTaskDecomposerTest {

    private DefaultTaskDecomposer decomposer;

    @BeforeEach
    void setUp() {
        decomposer = new DefaultTaskDecomposer();
    }

    @Test
    void decompose_nullQuery_returnsEmpty() {
        List<AgentTask> tasks = decomposer.decompose(null);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void decompose_blankQuery_returnsEmpty() {
        ProcessedQuery pq = new ProcessedQuery("");
        List<AgentTask> tasks = decomposer.decompose(pq);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void decompose_singleQuery_returnsOneTask() {
        ProcessedQuery pq = new ProcessedQuery("查看员工绩效");
        pq.setIntent(QueryIntent.TOOL);
        List<AgentTask> tasks = decomposer.decompose(pq);
        assertEquals(1, tasks.size());
        assertEquals("查看员工绩效", tasks.get(0).getQuery());
        assertEquals(QueryIntent.TOOL, tasks.get(0).getIntent(), "单任务应继承顶层意图");
    }

    @Test
    void decompose_compositeQuery_returnsMultipleTasks() {
        ProcessedQuery pq = new ProcessedQuery("查看绩效，同时看看考勤");
        pq.setIntent(QueryIntent.MIXED);
        pq.setComposite(true);
        SubTask first = new SubTask(0, "查看绩效");
        first.setIntent(QueryIntent.TOOL);
        SubTask second = new SubTask(1, "看看考勤");
        second.setIntent(QueryIntent.RAG);
        pq.addSubTask(first);
        pq.addSubTask(second);

        List<AgentTask> tasks = decomposer.decompose(pq);
        assertEquals(2, tasks.size());
        assertEquals(QueryIntent.TOOL, tasks.get(0).getIntent(), "子任务应携带自己的意图");
        assertEquals(QueryIntent.RAG, tasks.get(1).getIntent(), "子任务应携带自己的意图");
    }

    @Test
    void decompose_compositeQuery_subTasksHaveParentId() {
        ProcessedQuery pq = new ProcessedQuery("parent query");
        pq.setComposite(true);
        pq.addSubTask(new SubTask(0, "child 1"));
        pq.addSubTask(new SubTask(1, "child 2"));

        List<AgentTask> tasks = decomposer.decompose(pq);
        // Each child should have a parent task ID set
        assertNotNull(tasks.get(0).getParentTaskId());
        assertNotNull(tasks.get(1).getParentTaskId());
    }

    @Test
    void decompose_excessiveSubTasks_limitedToMax() {
        ProcessedQuery pq = new ProcessedQuery("many sub tasks");
        pq.setComposite(true);
        for (int i = 0; i < 10; i++) {
            pq.addSubTask(new SubTask(i, "sub task " + i));
        }

        List<AgentTask> tasks = decomposer.decompose(pq);
        assertTrue(tasks.size() <= 5); // PLANNER_MAX_SUBTASKS = 5
    }
}
