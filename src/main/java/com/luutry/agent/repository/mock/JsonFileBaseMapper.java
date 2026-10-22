package com.luutry.agent.repository.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luutry.agent.repository.jsonapi.JsonBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * JSON 文件实现的通用 Mapper 基类。
 * <p>
 * 启动时从 classpath 加载 JSON 数据文件，模拟 MyBatis-Plus 的数据库表。
 * 子类只需指定文件名和主键字段即可获得 BaseMapper 全部能力。
 * 真实接入时替换为 MybatisPlusXxxMapper，调用方不改（R11）。
 * </p>
 */
@Slf4j
public abstract class JsonFileBaseMapper<T> implements JsonBaseMapper<Map<String, Object>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 子类提供：classpath 下的 JSON 文件名，如 "mock/departments.json" */
    protected abstract String getJsonFileName();

    /** 子类提供：主键字段名，如 "deptId"、"employeeId" */
    protected abstract String getPrimaryKeyField();

    /** 子类提供：数据权限过滤字段名，如 "deptId"（员工所属部门） */
    protected abstract String getScopeField();

    private final List<Map<String, Object>> allData;

    protected JsonFileBaseMapper() {
        this.allData = loadData();
    }

    @Override
    public List<Map<String, Object>> selectAll() {
        return Collections.unmodifiableList(allData);
    }

    @Override
    public List<Map<String, Object>> selectList(List<String> scope) {
        if (scope == null || scope.isEmpty()) {
            return Collections.emptyList();
        }
        return allData.stream()
                .filter(row -> scope.contains(String.valueOf(row.get(getScopeField()))))
                .toList();
    }

    @Override
    public Map<String, Object> selectById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return allData.stream()
                .filter(row -> id.equals(String.valueOf(row.get(getPrimaryKeyField()))))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Map<String, Object>> selectByCondition(Map<String, Object> conditions, List<String> scope) {
        List<Map<String, Object>> scoped = (scope != null && !scope.isEmpty())
                ? selectList(scope)
                : new ArrayList<>(allData);

        if (conditions == null || conditions.isEmpty()) {
            return scoped;
        }

        return scoped.stream()
                .filter(row -> conditions.entrySet().stream().allMatch(e -> matchCondition(row, e.getKey(), e.getValue())))
                .toList();
    }

    /** 单字段匹配：精确相等或 contains（字符串模糊匹配） */
    private boolean matchCondition(Map<String, Object> row, String key, Object value) {
        if (value == null) {
            return true;
        }
        Object fieldValue = row.get(key);
        if (fieldValue == null) {
            return false;
        }
        // 数值范围条件（司龄 minTenure/maxTenure）由调用方在 stream 里额外过滤，此处只做等值/包含
        if (fieldValue instanceof String fs && value instanceof String vs) {
            return fs.contains(vs);
        }
        return fieldValue.equals(value);
    }

    private List<Map<String, Object>> loadData() {
        String file = getJsonFileName();
        try (InputStream is = new ClassPathResource(file).getInputStream()) {
            return OBJECT_MAPPER.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Failed to load mock JSON data from {}: {}", file, e.getMessage());
            return Collections.emptyList();
        }
    }
}
