package com.luutry.agent.tool.builtin;

import com.luutry.agent.repository.mock.JsonDepartmentMapper;
import com.luutry.agent.repository.mock.JsonEmployeeMapper;
import com.luutry.agent.repository.mock.JsonPerformanceRecordMapper;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for all 5 built-in HR tools
 */
@DisplayName("Built-in HR Tools Tests")
class BuiltinToolsTest {

    private MockHrDataProvider dataProvider;
    private EmployeeSearchTool searchTool;
    private TeamMetricsTool metricsTool;
    private EmployeeProfileTool profileTool;
    private PerformanceRecordTool perfTool;
    private OrgTreeTool orgTreeTool;

    @BeforeEach
    void setUp() {
        // R2：所有工具测试默认授予全部门范围，个别嵌套类可覆盖
        UserContextHolder.set(UserContext.builder()
                .userId("manager_001")
                .managedOrgIds(List.of("DEPT_TECH", "DEPT_HR", "DEPT_FIN"))
                .build());

        JsonEmployeeMapper employeeMapper = new JsonEmployeeMapper();
        JsonDepartmentMapper departmentMapper = new JsonDepartmentMapper();
        JsonPerformanceRecordMapper performanceRecordMapper = new JsonPerformanceRecordMapper(employeeMapper);
        dataProvider = new MockHrDataProvider(employeeMapper, departmentMapper, performanceRecordMapper);
        searchTool = new EmployeeSearchTool(dataProvider);
        metricsTool = new TeamMetricsTool(dataProvider);
        profileTool = new EmployeeProfileTool(dataProvider);
        perfTool = new PerformanceRecordTool(dataProvider);
        orgTreeTool = new OrgTreeTool(dataProvider);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ===== EmployeeSearchTool Tests =====

    @Nested
    @DisplayName("EmployeeSearchTool Tests")
    class EmployeeSearchTests {

        @Test
        @DisplayName("Should return all employees with empty params")
        void testSearchAll() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of())
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(12, result.getData().get("total"));
        }

        @Test
        @DisplayName("Should filter by name fuzzy match")
        void testSearchByName() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of("name", "张"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(1, result.getData().get("total"));
        }

        @Test
        @DisplayName("Should filter by department")
        void testSearchByDept() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of("deptId", "DEPT_TECH"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(5, result.getData().get("total"));
        }

