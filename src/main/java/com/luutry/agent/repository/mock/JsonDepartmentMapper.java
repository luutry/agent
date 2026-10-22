package com.luutry.agent.repository.mock;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 部门表 JSON Mapper。
 * 从 mock/departments.json 加载部门数据，scope 字段为 deptId。
 */
@Component
public class JsonDepartmentMapper extends JsonFileBaseMapper<Map<String, Object>> {

    @Override
    protected String getJsonFileName() {
        return "mock/departments.json";
    }

    @Override
    protected String getPrimaryKeyField() {
        return "deptId";
    }

    @Override
    protected String getScopeField() {
        return "deptId";
    }

    /** 按父部门 ID 查子部门（无权限过滤，内部使用） */
    public List<Map<String, Object>> selectByParentDeptId(String parentDeptId) {
        return selectAll().stream()
                .filter(d -> parentDeptId.equals(String.valueOf(d.get("parentDeptId"))))
                .toList();
    }

    /** 按父部门 ID 查子部门 + 权限过滤 */
    public List<Map<String, Object>> selectByParentDeptId(String parentDeptId, List<String> scope) {
        return selectList(scope).stream()
                .filter(d -> parentDeptId.equals(String.valueOf(d.get("parentDeptId"))))
                .toList();
    }
}
