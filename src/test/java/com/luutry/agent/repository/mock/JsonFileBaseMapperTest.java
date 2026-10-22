package com.luutry.agent.repository.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON 文件 Mapper 层单元测试：验证数据加载与权限范围过滤。
 */
@DisplayName("JsonFile Mapper Tests")
class JsonFileBaseMapperTest {

    private JsonEmployeeMapper employeeMapper;
    private JsonDepartmentMapper departmentMapper;
    private JsonPerformanceRecordMapper performanceRecordMapper;

    @BeforeEach
    void setUp() {
        employeeMapper = new JsonEmployeeMapper();
        departmentMapper = new JsonDepartmentMapper();
        performanceRecordMapper = new JsonPerformanceRecordMapper(employeeMapper);
    }

    @Nested
    @DisplayName("DepartmentMapper Tests")
    class DepartmentMapperTests {

        @Test
        void selectAll_returnsAllDepartments() {
            List<Map<String, Object>> all = departmentMapper.selectAll();
            assertEquals(3, all.size());
        }

        @Test
        void selectList_withFullScope_returnsAll() {
            List<Map<String, Object>> result = departmentMapper.selectList(
                    List.of("DEPT_TECH", "DEPT_HR", "DEPT_FIN"));
            assertEquals(3, result.size());
        }

        @Test
        void selectList_withPartialScope_filtersAtQueryLayer() {
            List<Map<String, Object>> result = departmentMapper.selectList(
                    List.of("DEPT_TECH"));
            assertEquals(1, result.size());
            assertEquals("技术部", result.get(0).get("deptName"));
        }

        @Test
        void selectList_withEmptyScope_returnsEmpty() {
            List<Map<String, Object>> result = departmentMapper.selectList(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        void selectList_withNullScope_returnsEmpty() {
            List<Map<String, Object>> result = departmentMapper.selectList(null);
            assertTrue(result.isEmpty());
        }

        @Test
        void selectById_existingDept_returnsIt() {
            Map<String, Object> dept = departmentMapper.selectById("DEPT_TECH");
            assertNotNull(dept);
            assertEquals("技术部", dept.get("deptName"));
        }

        @Test
        void selectById_nonExistent_returnsNull() {
            Map<String, Object> dept = departmentMapper.selectById("DEPT_FAKE");
            assertNull(dept);
        }

        @Test
        void selectByParentDeptId_returnsSubDepts() {
            // 当前数据无子部门，应返回空
            List<Map<String, Object>> subs = departmentMapper.selectByParentDeptId("DEPT_TECH");
            assertTrue(subs.isEmpty());
        }
    }

    @Nested
    @DisplayName("EmployeeMapper Tests")
    class EmployeeMapperTests {

        @Test
        void selectAll_returnsAllEmployees() {
            List<Map<String, Object>> all = employeeMapper.selectAll();
            assertEquals(12, all.size());
        }

        @Test
        void selectList_withTechScope_returnsOnlyTechEmployees() {
            List<Map<String, Object>> result = employeeMapper.selectList(List.of("DEPT_TECH"));
            assertEquals(5, result.size());
            assertTrue(result.stream().allMatch(e -> "DEPT_TECH".equals(e.get("deptId"))));
        }

        @Test
        void selectList_withHrScope_returnsOnlyHrEmployees() {
            List<Map<String, Object>> result = employeeMapper.selectList(List.of("DEPT_HR"));
            assertEquals(4, result.size());
        }

        @Test
        void selectById_existingEmployee_returnsIt() {
            Map<String, Object> emp = employeeMapper.selectById("E001");
            assertNotNull(emp);
            assertEquals("张三", emp.get("name"));
            assertEquals("DEPT_TECH", emp.get("deptId"));
        }

        @Test
        void selectByCondition_withDeptFilter() {
            Map<String, Object> conditions = Map.of("deptId", "DEPT_TECH");
            List<Map<String, Object>> result = employeeMapper.selectByCondition(
                    conditions, List.of("DEPT_TECH", "DEPT_HR", "DEPT_FIN"));
            assertEquals(5, result.size());
        }
    }

    @Nested
    @DisplayName("PerformanceRecordMapper Tests")
    class PerformanceRecordMapperTests {

        @Test
        void selectAll_returnsAllRecords() {
            List<Map<String, Object>> all = performanceRecordMapper.selectAll();
            assertEquals(12, all.size());
        }

        @Test
        void selectByEmployeeId_returnsRecordsForEmployee() {
            List<Map<String, Object>> records = performanceRecordMapper.selectByEmployeeId("E001");
            assertEquals(2, records.size());
        }

        @Test
        void selectByEmployeeId_withScope_filtersCorrectly() {
            // E001 属于 DEPT_TECH，scope 包含 DEPT_TECH → 应返回记录
            List<Map<String, Object>> records = performanceRecordMapper.selectByEmployeeId(
                    "E001", List.of("DEPT_TECH"));
            assertEquals(2, records.size());
        }

        @Test
        void selectByEmployeeId_withOutOfScope_returnsEmpty() {
            // E001 属于 DEPT_TECH，scope 只有 DEPT_HR → 应返回空
            List<Map<String, Object>> records = performanceRecordMapper.selectByEmployeeId(
                    "E001", List.of("DEPT_HR"));
            assertTrue(records.isEmpty());
        }

        @Test
        void selectByEmployeeId_withEmptyScope_returnsEmpty() {
            List<Map<String, Object>> records = performanceRecordMapper.selectByEmployeeId(
                    "E001", List.of());
            assertTrue(records.isEmpty());
        }
    }
}
