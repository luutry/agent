package com.luutry.agent.tool.param;

import com.luutry.agent.repository.mock.JsonDepartmentMapper;
import com.luutry.agent.repository.mock.JsonEmployeeMapper;
import com.luutry.agent.repository.mock.JsonPerformanceRecordMapper;
import com.luutry.agent.tool.builtin.MockHrDataProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolParameterExtractor.
 */
@DisplayName("ToolParameterExtractor Tests")
class ToolParameterExtractorTest {

    private final ToolParameterExtractor extractor =
            new ToolParameterExtractor(createMockHrDataProvider());

    private static MockHrDataProvider createMockHrDataProvider() {
        JsonEmployeeMapper employeeMapper = new JsonEmployeeMapper();
        JsonDepartmentMapper departmentMapper = new JsonDepartmentMapper();
        JsonPerformanceRecordMapper performanceRecordMapper = new JsonPerformanceRecordMapper(employeeMapper);
        return new MockHrDataProvider(employeeMapper, departmentMapper, performanceRecordMapper);
    }

    @Test
    @DisplayName("should resolve employee id from unique name")
    void shouldResolveEmployeeIdFromName() {
        Map<String, Object> params = extractor.extract("看看张三的档案");

        assertEquals("E001", params.get("employeeId"));
        assertEquals("张三", params.get("name"));
    }

    @Test
    @DisplayName("should prefer explicit employee id over name")
    void shouldPreferExplicitEmployeeId() {
        Map<String, Object> params = extractor.extract("E006 和 张三 的绩效对比");

        assertEquals("E006", params.get("employeeId"), "显式工号应优先");
    }

    @Test
    @DisplayName("should resolve department id from department name")
    void shouldResolveDepartmentIdFromName() {
        Map<String, Object> params = extractor.extract("技术部本月离职率");

        assertEquals("DEPT_TECH", params.get("deptId"));
    }

    @Test
    @DisplayName("should resolve department id from explicit id")
    void shouldResolveDepartmentIdFromId() {
        Map<String, Object> params = extractor.extract("DEPT_FIN 的人员情况");

        assertEquals("DEPT_FIN", params.get("deptId"));
    }

    @Test
    @DisplayName("should not bind employee when multiple names match")
    void shouldNotBindAmbiguousEmployee() {
        Map<String, Object> params = extractor.extract("张三和李四的绩效");

        assertFalse(params.containsKey("employeeId"), "多员工歧义时不得静默绑定");
    }

    @Test
    @DisplayName("should not bind department when multiple departments match")
    void shouldNotBindAmbiguousDepartment() {
        Map<String, Object> params = extractor.extract("技术部和财务部的人数");

        assertFalse(params.containsKey("deptId"), "多部门歧义时不得静默绑定");
    }

    @Test
    @DisplayName("should extract performance cycle")
    void shouldExtractCycle() {
        Map<String, Object> params = extractor.extract("张三 2025-H2 的绩效");

        assertEquals("E001", params.get("employeeId"));
        assertEquals("2025-H2", params.get("cycle"));
    }

    @Test
    @DisplayName("should return empty params for blank query")
    void shouldReturnEmptyForBlank() {
        assertTrue(extractor.extract(null).isEmpty());
        assertTrue(extractor.extract("   ").isEmpty());
    }
}
