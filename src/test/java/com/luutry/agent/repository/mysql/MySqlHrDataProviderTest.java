package com.luutry.agent.repository.mysql;

import com.luutry.agent.repository.HrDataProvider;
import com.luutry.agent.repository.mapper.HrQueryMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySqlHrDataProvider 集成测试。
 * <p>
 * 手动 bootstrap MyBatis（不加载 Spring 全上下文），依赖本机 MySQL(hr_data)。
 * MySQL 不可用或未执行 sql/005_dept_metrics_seed.sql 时整类跳过，保证无库 CI 仍全绿。
 * </p>
 */
@DisplayName("MySqlHrDataProvider Integration Tests")
class MySqlHrDataProviderTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/hr_data?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String USER = "root";
    private static final String PWD = "root";

    /** 种子里确定存在员工的部门编码 */
    private static final String DEPT_BE = "DEPT_BE";

    private SqlSession session;
    private HrDataProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource ds = new UnpooledDataSource(DRIVER, URL, USER, PWD);
        assumeTrue(isReachable(ds), "本机无 MySQL(hr_data)，跳过 MySqlHrDataProvider 集成测试");
        assumeTrue(hasDeptMetricsColumns(ds), "hr_department 缺少 turnover_rate 列，请先执行 sql/005_dept_metrics_seed.sql");

        Configuration cfg = new Configuration(new Environment("it", new JdbcTransactionFactory(), ds));
        cfg.setMapUnderscoreToCamelCase(true);
        try (InputStream is = Resources.getResourceAsStream("mapper/HrQueryMapper.xml")) {
            new XMLMapperBuilder(is, cfg, "mapper/HrQueryMapper.xml", cfg.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(cfg);
        session = factory.openSession();
        provider = new MySqlHrDataProvider(session.getMapper(HrQueryMapper.class));
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    @DisplayName("R2 - scope 下推 SQL：只返回管辖部门的员工")
    void testScopePushdownEmployees() {
        List<Map<String, Object>> emps = provider.getEmployees(List.of(DEPT_BE));
        assertFalse(emps.isEmpty(), "DEPT_BE 应有种子员工");
        assertTrue(emps.stream().allMatch(e -> DEPT_BE.equals(String.valueOf(e.get("deptId")))),
                "越权部门员工不得出现（R2 查询层过滤）");
    }

    @Test
    @DisplayName("R2 - 空 scope 返回空，绝不全量放行")
    void testEmptyScopeReturnsEmpty() {
        assertTrue(provider.getEmployees(List.of()).isEmpty());
        assertTrue(provider.getDepartments(List.of()).isEmpty());
        assertTrue(provider.getPerformanceByEmployeeId(anyEmployeeCode(), List.of()).isEmpty());
    }

    @Test
    @DisplayName("R3 - phone 掩码、email 明文、idNumber/birthDate 不出域")
    void testDesensitization() {
        String code = anyEmployeeCode();
        Map<String, Object> emp = provider.findEmployeeById(code);
        assertNotNull(emp, "应能按工号查到员工");

        Object phone = emp.get("phone");
        if (phone != null && !phone.toString().isBlank()) {
            assertTrue(phone.toString().matches("\\d{3}\\*{4}\\d{4}"),
                    "phone 必须掩码为 138****1234，实际=" + phone);
        }
        Object email = emp.get("email");
        if (email != null && !email.toString().isBlank()) {
            assertTrue(email.toString().contains("@"), "email 应为明文");
            assertFalse(email.toString().contains("***"), "email 不应被掩码（决策⑤）");
        }
        assertFalse(emp.containsKey("idNumber"), "身份证不得出域（R3）");
        assertFalse(emp.containsKey("birthDate"), "出生日期不得出域（R3）");
    }

    @Test
    @DisplayName("绩效三表 join - 返回 cycle/score/grade")
    void testPerformanceJoin() {
        String code = anyEmployeeCode();
        List<Map<String, Object>> records =
                provider.getPerformanceByEmployeeId(code, List.of(DEPT_BE, "DEPT_TECH", "DEPT_FE", "DEPT_QA", "DEPT_SRE"));
        // 员工可能不在上述部门，仅在有记录时校验结构
        records.forEach(r -> {
            assertNotNull(r.get("cycle"), "cycle 应来自 hr_evaluation_cycle.cycle_name");
            assertNotNull(r.get("score"), "score 应来自 hr_evaluation_result.total_score");
            assertNotNull(r.get("grade"), "grade 应来自 final_grade??grade");
        });
    }

    @Test
    @DisplayName("部门指标 - turnoverRate/probationPassRate 直存列可读（决策③）")
    void testDepartmentMetricsColumns() {
        List<Map<String, Object>> depts = provider.getDepartments(List.of(DEPT_BE));
        assertFalse(depts.isEmpty());
        Map<String, Object> dept = depts.get(0);
        assertNotNull(dept.get("turnoverRate"), "turnoverRate 应直读列");
        assertNotNull(dept.get("probationPassRate"), "probationPassRate 应直读列");
        assertEquals(DEPT_BE, String.valueOf(dept.get("deptId")));
    }

    /** 取一个确定存在的工号：从 DEPT_BE 员工里拿第一个 */
    private String anyEmployeeCode() {
        List<Map<String, Object>> emps = provider.getEmployees(List.of(DEPT_BE));
        assumeTrue(!emps.isEmpty(), "无种子员工可用");
        return String.valueOf(emps.get(0).get("employeeId"));
    }

    private boolean isReachable(UnpooledDataSource ds) {
        try (Connection c = ds.getConnection()) {
            return c.isValid(2);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasDeptMetricsColumns(UnpooledDataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA='hr_data' AND TABLE_NAME='hr_department' "
                    + "AND COLUMN_NAME IN ('turnover_rate','probation_pass_rate','head_employee_code')");
            return rs.next() && rs.getInt(1) == 3;
        } catch (Throwable t) {
            return false;
        }
    }
}
