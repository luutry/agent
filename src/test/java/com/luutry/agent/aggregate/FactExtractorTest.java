package com.luutry.agent.aggregate;

import com.luutry.agent.aggregate.model.FactSet;
import com.luutry.agent.tool.model.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * FactExtractor 单元测试：验证从 ToolResult 结构化数据提取事实三元组。
 */
class FactExtractorTest {

    private final FactExtractor extractor = new FactExtractor();

    @Test
    void extract_nestedMetricsList_producesEntityFacts() {
        Map<String, Object> tech = new LinkedHashMap<>();
        tech.put("deptId", "DEPT_TECH");
        tech.put("deptName", "技术部");
        tech.put("turnoverRate", 0.08);
        tech.put("currentStaff", 15);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalDepartments", 1);
        data.put("metrics", List.of(tech));

        FactSet facts = extractor.extract(List.of(ToolResult.success(data)));

        assertTrue(facts.containsEntityValue("技术部", 0.08));
        assertTrue(facts.containsEntityValue("技术部", 15.0));
        assertTrue(facts.entities().contains("技术部"));
    }

    @Test
    void extract_skipsCompositeFields() {
        Map<String, Object> tech = new LinkedHashMap<>();
        tech.put("deptName", "技术部");
        tech.put("performanceDistribution", Map.of("A", 3L));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metrics", List.of(tech));

        FactSet facts = extractor.extract(List.of(ToolResult.success(data)));

        assertTrue(facts.byMetric("performanceDistribution").isEmpty());
    }

    /**
     * N2 回归：totalEmployees（明细记录数）属非业务计数字段，不得进入 FactSet，
     * 以免与 currentStaff/headcount 构成三个互斥"人数"而稀释校验判别力。
     */
    @Test
    void extract_excludesNonBusinessMetric() {
        Map<String, Object> tech = new LinkedHashMap<>();
        tech.put("deptName", "技术部");
        tech.put("currentStaff", 15);
        tech.put("headcount", 20);
        tech.put("totalEmployees", 5);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metrics", List.of(tech));

        FactSet facts = extractor.extract(List.of(ToolResult.success(data)));

        assertTrue(facts.byMetric("totalEmployees").isEmpty(), "totalEmployees 不应入 FactSet");
        assertFalse(facts.containsEntityValue("技术部", 5.0), "明细数 5 不得作为合法值放行");
        assertTrue(facts.containsEntityValue("技术部", 15.0));
    }

    @Test
    void extract_failedResult_ignored() {
        assertTrue(extractor.extract(List.of(ToolResult.fail("boom"))).isEmpty());
    }

    @Test
    void extract_null_returnsEmpty() {
        assertTrue(extractor.extract(null).isEmpty());
    }
}
