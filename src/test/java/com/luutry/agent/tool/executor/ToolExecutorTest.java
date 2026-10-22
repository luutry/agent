package com.luutry.agent.tool.executor;

import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolExecutor
 */
@DisplayName("ToolExecutor Tests")
class ToolExecutorTest {

    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor();
    }

    @Nested
    @DisplayName("Successful execution")
    class SuccessTests {

        @Test
        @DisplayName("Should execute tool and return result")
        void testSuccessfulExecution() {
            AgentTool tool = createTool("test_tool",
                    ToolResult.success(Map.of("key", "value")));

            ToolResult result = executor.execute(tool, ToolRequest.builder()
                    .toolName("test_tool").build());

            assertTrue(result.isSuccess());
            assertEquals("value", result.getData().get("key"));
        }

        @Test
        @DisplayName("Should return fail for null tool")
        void testNullTool() {
            ToolResult result = executor.execute(null, ToolRequest.builder().build());
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should return fail for null request")
        void testNullRequest() {
            AgentTool tool = createTool("test_tool", ToolResult.success(Map.of()));
            ToolResult result = executor.execute(tool, null);
            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Retry mechanism")
    class RetryTests {

        @Test
        @DisplayName("Should retry on failure and eventually succeed")
        void testRetrySuccess() {
            AtomicInteger attempts = new AtomicInteger(0);
            AgentTool tool = new AgentTool() {
                @Override
                public String getToolName() {
                    return "retry_tool";
                }

                @Override
                public ToolResult execute(ToolRequest request) {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 2) {
                        throw new RuntimeException("Transient error");
                    }
                    return ToolResult.success(Map.of("attempt", attempt));
                }
            };

            ToolResult result = executor.execute(tool, ToolRequest.builder()
                    .toolName("retry_tool").build());

            assertTrue(result.isSuccess());
            assertEquals(2, attempts.get());
        }

        @Test
        @DisplayName("Should fail after exhausting all retries")
        void testRetryExhausted() {
            AgentTool tool = new AgentTool() {
                @Override
                public String getToolName() {
                    return "failing_tool";
                }

                @Override
                public ToolResult execute(ToolRequest request) {
                    throw new RuntimeException("Persistent error");
                }
            };

            ToolResult result = executor.execute(tool, ToolRequest.builder()
                    .toolName("failing_tool").build());

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("failed after"));
        }
    }

    @Nested
    @DisplayName("Circuit breaker")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should open circuit after consecutive failures")
        void testCircuitOpensAfterThreshold() {
            AgentTool failingTool = createTool("circuit_tool",
                    () -> { throw new RuntimeException("fail"); });

            // Trigger 3 failures (below threshold of 5)
            for (int i = 0; i < 3; i++) {
                executor.execute(failingTool, ToolRequest.builder()
                        .toolName("circuit_tool").build());
            }

            // Should still attempt execution (below threshold)
            AgentTool workingTool = createTool("circuit_tool",
                    () -> ToolResult.success(Map.of("ok", true)));
            ToolResult result = executor.execute(workingTool, ToolRequest.builder()
                    .toolName("circuit_tool").build());

            // After a success, failure count resets
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject execution when circuit is open")
        void testCircuitRejectsWhenOpen() {
            AgentTool failingTool = createTool("open_circuit_tool",
                    () -> { throw new RuntimeException("fail"); });

            // Trigger 6 failures (above threshold of 5)
            for (int i = 0; i < 6; i++) {
                executor.execute(failingTool, ToolRequest.builder()
                        .toolName("open_circuit_tool").build());
            }

            // Next call should be rejected by circuit breaker
            AgentTool workingTool = createTool("open_circuit_tool",
                    () -> ToolResult.success(Map.of("ok", true)));
            ToolResult result = executor.execute(workingTool, ToolRequest.builder()
                    .toolName("open_circuit_tool").build());

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("circuit breaker"));
        }
    }

    private AgentTool createTool(String name, ToolResult resultToReturn) {
        return new AgentTool() {
            @Override
            public String getToolName() {
                return name;
            }

            @Override
            public ToolResult execute(ToolRequest request) {
                return resultToReturn;
            }
        };
    }

    private AgentTool createTool(String name, ToolResultSupplier supplier) {
        return new AgentTool() {
            @Override
            public String getToolName() {
                return name;
            }

            @Override
            public ToolResult execute(ToolRequest request) {
                return supplier.get();
            }
        };
    }

    @FunctionalInterface
    interface ToolResultSupplier {
        ToolResult get();
    }
}
