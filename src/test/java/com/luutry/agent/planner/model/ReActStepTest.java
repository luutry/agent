package com.luutry.agent.planner.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReActStep model.
 */
class ReActStepTest {

    @Test
    void constructor_setsFields() {
        ReActStep step = new ReActStep("thinking", PlannerDecision.CALL_TOOL, "employee_search", 0);
        assertEquals("thinking", step.getThought());
        assertEquals(PlannerDecision.CALL_TOOL, step.getActionType());
        assertEquals("employee_search", step.getActionDetail());
        assertEquals(0, step.getRoundIndex());
        assertTrue(step.getTimestampMs() > 0);
    }

    @Test
    void finish_createsFinishStep() {
        ReActStep step = ReActStep.finish("done", 3);
        assertEquals(PlannerDecision.FINISH, step.getActionType());
        assertEquals("", step.getActionDetail());
        assertEquals(3, step.getRoundIndex());
    }

    @Test
    void callTool_createsToolStep() {
        ReActStep step = ReActStep.callTool("need data", "team_metrics", 1);
        assertEquals(PlannerDecision.CALL_TOOL, step.getActionType());
        assertEquals("team_metrics", step.getActionDetail());
    }

    @Test
    void callRag_createsRagStep() {
        ReActStep step = ReActStep.callRag("need docs", "考勤制度", 2);
        assertEquals(PlannerDecision.CALL_RAG, step.getActionType());
        assertEquals("考勤制度", step.getActionDetail());
    }

    @Test
    void readMemory_createsMemoryStep() {
        ReActStep step = ReActStep.readMemory("recall", "偏好", 0);
        assertEquals(PlannerDecision.READ_MEMORY, step.getActionType());
    }

    @Test
    void directAnswer_createsDirectStep() {
        ReActStep step = ReActStep.directAnswer("chitchat", 0);
        assertEquals(PlannerDecision.DIRECT_ANSWER, step.getActionType());
    }

    @Test
    void observation_setAndGet() {
        ReActStep step = ReActStep.callTool("thought", "tool", 0);
        assertNull(step.getObservation());
        step.setObservation("result data");
        assertEquals("result data", step.getObservation());
    }

    @Test
    void toString_containsKeyInfo() {
        ReActStep step = ReActStep.callTool("thinking", "search", 1);
        step.setObservation("data");
        String str = step.toString();
        assertTrue(str.contains("round=1"));
        assertTrue(str.contains("CALL_TOOL"));
        assertTrue(str.contains("hasObservation=true"));
    }
}
