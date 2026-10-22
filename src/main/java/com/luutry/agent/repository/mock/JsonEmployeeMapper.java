package com.luutry.agent.repository.mock;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 员工表 JSON Mapper。
 * 从 mock/employees.json 加载员工数据，scope 字段为 deptId。
 */
@Component
public class JsonEmployeeMapper extends JsonFileBaseMapper<Map<String, Object>> {

    @Override
    protected String getJsonFileName() {
        return "mock/employees.json";
    }

    @Override
    protected String getPrimaryKeyField() {
        return "employeeId";
    }

    @Override
    protected String getScopeField() {
        return "deptId";
    }
}
