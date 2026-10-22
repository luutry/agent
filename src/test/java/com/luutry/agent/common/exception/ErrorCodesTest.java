package com.luutry.agent.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorCodes enum
 */
@DisplayName("ErrorCodes Tests")
class ErrorCodesTest {

    @Test
    @DisplayName("All error codes should have non-null code and message")
    void testAllCodesHaveFields() {
        for (ErrorCodes errorCode : ErrorCodes.values()) {
            assertNotNull(errorCode.getCode(), "Code should not be null for " + errorCode.name());
            assertNotNull(errorCode.getMessage(), "Message should not be null for " + errorCode.name());
            assertNotNull(errorCode.getHttpStatus(), "HttpStatus should not be null for " + errorCode.name());
        }
    }

    @Test
    @DisplayName("SUCCESS code should be 000000")
    void testSuccessCode() {
        assertEquals("000000", ErrorCodes.SUCCESS.getCode());
        assertEquals("Success", ErrorCodes.SUCCESS.getMessage());
        assertEquals(HttpStatus.OK, ErrorCodes.SUCCESS.getHttpStatus());
    }

    @Test
    @DisplayName("Error codes should have correct HTTP status mapping")
    void testHttpStatusMapping() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCodes.PARAM_ERROR.getHttpStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCodes.AUTH_FAILED.getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN, ErrorCodes.PERMISSION_DENIED.getHttpStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCodes.DATA_NOT_FOUND.getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_ERROR.getHttpStatus());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ErrorCodes.TOOL_TIMEOUT.getHttpStatus());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMIT_EXCEEDED.getHttpStatus());
    }

    @Test
    @DisplayName("Error codes should implement ErrorCode interface")
    void testImplementsErrorCodeInterface() {
        for (ErrorCodes errorCode : ErrorCodes.values()) {
            assertInstanceOf(ErrorCode.class, errorCode);
        }
    }

    @Test
    @DisplayName("Should have unique code values")
    void testUniqueCodes() {
        ErrorCodes[] values = ErrorCodes.values();
        long distinctCount = java.util.Arrays.stream(values)
                .map(ErrorCodes::getCode)
                .distinct()
                .count();
        assertEquals(values.length, distinctCount, "All error codes should be unique");
    }
}
