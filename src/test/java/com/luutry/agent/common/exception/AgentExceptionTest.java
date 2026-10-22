package com.luutry.agent.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentException
 */
@DisplayName("AgentException Tests")
class AgentExceptionTest {

    @Test
    @DisplayName("Should create exception with ErrorCode")
    void testCreateWithErrorCode() {
        AgentException exception = new AgentException(ErrorCodes.PARAM_ERROR);

        assertEquals(ErrorCodes.PARAM_ERROR, exception.getErrorCode());
        assertEquals("Invalid parameter", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create exception with ErrorCode and custom message")
    void testCreateWithCustomMessage() {
        AgentException exception = new AgentException(ErrorCodes.PARAM_ERROR, "Name is required");

        assertEquals(ErrorCodes.PARAM_ERROR, exception.getErrorCode());
        assertEquals("Name is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should create exception with ErrorCode and cause")
    void testCreateWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        AgentException exception = new AgentException(ErrorCodes.SYSTEM_ERROR, cause);

        assertEquals(ErrorCodes.SYSTEM_ERROR, exception.getErrorCode());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should create exception with ErrorCode, custom message and cause")
    void testCreateWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        AgentException exception = new AgentException(ErrorCodes.TOOL_EXECUTION_FAILED, "Tool X failed", cause);

        assertEquals(ErrorCodes.TOOL_EXECUTION_FAILED, exception.getErrorCode());
        assertEquals("Tool X failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("AgentException should be RuntimeException")
    void testIsRuntimeException() {
        AgentException exception = new AgentException(ErrorCodes.SYSTEM_ERROR);
        assertInstanceOf(RuntimeException.class, exception);
    }
}
