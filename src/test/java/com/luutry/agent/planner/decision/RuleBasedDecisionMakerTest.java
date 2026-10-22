package com.luutry.agent.planner.decision;

import com.luutry.agent.planner.model.AgentTask;
import com.luutry.agent.planner.model.PlannerDecision;
import com.luutry.agent.planner.model.ReActStep;
import com.luutry.agent.preprocessor.model.QueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleBasedDecisionMaker.
 */
class RuleBasedDecisionMakerTest {

    private RuleBasedDecisionMaker decisionMaker;

    @BeforeEach
    void setUp() {
        decisionMaker = new RuleBasedDecisionMaker();
    }

    @Test
    void decide_firstStep_toolIntent_callsTool() {
        AgentTask task = new AgentTask("查看员工绩效");
        assertEquals(PlannerDecision.CALL_TOOL, decisionMaker.decide(task, QueryIntent.TOOL));
    }

    @Test
    void decide_firstStep_ragIntent_callsRag() {
        AgentTask task = new AgentTask("离职制度");
        assertEquals(PlannerDecision.CALL_RAG, decisionMaker.decide(task, QueryIntent.RAG));
    }

    @Test
    void decide_firstStep_mixedIntent_callsToolFirst() {
        AgentTask task = new AgentTask("查看绩效并了解制度");
        assertEquals(PlannerDecision.CALL_TOOL, decisionMaker.decide(task, QueryIntent.MIXED));
    }

    @Test
    void decide_firstStep_chitchatIntent_directAnswer() {
        AgentTask task = new AgentTask("你好");
        assertEquals(PlannerDecision.DIRECT_ANSWER, decisionMaker.decide(task, QueryIntent.CHITCHAT));
    }

    @Test
    void decide_firstStep_nullIntent_directAnswer() {
        AgentTask task = new AgentTask("random");
        assertEquals(PlannerDecision.DIRECT_ANSWER, decisionMaker.decide(task, null));
    }

    @Test
    void decide_maxRoundsReached_forcesFinish() {
        AgentTask task = new AgentTask("task-1", "query", 2);
        task.incrementRound();
        task.incrementRound();
        assertTrue(task.isMaxRoundsReached());
        assertEquals(PlannerDecision.FINISH, decisionMaker.decide(task, QueryIntent.TOOL));
    }

    @Test
    void decide_mixedIntent_secondStep_callsRag() {
        AgentTask task = new AgentTask("查看绩效并了解制度");
        // Simulate first step: CALL_TOOL with observation
        ReActStep firstStep = ReActStep.callTool("thought", "tool", 0);
        firstStep.setObservation("绩效数据");
        task.addStep(firstStep);
        task.incrementRound();

        assertEquals(PlannerDecision.CALL_RAG, decisionMaker.decide(task, QueryIntent.MIXED));
    }

    @Test
    void decide_afterDirectAnswer_finishes() {
        AgentTask task = new AgentTask("你好");
        ReActStep directStep = ReActStep.directAnswer("chitchat", 0);
        task.addStep(directStep);
        task.incrementRound();

        assertEquals(PlannerDecision.FINISH, decisionMaker.decide(task, QueryIntent.CHITCHAT));
    }

    @Test
    void decide_afterToolWithObservation_finishes() {
        AgentTask task = new AgentTask("查看绩效");
        ReActStep toolStep = ReActStep.callTool("thought", "search", 0);
        toolStep.setObservation("result data");
        task.addStep(toolStep);
        task.incrementRound();

        assertEquals(PlannerDecision.FINISH, decisionMaker.decide(task, QueryIntent.TOOL));
    }

    @Test
    void decide_afterToolWithEmptyObservation_fallsToRag() {
        AgentTask task = new AgentTask("查看绩效");
        ReActStep toolStep = ReActStep.callTool("thought", "search", 0);
        toolStep.setObservation("");
        task.addStep(toolStep);
        task.incrementRound();

        assertEquals(PlannerDecision.CALL_RAG, decisionMaker.decide(task, QueryIntent.TOOL));
    }
}
