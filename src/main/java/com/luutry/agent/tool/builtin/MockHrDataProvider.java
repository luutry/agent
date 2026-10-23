package com.luutry.agent.tool.builtin;

import com.luutry.agent.repository.HrDataProvider;
import com.luutry.agent.repository.mock.JsonDepartmentMapper;
import com.luutry.agent.repository.mock.JsonEmployeeMapper;
import com.luutry.agent.repository.mock.JsonPerformanceRecordMapper;
import com.luutry.agent.security.filter.PiiMaskerFactory;
import com.luutry.agent.security.filter.PiiType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * HR 业务数据提供者（委托 Repository 层）。
 * <p>
 * 当前阶段委托 JsonFileBaseMapper 子类从 JSON 文件加载数据，
 * 模拟 MyBatis-Plus 的数据访问方式。真实数据库接入后只需替换 Mapper 实现，
 * 本类及上层工具代码不改（R11）。
 * </p>
 * <p>
 * 过渡态：真实数据已由 {@link com.luutry.agent.repository.mysql.MySqlHrDataProvider} 提供（@Primary），
 * 本 Mock 实现仅为兼容既有单元测试保留，将在阶段 D 随 Json Mock 一并移除。
 * </p>
 */
@Component
public class MockHrDataProvider implements HrDataProvider {

    private final JsonEmployeeMapper employeeMapper;
    private final JsonDepartmentMapper departmentMapper;
    private final JsonPerformanceRecordMapper performanceRecordMapper;

    public MockHrDataProvider(JsonEmployeeMapper employeeMapper,
                              JsonDepartmentMapper departmentMapper,
                              JsonPerformanceRecordMapper performanceRecordMapper) {
        this.employeeMapper = employeeMapper;
        this.departmentMapper = departmentMapper;
        this.performanceRecordMapper = performanceRecordMapper;
    }

    public List<Map<String, Object>> getEmployees() {
        return maskEmployees(employeeMapper.selectAll());
    }

    public List<Map<String, Object>> getDepartments() {
        return departmentMapper.selectAll();
    }

    public List<Map<String, Object>> getPerformanceRecords() {
        return performanceRecordMapper.selectAll();
    }

    public Map<String, Object> findEmployeeById(String employeeId) {
        return maskEmployee(employeeMapper.selectById(employeeId));
    }

    public Map<String, Object> findDepartmentById(String deptId) {
        return departmentMapper.selectById(deptId);
    }

    public List<Map<String, Object>> getSubDepartments(String parentDeptId) {
        return departmentMapper.selectByParentDeptId(parentDeptId);
    }

    public List<Map<String, Object>> getPerformanceByEmployeeId(String employeeId) {
        return performanceRecordMapper.selectByEmployeeId(employeeId);
    }

    // ===== R2 权限范围查询方法（供工具层调用） =====

    /** 按管辖范围查询部门 */
    public List<Map<String, Object>> getDepartments(List<String> scope) {
        return departmentMapper.selectList(scope);
    }

    /** 按管辖范围查询员工 */
    public List<Map<String, Object>> getEmployees(List<String> scope) {
        return maskEmployees(employeeMapper.selectList(scope));
    }

    /** 按管辖范围查询绩效记录（通过员工关联过滤） */
    public List<Map<String, Object>> getPerformanceByEmployeeId(String employeeId, List<String> scope) {
        return performanceRecordMapper.selectByEmployeeId(employeeId, scope);
    }

    /** 按管辖范围查询子部门 */
    public List<Map<String, Object>> getSubDepartments(String parentDeptId, List<String> scope) {
        return departmentMapper.selectByParentDeptId(parentDeptId, scope);
    }

    /** 全量部门数（用于 TeamMetricsTool 的 scopeNotice 截断检测） */
    public int getTotalDepartmentCount() {
        return departmentMapper.selectAll().size();
    }

    // ===== 出域脱敏：与 MySqlHrDataProvider 契约一致（phone 掩码，R3） =====
    // Mock 数据为共享缓存，必须复制后再掩码，避免污染原始 JSON 加载结果。

    private List<Map<String, Object>> maskEmployees(List<Map<String, Object>> rows) {
        if (rows == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(maskEmployee(row));
        }
        return out;
    }

    private Map<String, Object> maskEmployee(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(row);
        Object phone = copy.get("phone");
        if (phone != null) {
            copy.put("phone", PiiMaskerFactory.getMasker(PiiType.PHONE).mask(phone.toString()));
        }
        return copy;
    }
}
