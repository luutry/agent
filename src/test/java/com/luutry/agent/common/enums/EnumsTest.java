package com.luutry.agent.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IntentType, TaskState, DataSource enums
 */
@DisplayName("Enum Tests")
class EnumsTest {

    // ===== IntentType Tests =====

    @Test
    @DisplayName("IntentType should have all expected values")
    void testIntentTypeValues() {
        assertEquals(4, IntentType.values().length);
        assertNotNull(IntentType.valueOf("RAG"));
        assertNotNull(IntentType.valueOf("TOOL"));
        assertNotNull(IntentType.valueOf("MIXED"));
        assertNotNull(IntentType.valueOf("CHAT"));
    }

    @Test
    @DisplayName("IntentType should have non-empty code and description")
    void testIntentTypeFields() {
        for (IntentType type : IntentType.values()) {
            assertFalse(type.getCode().isEmpty());
            assertFalse(type.getDescription().isEmpty());
        }
    }

    // ===== TaskState Tests =====

    @Test
    @DisplayName("TaskState should have all expected values")
    void testTaskStateValues() {
        assertEquals(5, TaskState.values().length);
        assertNotNull(TaskState.valueOf("PENDING"));
        assertNotNull(TaskState.valueOf("RUNNING"));
        assertNotNull(TaskState.valueOf("SUCCESS"));
        assertNotNull(TaskState.valueOf("FAILED"));
        assertNotNull(TaskState.valueOf("CANCELLED"));
    }

    // ===== DataSource Tests =====

    @Test
    @DisplayName("DataSource should have correct priority order")
    void testDataSourcePriority() {
        assertTrue(DataSource.TOOL.getPriority() < DataSource.RAG.getPriority());
        assertTrue(DataSource.RAG.getPriority() < DataSource.MEMORY.getPriority());
    }

    @Test
    @DisplayName("DataSource TOOL should have highest priority (lowest number)")
    void testDataSourceToolHighestPriority() {
        assertEquals(1, DataSource.TOOL.getPriority());
    }

    @Test
    @DisplayName("DataSource should have all expected values")
    void testDataSourceValues() {
        assertEquals(3, DataSource.values().length);
        assertNotNull(DataSource.valueOf("TOOL"));
        assertNotNull(DataSource.valueOf("RAG"));
        assertNotNull(DataSource.valueOf("MEMORY"));
    }
}
