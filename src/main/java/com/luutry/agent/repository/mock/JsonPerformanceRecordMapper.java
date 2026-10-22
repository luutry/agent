package com.luutry.agent.repository.mock;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 绩效记录表 JSON Mapper。
 * 从 mock/performance_records.json 加载绩效数据。
 * R3.1：绩效为最高敏感级，本 Mapper 仅提供数据访问，敏感控制由上层工具负责。
 */
@Component
public class JsonPerformanceRecordMapper extends JsonFileBaseMapper<Map<String, Object>> {

    private final JsonEmployeeMapper employeeMapper;

    public JsonPerformanceRecordMapper(JsonEmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    protected String getJsonFileName() {
        return "mock/performance_records.json";
    }

    @Override
    protected String getPrimaryKeyField() {
        return "employeeId";
    }

    @Override
    protected String getScopeField() {
        return "deptId";
    }

    /** 按员工 ID 查询绩效记录（无权限过滤） */
    public List<Map<String, Object>> selectByEmployeeId(String employeeId) {
        return selectAll().stream()
                .filter(r -> employeeId.equals(String.valueOf(r.get("employeeId"))))
                .toList();
    }

    /** 按员工 ID + 权限范围查询绩效记录（R2） */
    public List<Map<String, Object>> selectByEmployeeId(String employeeId, List<String> scope) {
        if (scope == null || scope.isEmpty()) {
            return List.of();
        }
        List<String> scopedEmployeeIds = employeeMapper.selectList(scope).stream()
                .map(e -> String.valueOf(e.get("employeeId")))
                .toList();
        return selectAll().stream()
                .filter(r -> employeeId.equals(String.valueOf(r.get("employeeId")))
                        && scopedEmployeeIds.contains(String.valueOf(r.get("employeeId"))))
                .toList();
    }
}
