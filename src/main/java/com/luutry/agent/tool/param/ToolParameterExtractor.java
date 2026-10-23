package com.luutry.agent.tool.param;

import com.luutry.agent.repository.HrDataProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规则式槽位抽取：从查询中补出工具入参（工号、部门、绩效周期）。
 * 保守策略：同一槽位出现多个候选时不绑定，宁可让工具报缺参，也不静默绑错。
 * TODO(doc/TODO/002_外部HR_API接入.md): 姓名→工号、部门名→部门ID 当前依赖内置 Mock 数据，真实实现由 HR 接口提供。
 */
@Component
public class ToolParameterExtractor {

    /** 显式工号，如 E001。 */
    private static final Pattern EMPLOYEE_ID_PATTERN =
            Pattern.compile("\\b(E\\d{3})\\b", Pattern.CASE_INSENSITIVE);

    /** 绩效周期，如 2025-H2。 */
    private static final Pattern CYCLE_PATTERN =
            Pattern.compile("\\b(20\\d{2}-H[12])\\b", Pattern.CASE_INSENSITIVE);

    private static final String PARAM_EMPLOYEE_ID = "employeeId";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_DEPT_ID = "deptId";
    private static final String PARAM_CYCLE = "cycle";

    private final HrDataProvider dataProvider;

    public ToolParameterExtractor(HrDataProvider dataProvider) {
        this.dataProvider = Objects.requireNonNull(dataProvider, "dataProvider must not be null");
    }

    /**
     * 抽取工具入参。
     *
     * @param query 用户原始查询
     * @return 只包含识别成功的槽位；无法识别时返回空 Map（不返回 null）
     */
    public Map<String, Object> extract(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Object> params = new LinkedHashMap<>();

        // 显式工号优先于姓名解析，避免姓名命中覆盖用户明确指定的对象
        putIfPresent(params, PARAM_EMPLOYEE_ID, matchSingle(EMPLOYEE_ID_PATTERN, query));
        if (!params.containsKey(PARAM_EMPLOYEE_ID)) {
            params.putAll(resolveEmployeeByName(query));
        }

        putIfPresent(params, PARAM_DEPT_ID, resolveDepartmentId(query));
        putIfPresent(params, PARAM_CYCLE, matchSingle(CYCLE_PATTERN, query));

        return params;
    }

    /**
     * 姓名唯一命中时才回填工号与姓名，多个姓名视为歧义不绑定。
     */
    private Map<String, Object> resolveEmployeeByName(String query) {
        List<Map<String, Object>> hits = dataProvider.getEmployees().stream()
                .filter(employee -> query.contains(String.valueOf(employee.get(PARAM_NAME))))
                .toList();
        Set<String> employeeIds = hits.stream()
                .map(employee -> String.valueOf(employee.get(PARAM_EMPLOYEE_ID)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (employeeIds.size() != 1) {
            return Collections.emptyMap();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(PARAM_EMPLOYEE_ID, employeeIds.iterator().next());
        params.put(PARAM_NAME, hits.get(0).get(PARAM_NAME));
        return params;
    }

    /**
     * 部门名或部门ID唯一命中时才回填，多个部门视为歧义不绑定。
     */
    private String resolveDepartmentId(String query) {
        Set<String> deptIds = dataProvider.getDepartments().stream()
                .filter(dept -> query.contains(String.valueOf(dept.get("deptName")))
                        || query.contains(String.valueOf(dept.get(PARAM_DEPT_ID))))
                .map(dept -> String.valueOf(dept.get(PARAM_DEPT_ID)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return deptIds.size() == 1 ? deptIds.iterator().next() : null;
    }

    /**
     * 正则多命中时只在唯一时返回，统一转大写便于与工具Schema对齐。
     */
    private String matchSingle(Pattern pattern, String query) {
        Matcher matcher = pattern.matcher(query);
        Set<String> matches = new LinkedHashSet<>();
        while (matcher.find()) {
            matches.add(matcher.group(1).toUpperCase());
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private void putIfPresent(Map<String, Object> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }
}
