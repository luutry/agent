package com.luutry.agent.tool.registry;

import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolDefinition;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultToolRegistry
 */
@DisplayName("DefaultToolRegistry Tests")
class DefaultToolRegistryTest {

    private DefaultToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
    }

    @Nested
    @DisplayName("Registration tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register tool and retrieve it")
        void testRegisterAndGet() {
            AgentTool tool = createDummyTool("test_tool");
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .description("A test tool")
                    .requiredPermissions(Set.of("test:view"))
                    .build();

            registry.register(tool, def);

            assertTrue(registry.hasTool("test_tool"));
            assertTrue(registry.getTool("test_tool").isPresent());
            assertTrue(registry.getDefinition("test_tool").isPresent());
            assertEquals("A test tool", registry.getDefinition("test_tool").get().getDescription());
        }

        @Test
        @DisplayName("Should overwrite existing tool on duplicate registration")
        void testDuplicateRegistration() {
            AgentTool tool1 = createDummyTool("test_tool");
            AgentTool tool2 = createDummyTool("test_tool");
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .description("Updated description")
                    .build();

            registry.register(tool1, ToolDefinition.builder().name("test_tool").description("Old").build());
            registry.register(tool2, def);

            assertEquals("Updated description", registry.getDefinition("test_tool").get().getDescription());
        }

        @Test
        @DisplayName("Should throw on null tool registration")
        void testRegisterNullTool() {
            ToolDefinition def = ToolDefinition.builder().name("test").build();
            assertThrows(IllegalArgumentException.class, () -> registry.register(null, def));
        }

        @Test
        @DisplayName("Should throw on null definition registration")
        void testRegisterNullDefinition() {
            AgentTool tool = createDummyTool("test");
            assertThrows(IllegalArgumentException.class, () -> registry.register(tool, null));
        }
    }

    @Nested
    @DisplayName("Query tests")
    class QueryTests {

        @Test
        @DisplayName("Should return empty for non-existent tool")
        void testGetNonExistent() {
            assertTrue(registry.getTool("non_existent").isEmpty());
            assertTrue(registry.getDefinition("non_existent").isEmpty());
            assertFalse(registry.hasTool("non_existent"));
        }

        @Test
        @DisplayName("Should return all registered definitions")
        void testGetAllDefinitions() {
            registry.register(createDummyTool("tool1"),
                    ToolDefinition.builder().name("tool1").description("Tool 1").build());
            registry.register(createDummyTool("tool2"),
                    ToolDefinition.builder().name("tool2").description("Tool 2").build());

            assertEquals(2, registry.getAllDefinitions().size());
        }

        @Test
        @DisplayName("Should unregister tool")
        void testUnregister() {
            registry.register(createDummyTool("test_tool"),
                    ToolDefinition.builder().name("test_tool").build());
            assertTrue(registry.hasTool("test_tool"));

            registry.unregister("test_tool");

            assertFalse(registry.hasTool("test_tool"));
            assertTrue(registry.getTool("test_tool").isEmpty());
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
