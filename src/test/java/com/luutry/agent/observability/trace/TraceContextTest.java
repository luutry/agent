package com.luutry.agent.observability.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TraceContext, TraceContextHolder, TraceIdGenerator
 */
@DisplayName("Trace Context Tests")
class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    // ===== TraceContext Tests =====

    @Test
    @DisplayName("TraceContext should initialize with traceId and startTime")
    void testTraceContextInit() {
        TraceContext ctx = new TraceContext("trace-001");

        assertEquals("trace-001", ctx.getTraceId());
        assertNull(ctx.getSpanId());
        assertNull(ctx.getStageName());
        assertTrue(ctx.getStartTimeMs() > 0);
        assertTrue(ctx.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("TraceContext should initialize with traceId, spanId, and stageName")
    void testTraceContextFullInit() {
        TraceContext ctx = new TraceContext("trace-001", "span-001", "planner");

        assertEquals("trace-001", ctx.getTraceId());
        assertEquals("span-001", ctx.getSpanId());
        assertEquals("planner", ctx.getStageName());
    }

    @Test
    @DisplayName("putAttribute and getAttribute should work correctly")
    void testAttributes() {
        TraceContext ctx = new TraceContext("trace-001");

        ctx.putAttribute("tokenCount", 128);
        ctx.putAttribute("toolName", "employee_search");

        assertEquals(128, ctx.getAttribute("tokenCount"));
        assertEquals("employee_search", ctx.getAttribute("toolName"));
        assertNull(ctx.getAttribute("nonExistent"));
    }

    @Test
    @DisplayName("getElapsedMs should return non-negative value")
    void testGetElapsedMs() throws InterruptedException {
        TraceContext ctx = new TraceContext("trace-001");
        Thread.sleep(10);

        assertTrue(ctx.getElapsedMs() >= 10);
    }

    // ===== TraceContextHolder Tests =====

    @Test
    @DisplayName("TraceContextHolder should return null when not set")
    void testHolderReturnsNullWhenNotSet() {
        assertNull(TraceContextHolder.get());
    }

    @Test
    @DisplayName("TraceContextHolder should set and get context")
    void testHolderSetAndGet() {
        TraceContext ctx = new TraceContext("trace-001");
        TraceContextHolder.set(ctx);

        assertNotNull(TraceContextHolder.get());
        assertEquals("trace-001", TraceContextHolder.get().getTraceId());
    }

    @Test
    @DisplayName("TraceContextHolder should clear context")
    void testHolderClear() {
        TraceContextHolder.set(new TraceContext("trace-001"));
        assertNotNull(TraceContextHolder.get());

        TraceContextHolder.clear();
        assertNull(TraceContextHolder.get());
    }

    // ===== TraceIdGenerator Tests =====

    @Test
    @DisplayName("generateTraceId should return 32-char hex string")
    void testGenerateTraceId() {
        String traceId = TraceIdGenerator.generateTraceId();

        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertFalse(traceId.contains("-"));
        assertTrue(traceId.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("generateTraceId should return unique values")
    void testGenerateTraceIdUniqueness() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(TraceIdGenerator.generateTraceId());
        }
        assertEquals(100, ids.size(), "All generated trace IDs should be unique");
    }

    @Test
    @DisplayName("generateSpanId should return 32-char hex string")
    void testGenerateSpanId() {
        String spanId = TraceIdGenerator.generateSpanId();

        assertNotNull(spanId);
        assertEquals(32, spanId.length());
        assertFalse(spanId.contains("-"));
    }

    @Test
    @DisplayName("generateSpanId should return unique values")
    void testGenerateSpanIdUniqueness() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(TraceIdGenerator.generateSpanId());
        }
        assertEquals(100, ids.size(), "All generated span IDs should be unique");
    }
}
