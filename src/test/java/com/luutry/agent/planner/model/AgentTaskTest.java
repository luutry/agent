package com.luutry.agent.planner.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentTask model.
 */
class AgentTaskTest {

    @Test
    void constructor_defaultStatus_isPending() {
        AgentTask task = new AgentTask("test query");
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(0, task.getCurrentRound());
    }

    @Test
    void setStatus_changesState() {
        AgentTask task = new AgentTask("query");
        task.setStatus(TaskStatus.RUNNING);
        assertEquals(TaskStatus.RUNNING, task.getStatus());
    }

    @Test
    void incrementRound_advancesCounter() {
        AgentTask task = new AgentTask("query");
        assertEquals(0, task.getCurrentRound());
        task.incrementRound();
        assertEquals(1, task.getCurrentRound());
        task.incrementRound();
        assertEquals(2, task.getCurrentRound());
    }

    @Test
    void isMaxRoundsReached_respectsLimit() {
        AgentTask task = new AgentTask("task-1", "query", 3);
        assertFalse(task.isMaxRoundsReached());
        task.incrementRound();
        task.incrementRound();
        task.incrementRound();
        assertTrue(task.isMaxRoundsReached());
    }

    @Test
    void addSubTask_setsParentId() {
        AgentTask parent = new AgentTask("parent");
        AgentTask child = new AgentTask("child");
        parent.addSubTask(child);
        assertEquals(parent.getId(), child.getParentTaskId());
        assertTrue(parent.hasSubTasks());
        assertEquals(1, parent.getSubTasks().size());
    }

    @Test
    void addStep_recordsStep() {
        AgentTask task = new AgentTask("query");
        ReActStep step = ReActStep.finish("done", 0);
        task.addStep(step);
        assertEquals(1, task.getSteps().size());
        assertEquals(step, task.getLastStep());
    }

    @Test
    void getLastStep_emptySteps_returnsNull() {
        AgentTask task = new AgentTask("query");
        assertNull(task.getLastStep());
    }

    @Test
    void isRoot_noParent_returnsTrue() {
        AgentTask task = new AgentTask("query");
        assertTrue(task.isRoot());
    }

    @Test
    void taskStatus_terminalStates() {
        assertTrue(TaskStatus.COMPLETED.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
        assertTrue(TaskStatus.CANCELLED.isTerminal());
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.RUNNING.isTerminal());
    }

    @Test
    void setResult_storesValue() {
        AgentTask task = new AgentTask("query");
        task.setResult("some result");
        assertEquals("some result", task.getResult());
    }
}
