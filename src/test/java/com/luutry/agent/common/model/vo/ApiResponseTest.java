package com.luutry.agent.common.model.vo;

import com.luutry.agent.common.exception.ErrorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiResponse.
 */
class ApiResponseTest {

    @Test
    void success_withData_containsPayload() {
        ApiResponse<String> response = ApiResponse.success("hello");
        assertTrue(response.isSuccess());
        assertEquals(ErrorCodes.SUCCESS.getCode(), response.getCode());
        assertEquals("hello", response.getData());
    }

    @Test
    void success_noData_containsNullPayload() {
        ApiResponse<Void> response = ApiResponse.success();
        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void fail_withErrorCode_usesCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCodes.PARAM_ERROR);
        assertFalse(response.isSuccess());
        assertEquals(ErrorCodes.PARAM_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCodes.PARAM_ERROR.getMessage(), response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void fail_withCodeAndMessage_usesProvidedValues() {
        ApiResponse<Void> response = ApiResponse.fail("999", "custom error");
        assertFalse(response.isSuccess());
        assertEquals("999", response.getCode());
        assertEquals("custom error", response.getMessage());
    }

    @Test
    void badRequest_usesParamErrorCode() {
        ApiResponse<Void> response = ApiResponse.badRequest("missing field");
        assertFalse(response.isSuccess());
        assertEquals(ErrorCodes.PARAM_ERROR.getCode(), response.getCode());
        assertEquals("missing field", response.getMessage());
    }

    @Test
    void error_usesSystemErrorCode() {
        ApiResponse<Void> response = ApiResponse.error("internal failure");
        assertFalse(response.isSuccess());
        assertEquals(ErrorCodes.SYSTEM_ERROR.getCode(), response.getCode());
        assertEquals("internal failure", response.getMessage());
    }

    @Test
    void timestamp_isSet() {
        long before = System.currentTimeMillis();
        ApiResponse<Void> response = ApiResponse.success();
        long after = System.currentTimeMillis();
        assertTrue(response.getTimestamp() >= before && response.getTimestamp() <= after);
    }
}
