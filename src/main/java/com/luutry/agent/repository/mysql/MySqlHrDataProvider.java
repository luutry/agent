package com.luutry.agent.repository.mysql;

import com.luutry.agent.repository.HrDataProvider;
import com.luutry.agent.repository.mapper.HrQueryMapper;
import com.luutry.agent.security.filter.PiiMaskerFactory;
import com.luutry.agent.security.filter.PiiType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HrDataProvider 的真实 MySQL 实现（唯一出域脱敏口）。
 * <p>
 * scope 为空一律返回空结果（R2：宁可少给不可越权）；员工 phone 掩码、email 明文、
 * idNumber/birthDate 不在查询列内（R3）；掩码失败时丢弃该字段值（fail-closed）。
 * </p>
 */
@Slf4j
@Primary
@Component
public class MySqlHrDataProvider implements HrDataProvider {

    private static final String KEY_PHONE = "phone";

    private final HrQueryMapper queryMapper;

    public MySqlHrDataProvider(HrQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    @Override
    public List<Map<String, Object>> getEmployees() {
        return maskAll(queryMapper.selectEmployees(null));
    }

    @Override
    public List<Map<String, Object>> getEmployees(List<String> scopeDeptCodes) {
        if (isEmpty(scopeDeptCodes)) {
            return Collections.emptyList();
        }
        return maskAll(queryMapper.selectEmployees(scopeDeptCodes));
    }

    @Override
    public Map<String, Object> findEmployeeById(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) {
            return null;
        }
        return maskOne(queryMapper.selectEmployeeByCode(employeeCode));
    }

    @Override
    public List<Map<String, Object>> getDepartments() {
        return queryMapper.selectDepartments(null);
    }

    @Override
    public List<Map<String, Object>> getDepartments(List<String> scopeDeptCodes) {
        if (isEmpty(scopeDeptCodes)) {
            return Collections.emptyList();
        }
        return queryMapper.selectDepartments(scopeDeptCodes);
    }

    @Override
    public Map<String, Object> findDepartmentById(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return null;
        }
        return queryMapper.selectDepartmentByCode(deptCode);
    }

    @Override
    public List<Map<String, Object>> getSubDepartments(String parentDeptCode) {
        return queryMapper.selectSubDepartments(parentDeptCode, null);
    }

    @Override
    public List<Map<String, Object>> getSubDepartments(String parentDeptCode, List<String> scopeDeptCodes) {
        if (isEmpty(scopeDeptCodes)) {
            return Collections.emptyList();
        }
        return queryMapper.selectSubDepartments(parentDeptCode, scopeDeptCodes);
    }

    @Override
    public int getTotalDepartmentCount() {
        return queryMapper.countDepartments();
    }

    @Override
    public List<Map<String, Object>> getPerformanceRecords() {
        return queryMapper.selectAllPerformance();
    }

    @Override
    public List<Map<String, Object>> getPerformanceByEmployeeId(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) {
            return Collections.emptyList();
        }
        return queryMapper.selectPerformanceByEmployeeCode(employeeCode, null);
    }

    @Override
    public List<Map<String, Object>> getPerformanceByEmployeeId(String employeeCode, List<String> scopeDeptCodes) {
        if (employeeCode == null || employeeCode.isBlank() || isEmpty(scopeDeptCodes)) {
            return Collections.emptyList();
        }
        return queryMapper.selectPerformanceByEmployeeCode(employeeCode, scopeDeptCodes);
    }

    private boolean isEmpty(List<String> scope) {
        return scope == null || scope.isEmpty();
    }

    private List<Map<String, Object>> maskAll(List<Map<String, Object>> rows) {
        if (rows == null) {
            return Collections.emptyList();
        }
        rows.forEach(this::maskOne);
        return rows;
    }

    private Map<String, Object> maskOne(Map<String, Object> row) {
        if (row == null || !row.containsKey(KEY_PHONE)) {
            return row;
        }
        Object raw = row.get(KEY_PHONE);
        try {
            row.put(KEY_PHONE, raw == null ? "" : PiiMaskerFactory.getMasker(PiiType.PHONE).mask(raw.toString()));
        } catch (Throwable t) {
            // fail-closed：脱敏失败绝不回退原文（R3）
            log.warn("phone 脱敏失败，已置空该字段");
            row.put(KEY_PHONE, "");
        }
        return row;
    }
}
