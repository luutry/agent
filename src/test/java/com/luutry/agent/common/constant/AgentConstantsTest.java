package com.luutry.agent.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentConstants
 */
@DisplayName("AgentConstants Tests")
class AgentConstantsTest {

    @Test
    @DisplayName("MAX_TOOL_ROUNDS should be positive")
    void testMaxToolRounds() {
        assertTrue(AgentConstants.MAX_TOOL_ROUNDS > 0);
        assertEquals(10, AgentConstants.MAX_TOOL_ROUNDS);
    }

    @Test
    @DisplayName("MAX_TOKEN_BUDGET should be positive and reasonable")
    void testMaxTokenBudget() {
        assertTrue(AgentConstants.MAX_TOKEN_BUDGET > 0);
        assertEquals(4096, AgentConstants.MAX_TOKEN_BUDGET);
    }

    @Test
    @DisplayName("DEFAULT_TOOL_TIMEOUT_MS should be positive")
    void testDefaultToolTimeout() {
        assertTrue(AgentConstants.DEFAULT_TOOL_TIMEOUT_MS > 0);
        assertEquals(30_000L, AgentConstants.DEFAULT_TOOL_TIMEOUT_MS);
    }

    @Test
    @DisplayName("DEFAULT_TOOL_RETRY_COUNT should be non-negative")
    void testDefaultRetryCount() {
        assertTrue(AgentConstants.DEFAULT_TOOL_RETRY_COUNT >= 0);
    }

    @Test
    @DisplayName("MAX_QUERY_LENGTH should be positive")
    void testMaxQueryLength() {
        assertTrue(AgentConstants.MAX_QUERY_LENGTH > 0);
        assertEquals(2000, AgentConstants.MAX_QUERY_LENGTH);
    }

    @Test
    @DisplayName("Page size constants should be valid")
    void testPageSizeConstants() {
        assertTrue(AgentConstants.DEFAULT_PAGE_SIZE > 0);
        assertTrue(AgentConstants.MAX_PAGE_SIZE >= AgentConstants.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("Should not be instantiable")
    void testCannotInstantiate() {
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            var constructor = AgentConstants.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }
}
