package com.luutry.agent.tool.schema;

import com.luutry.agent.tool.model.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolInputValidator
 */
@DisplayName("ToolInputValidator Tests")
class ToolInputValidatorTest {

    @Nested
    @DisplayName("Required parameter validation")
    class RequiredParamTests {

        @Test
        @DisplayName("Should pass when all required params present")
        void testAllRequiredPresent() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("employeeId", "string, required"))
                    .build();
            Map<String, Object> params = Map.of("employeeId", "E001");

            List<String> errors = ToolInputValidator.validate(def, params);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should fail when required param is missing")
        void testMissingRequired() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("employeeId", "string, required"))
                    .build();

            List<String> errors = ToolInputValidator.validate(def, Map.of());

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("Missing required parameter"));
        }

        @Test
        @DisplayName("Should pass when optional param is missing")
        void testOptionalMissing() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("cycle", "string, optional"))
                    .build();

            List<String> errors = ToolInputValidator.validate(def, Map.of());

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Type validation")
    class TypeValidationTests {

        @Test
        @DisplayName("Should pass when string type matches")
        void testStringTypeMatch() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("name", "string, required"))
                    .build();
            Map<String, Object> params = Map.of("name", "test");

            List<String> errors = ToolInputValidator.validate(def, params);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should fail when string type gets integer")
        void testStringTypeMismatch() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("name", "string, required"))
                    .build();
            Map<String, Object> params = Map.of("name", 123);

            List<String> errors = ToolInputValidator.validate(def, params);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("expected type 'string'"));
        }

        @Test
        @DisplayName("Should pass when int type matches Integer")
        void testIntTypeMatch() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("count", "int, required"))
                    .build();
            Map<String, Object> params = Map.of("count", 5);

            List<String> errors = ToolInputValidator.validate(def, params);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should pass when number type matches Double")
        void testNumberTypeMatch() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("score", "number, required"))
                    .build();
            Map<String, Object> params = Map.of("score", 95.5);

            List<String> errors = ToolInputValidator.validate(def, params);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should pass when boolean type matches")
        void testBooleanTypeMatch() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("active", "boolean, required"))
                    .build();
            Map<String, Object> params = Map.of("active", true);

            List<String> errors = ToolInputValidator.validate(def, params);

            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return error for null definition")
        void testNullDefinition() {
            List<String> errors = ToolInputValidator.validate(null, Map.of());

            assertEquals(1, errors.size());
            assertEquals("Tool definition is null", errors.get(0));
        }

        @Test
        @DisplayName("Should pass for empty schema")
        void testEmptySchema() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of())
                    .build();

            List<String> errors = ToolInputValidator.validate(def, Map.of("anything", "value"));

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should pass for null parameters with no required fields")
        void testNullParamsNoRequired() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of("name", "string, optional"))
                    .build();

            List<String> errors = ToolInputValidator.validate(def, null);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should collect multiple errors")
        void testMultipleErrors() {
            ToolDefinition def = ToolDefinition.builder()
                    .name("test_tool")
                    .inputSchema(Map.of(
                            "field1", "string, required",
                            "field2", "int, required"
                    ))
                    .build();

            List<String> errors = ToolInputValidator.validate(def, Map.of());

            assertEquals(2, errors.size());
        }
    }
}
