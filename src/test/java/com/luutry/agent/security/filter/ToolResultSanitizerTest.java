package com.luutry.agent.security.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolResultSanitizer.
 */
@DisplayName("ToolResultSanitizer Tests")
class ToolResultSanitizerTest {

    private final ToolResultSanitizer sanitizer = new ToolResultSanitizer();

    @Test
    @DisplayName("should mask phone and email before prompt")
    void shouldMaskPhoneAndEmail() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone", "13812345678");
        data.put("email", "zhangsan@company.com");

        Map<String, Object> sanitized = sanitizer.sanitizeForLlm(data);

        assertTrue(sanitized.get("phone").toString().contains("****"), "手机号应脱敏");
        assertFalse(sanitized.get("email").toString().contains("zhangsan"), "邮箱应脱敏");
    }

    @Test
    @DisplayName("should mask employee id before prompt")
    void shouldMaskEmployeeId() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeId", "E001");

        Map<String, Object> sanitized = sanitizer.sanitizeForLlm(data);

        assertEquals("E***", sanitized.get("employeeId"), "工号应保首字符脱敏（R3）");
    }

    @Test
    @DisplayName("should remove employee-level performance detail")
    void shouldRemovePerformanceDetail() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("employeeId", "E001");
        record.put("cycle", "2025-H2");
        record.put("score", 92);
        record.put("grade", "A");
        record.put("comment", "excellent");
        record.put("appealed", false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", List.of(record));

        Map<String, Object> sanitized = sanitizer.sanitizeForLlm(data);
        Map<?, ?> safeRecord = (Map<?, ?>) ((List<?>) sanitized.get("records")).get(0);

        assertFalse(safeRecord.containsKey("score"), "绩效分数不得进入 Prompt（R3.1）");
        assertFalse(safeRecord.containsKey("grade"), "绩效等级不得进入 Prompt（R3.1）");
        assertFalse(safeRecord.containsKey("comment"), "绩效评语不得进入 Prompt（R3.1）");
        assertFalse(safeRecord.containsKey("appealed"), "绩效申诉不得进入 Prompt（R3.1）");
        assertEquals("2025-H2", safeRecord.get("cycle"), "非敏感元数据可保留");
    }

    @Test
    @DisplayName("should keep aggregated performance distribution")
    void shouldKeepAggregateDistribution() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("performanceDistribution", Map.of("A", 3L, "B", 2L));

        Map<String, Object> sanitized = sanitizer.sanitizeForLlm(data);

        assertTrue(sanitized.containsKey("performanceDistribution"),
                "聚合绩效分布按 R3.1 允许保留");
    }

    @Test
    @DisplayName("should not mutate the original tool result")
    void shouldNotMutateInput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone", "13812345678");

        sanitizer.sanitizeForLlm(data);

        assertEquals("13812345678", data.get("phone"), "脱敏必须返回副本，不能改原始真相源");
    }

    @Test
    @DisplayName("should handle null and empty data")
    void shouldHandleNullAndEmpty() {
        assertTrue(sanitizer.sanitizeForLlm(null).isEmpty());
        assertTrue(sanitizer.sanitizeForLlm(Map.of()).isEmpty());
    }
}
