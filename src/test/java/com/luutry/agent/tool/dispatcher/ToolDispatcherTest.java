package com.luutry.agent.tool.dispatcher;

import com.luutry.agent.security.auth.FunctionPermissionChecker;
import com.luutry.agent.security.auth.PermissionDeniedException;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.executor.ToolExecutor;
import com.luutry.agent.tool.model.ToolDefinition;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import com.luutry.agent.tool.registry.DefaultToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ToolDispatcher
 */
@DisplayName("ToolDispatcher Tests")
class ToolDispatcherTest {

    private DefaultToolRegistry registry;
    private ToolExecutor executor;
    private FunctionPermissionChecker permissionChecker;
    private ToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        executor = mock(ToolExecutor.class);
        permissionChecker = mock(FunctionPermissionChecker.class);
        dispatcher = new ToolDispatcher(registry, executor, permissionChecker);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Nested
    @DisplayName("Successful dispatch")
    class SuccessTests {

        @Test
        @DisplayName("Should dispatch tool call successfully")
        void testSuccessfulDispatch() {
            AgentTool tool = createDummyTool("test_tool");
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("name", "string, optional"))
                    .build();
            registry.register(tool, def);

            ToolResult expectedResult = ToolResult.success(Map.of("data", "value"));
            when(executor.execute(eq(tool), any(ToolRequest.class))).thenReturn(expectedResult);

            ToolRequest request = ToolRequest.builder()
                    .toolName("test_tool")
                    .parameters(Map.of("name", "test"))
                    .build();

            ToolResult result = dispatcher.dispatch(request);

            assertTrue(result.isSuccess());
            assertEquals("value", result.getData().get("data"));
        }

        @Test
        @DisplayName("Should skip permission check when no user context")
        void testNoUserContextSkipsPermission() {
            AgentTool tool = createDummyTool("test_tool");
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .requiredPermissions(Set.of("employee:view"))
                    .build();
            registry.register(tool, def);

            when(executor.execute(eq(tool), any())).thenReturn(ToolResult.success(Map.of()));

            ToolResult result = dispatcher.dispatch(ToolRequest.builder()
                    .toolName("test_tool").build());

            assertTrue(result.isSuccess());
            verifyNoInteractions(permissionChecker);
        }
    }

    @Nested
    @DisplayName("Failure scenarios")
    class FailureTests {

        @Test
        @DisplayName("Should fail for null request")
        void testNullRequest() {
            ToolResult result = dispatcher.dispatch(null);
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should fail for null tool name")
        void testNullToolName() {
            ToolResult result = dispatcher.dispatch(ToolRequest.builder().build());
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("must not be null"));
        }

        @Test
        @DisplayName("Should fail for unregistered tool")
        void testUnregisteredTool() {
            ToolResult result = dispatcher.dispatch(ToolRequest.builder()
                    .toolName("non_existent").build());
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("not registered"));
        }

        @Test
        @DisplayName("Should fail for input validation errors")
        void testInputValidationFailure() {
            AgentTool tool = createDummyTool("test_tool");
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("employeeId", "string, required"))
                    .build();
            registry.register(tool, def);

            ToolResult result = dispatcher.dispatch(ToolRequest.builder()
                    .toolName("test_tool")
                    .parameters(Map.of())
                    .build());

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("validation failed"));
        }

        @Test
        @DisplayName("Should throw PermissionDeniedException when user lacks permission")
        void testPermissionDenied() {
            UserContextHolder.set(UserContext.builder()
                    .userId("U001")
                    .permissions(Set.of())
                    .build());

            AgentTool tool = createDummyTool("test_tool");
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .requiredPermissions(Set.of("employee:view"))
                    .build();
            registry.register(tool, def);

            doThrow(new PermissionDeniedException("Missing permission"))
                    .when(permissionChecker).checkPermission("employee:view");

            assertThrows(PermissionDeniedException.class, () ->
                    dispatcher.dispatch(ToolRequest.builder()
                            .toolName("test_tool")
                            .parameters(Map.of("employeeId", "E001"))
                            .build()));
        }
    }

    private AgentTool createDummyTool(String name) {
        return new AgentTool() {
            @Override
            public String getToolName() {
                return name;
            }

            @Override
            public ToolResult execute(ToolRequest request) {
                return ToolResult.success(Map.of("result", "ok"));
            }
        };
    }
}