        @Test
        @DisplayName("Should filter by performance grade")
        void testSearchByGrade() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of("performanceGrade", "A"))
                    .build());

            assertTrue(result.isSuccess());
            int total = (int) result.getData().get("total");
            assertTrue(total > 0);
        }

        @Test
        @DisplayName("Should filter by employment type")
        void testSearchByType() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of("employmentType", "intern"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(1, result.getData().get("total"));
        }

        @Test
        @DisplayName("Should filter by tenure range")
        void testSearchByTenure() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of("minTenure", 5, "maxTenure", 10))
                    .build());

            assertTrue(result.isSuccess());
            int total = (int) result.getData().get("total");
            assertTrue(total > 0);
        }

        @Test
        @DisplayName("Should return empty for no matches")
        void testSearchNoMatch() {
            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of("name", "不存在的名字XYZ"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(0, result.getData().get("total"));
        }

        @Test
        @DisplayName("Should reject when user has no data scope (R2)")
        void testEmptyScope_rejected() {
            UserContextHolder.set(UserContext.builder().userId("unknown").build());

            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of())
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should only return in-scope employees (R2)")
        void testPartialScope_filtersAtQueryLayer() {
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_tech")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .build());

            ToolResult result = searchTool.execute(ToolRequest.builder()
                    .toolName("employee_search")
                    .parameters(Map.of())
                    .build());

            assertTrue(result.isSuccess());
            // 技术部有 5 名员工 (E001,E002,E006,E007,E010)
            assertEquals(5, result.getData().get("total"));
        }
    }

    // ===== TeamMetricsTool Tests =====

    @Nested
    @DisplayName("TeamMetricsTool Tests")
    class TeamMetricsTests {

        @BeforeEach
        void setUpScope() {
            // R2：团队指标在查询层按管辖范围前置过滤，测试默认授予全部门范围
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_001")
                    .managedOrgIds(List.of("DEPT_TECH", "DEPT_HR", "DEPT_FIN"))
                    .build());
        }

        @AfterEach
        void clearScope() {
            UserContextHolder.clear();
        }

        @Test
        @DisplayName("Should return metrics for all in-scope departments")
        void testAllDeptMetrics() {
            ToolResult result = metricsTool.execute(ToolRequest.builder()
                    .toolName("team_metrics")
                    .parameters(Map.of())
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(3, result.getData().get("totalDepartments"));
        }

        @Test
        @DisplayName("Should return metrics for specific department")
        void testSpecificDeptMetrics() {
            ToolResult result = metricsTool.execute(ToolRequest.builder()
                    .toolName("team_metrics")
                    .parameters(Map.of("deptId", "DEPT_TECH"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(1, result.getData().get("totalDepartments"));
            List<Map<String, Object>> metrics = (List<Map<String, Object>>) result.getData().get("metrics");
            assertEquals("技术部", metrics.get(0).get("deptName"));
        }

        @Test
        @DisplayName("Should reject department outside managed scope")
        void testOutOfScopeDept_rejected() {
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_tech")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .build());

            ToolResult result = metricsTool.execute(ToolRequest.builder()
                    .toolName("team_metrics")
                    .parameters(Map.of("deptId", "DEPT_HR"))
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should only aggregate in-scope departments when scope is partial")
        void testPartialScope_filtersAtQueryLayer() {
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_tech")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .build());

            ToolResult result = metricsTool.execute(ToolRequest.builder()
                    .toolName("team_metrics")
                    .parameters(Map.of())
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(1, result.getData().get("totalDepartments"));
        }

        @Test
        @DisplayName("Should reject when user has no data scope")
        void testEmptyScope_rejected() {
            UserContextHolder.set(UserContext.builder().userId("unknown").build());

            ToolResult result = metricsTool.execute(ToolRequest.builder()
                    .toolName("team_metrics")
                    .parameters(Map.of())
                    .build());

            assertFalse(result.isSuccess());
        }
    }

    // ===== EmployeeProfileTool Tests =====

    @Nested
    @DisplayName("EmployeeProfileTool Tests")
    class EmployeeProfileTests {

        @Test
        @DisplayName("Should return profile for existing employee")
        void testGetProfile() {
            ToolResult result = profileTool.execute(ToolRequest.builder()
                    .toolName("employee_profile")
                    .parameters(Map.of("employeeId", "E001"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals("张三", result.getData().get("name"));
            assertNotNull(result.getData().get("deptName"));
        }

        @Test
        @DisplayName("Should mask PII fields in profile")
        void testPiiMasking() {
            ToolResult result = profileTool.execute(ToolRequest.builder()
                    .toolName("employee_profile")
                    .parameters(Map.of("employeeId", "E001"))
                    .build());

            assertTrue(result.isSuccess());
            String phone = result.getData().get("phone").toString();
            assertTrue(phone.contains("****"), "Phone should be masked");
        }

        @Test
        @DisplayName("Should reject employee outside managed scope (R2)")
        void testOutOfScopeEmployee_rejected() {
            // manager_tech 只能看技术部员工，E003 属于 HR
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_tech")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .build());

            ToolResult result = profileTool.execute(ToolRequest.builder()
                    .toolName("employee_profile")
                    .parameters(Map.of("employeeId", "E003"))
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject when user has no data scope")
        void testEmptyScope_rejected() {
            UserContextHolder.set(UserContext.builder().userId("unknown").build());

            ToolResult result = profileTool.execute(ToolRequest.builder()
                    .toolName("employee_profile")
                    .parameters(Map.of("employeeId", "E001"))
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should not expose performance detail in profile (R3.1)")
        void testNoPerformanceDetail() {
            ToolResult result = profileTool.execute(ToolRequest.builder()
                    .toolName("employee_profile")
                    .parameters(Map.of("employeeId", "E001"))
                    .build());

            assertTrue(result.isSuccess());
            assertFalse(result.getData().containsKey("latestPerformance"),
                    "绩效明细不得进入档案产物（R3.1）");
        }
    }

    // ===== PerformanceRecordTool Tests =====

    @Nested
    @DisplayName("PerformanceRecordTool Tests")
    class PerformanceRecordTests {

        @Test
        @DisplayName("Should return all records for employee")
        void testAllRecords() {
            ToolResult result = perfTool.execute(ToolRequest.builder()
                    .toolName("performance_record")
                    .parameters(Map.of("employeeId", "E001"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(2, result.getData().get("totalRecords"));
            assertNotNull(result.getData().get("averageScore"));
        }

        @Test
        @DisplayName("Should filter records by cycle")
        void testFilterByCycle() {
            ToolResult result = perfTool.execute(ToolRequest.builder()
                    .toolName("performance_record")
                    .parameters(Map.of("employeeId", "E001", "cycle", "2025-H2"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(1, result.getData().get("totalRecords"));
        }

        @Test
        @DisplayName("Should reject when employee is out of scope (R2)")
        void testOutOfScopeEmployee_rejected() {
            // manager_tech 只能看技术部员工，E003 属于 HR
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_tech")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .build());

            ToolResult result = perfTool.execute(ToolRequest.builder()
                    .toolName("performance_record")
                    .parameters(Map.of("employeeId", "E003"))
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject when user has no data scope")
        void testEmptyScope_rejected() {
            UserContextHolder.set(UserContext.builder().userId("unknown").build());

            ToolResult result = perfTool.execute(ToolRequest.builder()
                    .toolName("performance_record")
                    .parameters(Map.of("employeeId", "E001"))
                    .build());

            assertFalse(result.isSuccess());
        }
    }

    // ===== OrgTreeTool Tests =====

    @Nested
    @DisplayName("OrgTreeTool Tests")
    class OrgTreeTests {

        @Test
        @DisplayName("Should return all top-level departments")
        void testRootTree() {
            ToolResult result = orgTreeTool.execute(ToolRequest.builder()
                    .toolName("org_tree")
                    .parameters(Map.of())
                    .build());

            assertTrue(result.isSuccess());
            assertEquals(3, result.getData().get("totalTopDepartments"));
        }

        @Test
        @DisplayName("Should return department detail with sub-departments")
        void testDeptDetail() {
            ToolResult result = orgTreeTool.execute(ToolRequest.builder()
                    .toolName("org_tree")
                    .parameters(Map.of("deptId", "DEPT_TECH"))
                    .build());

            assertTrue(result.isSuccess());
            assertEquals("技术部", result.getData().get("deptName"));
        }

        @Test
        @DisplayName("Should reject department outside managed scope (R2)")
        void testOutOfScopeDept_rejected() {
            UserContextHolder.set(UserContext.builder()
                    .userId("manager_tech")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .build());

            ToolResult result = orgTreeTool.execute(ToolRequest.builder()
                    .toolName("org_tree")
                    .parameters(Map.of("deptId", "DEPT_HR"))
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should reject when user has no data scope")
        void testEmptyScope_rejected() {
            UserContextHolder.set(UserContext.builder().userId("unknown").build());

            ToolResult result = orgTreeTool.execute(ToolRequest.builder()
                    .toolName("org_tree")
                    .parameters(Map.of())
                    .build());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Should include actual staff count in department node")
        void testStaffCount() {
            ToolResult result = orgTreeTool.execute(ToolRequest.builder()
                    .toolName("org_tree")
                    .parameters(Map.of("deptId", "DEPT_TECH"))
                    .build());

            assertTrue(result.isSuccess());
            assertNotNull(result.getData().get("actualStaffCount"));
        }
    }
}
