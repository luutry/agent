package com.luutry.agent.observability.trace;

import com.luutry.agent.observability.log.AgentLog;
import com.luutry.agent.observability.log.InMemoryAgentLogCollector;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentTraceAspect
 */
@DisplayName("AgentTraceAspect Tests")
class AgentTraceAspectTest {

    private InMemoryAgentLogCollector logCollector;
    private AgentTraceAspect aspect;

    @BeforeEach
    void setUp() {
        logCollector = new InMemoryAgentLogCollector();
        aspect = new AgentTraceAspect(logCollector);
    }

    // ===== Success scenario =====

    @Nested
    @DisplayName("Successful method execution")
    class SuccessTests {

        @Test
        @DisplayName("Should collect log with SUCCESS status when method returns normally")
        void testSuccessfulExecution() throws Throwable {
            // Arrange
            ProceedingJoinPoint joinPoint = mockJoinPoint("TestService", "doWork", new Object[]{"arg1"});
            when(joinPoint.proceed()).thenReturn("result");

            AgentTrace annotation = mockAnnotation("test_stage");

            // Act
            Object result = aspect.traceMethod(joinPoint, annotation);

            // Assert
            assertEquals("result", result);
            assertEquals(1, logCollector.getLogs().size());

            AgentLog log = logCollector.getLogs().get(0);
            assertEquals("test_stage", log.getStageName());
            assertEquals(AgentLog.STATUS_SUCCESS, log.getStatus());
            assertNull(log.getErrorMessage());
            assertNotNull(log.getTraceId());
            assertNotNull(log.getSpanId());
            assertTrue(log.getDurationMs() >= 0);
        }

        @Test
        @DisplayName("Should use method name as stage when annotation stage is empty")
        void testEmptyStageUsesMethodName() throws Throwable {
            ProceedingJoinPoint joinPoint = mockJoinPoint("TestService", "doWork", new Object[]{});
            when(joinPoint.proceed()).thenReturn("ok");

            AgentTrace annotation = mockAnnotation("");

            aspect.traceMethod(joinPoint, annotation);

            AgentLog log = logCollector.getLogs().get(0);
            // mock classForName falls back to AgentTraceAspectTest when class not found
            assertEquals("AgentTraceAspectTest.doWork", log.getStageName());
        }

        @Test
        @DisplayName("Should use traceId from TraceContextHolder when available")
        void testUseExistingTraceContext() throws Throwable {
            TraceContext ctx = new TraceContext("existing-trace-id");
            TraceContextHolder.set(ctx);

            try {
                ProceedingJoinPoint joinPoint = mockJoinPoint("Svc", "method", new Object[]{});
                when(joinPoint.proceed()).thenReturn("ok");
                AgentTrace annotation = mockAnnotation("stage");

                aspect.traceMethod(joinPoint, annotation);

                AgentLog log = logCollector.getLogs().get(0);
                assertEquals("existing-trace-id", log.getTraceId());
            } finally {
                TraceContextHolder.clear();
            }
        }

        @Test
        @DisplayName("Should include input summary from method arguments")
        void testInputSummary() throws Throwable {
            ProceedingJoinPoint joinPoint = mockJoinPoint("Svc", "method", new Object[]{"hello", 42});
            when(joinPoint.proceed()).thenReturn("ok");
            AgentTrace annotation = mockAnnotation("stage");

            aspect.traceMethod(joinPoint, annotation);

            AgentLog log = logCollector.getLogs().get(0);
            assertEquals("hello, 42", log.getInputSummary());
        }

        @Test
        @DisplayName("Should mask PII in input and output summaries")
        void testSummaryMasked() throws Throwable {
            ProceedingJoinPoint joinPoint = mockJoinPoint("Svc", "method", new Object[]{"查 13812345678"});
            when(joinPoint.proceed()).thenReturn("机主 13812345678");

            aspect.traceMethod(joinPoint, mockAnnotation("stage"));

            AgentLog log = logCollector.getLogs().get(0);
            assertFalse(log.getInputSummary().contains("13812345678"), "入参摘要不得含原始手机号");
            assertTrue(log.getInputSummary().contains("138****5678"));
            assertFalse(log.getOutputSummary().contains("13812345678"), "出参摘要不得含原始手机号");
        }
    }

    // ===== Failure scenario =====

    @Nested
    @DisplayName("Failed method execution")
    class FailureTests {

        @Test
        @DisplayName("Should collect log with FAILED status when method throws")
        void testFailedExecution() throws Throwable {
            ProceedingJoinPoint joinPoint = mockJoinPoint("TestService", "fail", new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Something went wrong"));

            AgentTrace annotation = mockAnnotation("fail_stage");

            // Act & Assert
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> aspect.traceMethod(joinPoint, annotation));
            assertEquals("Something went wrong", thrown.getMessage());

            // Verify log was collected
            assertEquals(1, logCollector.getLogs().size());
            AgentLog log = logCollector.getLogs().get(0);
            assertEquals(AgentLog.STATUS_FAILED, log.getStatus());
            assertEquals("Something went wrong", log.getErrorMessage());
            assertEquals("fail_stage", log.getStageName());
        }

        @Test
        @DisplayName("Should re-throw the original exception after logging")
        void testRethrowOriginalException() throws Throwable {
            ProceedingJoinPoint joinPoint = mockJoinPoint("Svc", "method", new Object[]{});
            IllegalStateException original = new IllegalStateException("bad state");
            when(joinPoint.proceed()).thenThrow(original);
            AgentTrace annotation = mockAnnotation("stage");

            Throwable thrown = assertThrows(IllegalStateException.class,
                    () -> aspect.traceMethod(joinPoint, annotation));
            assertSame(original, thrown);
        }
    }

    // ===== Helper methods =====

    private ProceedingJoinPoint mockJoinPoint(String className, String methodName, Object[] args) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringType()).thenReturn(classForName(className));
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    private Class<?> classForName(String simpleName) {
        try {
            return Class.forName("com.luutry.agent.observability.trace." + simpleName);
        } catch (ClassNotFoundException e) {
            // Return a proxy class for testing purposes
            return AgentTraceAspectTest.class;
        }
    }

    private AgentTrace mockAnnotation(String stage) {
        AgentTrace annotation = mock(AgentTrace.class);
        when(annotation.stage()).thenReturn(stage);
        return annotation;
    }
}
