package com.luutry.agent.observability.metric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentMetricType, AgentMetricRecorder, Slf4jMetricRecorder
 */
@DisplayName("Agent Metric Tests")
class AgentMetricTest {

    // ===== AgentMetricType Tests =====

    @Test
    @DisplayName("All metric types should have non-null code, description, and unit")
    void testAllMetricTypesHaveFields() {
        for (AgentMetricType type : AgentMetricType.values()) {
            assertNotNull(type.getCode(), "Code should not be null for " + type.name());
            assertNotNull(type.getDescription(), "Description should not be null for " + type.name());
            assertNotNull(type.getUnit(), "Unit should not be null for " + type.name());
        }
    }

    @Test
    @DisplayName("Should have exactly 9 metric types")
    void testMetricTypeCount() {
        assertEquals(9, AgentMetricType.values().length);
    }

    @Test
    @DisplayName("END_TO_END_RT should have 'ms' as unit")
    void testEndToEndRtUnit() {
        assertEquals("ms", AgentMetricType.END_TO_END_RT.getUnit());
    }

    @Test
    @DisplayName("TASK_COMPLETION_RATE should have 'ratio' as unit")
    void testTaskCompletionRateUnit() {
        assertEquals("ratio", AgentMetricType.TASK_COMPLETION_RATE.getUnit());
    }

    // ===== Slf4jMetricRecorder Tests =====

    @Test
    @DisplayName("record should not throw exception with valid input")
    void testRecordValidInput() {
        Slf4jMetricRecorder recorder = new Slf4jMetricRecorder();

        assertDoesNotThrow(() ->
                recorder.record(AgentMetricType.TOOL_CALL_ROUNDS, 3.0, Map.of("traceId", "t-001")));
    }

    @Test
    @DisplayName("record with null metric type should be silently ignored")
    void testRecordNullMetricType() {
        Slf4jMetricRecorder recorder = new Slf4jMetricRecorder();

        assertDoesNotThrow(() -> recorder.record(null, 1.0));
    }

    @Test
    @DisplayName("record without tags should use default method")
    void testRecordWithoutTags() {
        Slf4jMetricRecorder recorder = new Slf4jMetricRecorder();

        assertDoesNotThrow(() -> recorder.record(AgentMetricType.TOKEN_CONSUMED, 256.0));
    }
}
