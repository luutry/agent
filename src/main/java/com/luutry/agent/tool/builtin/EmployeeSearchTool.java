package com.luutry.agent.tool.builtin;

import com.luutry.agent.repository.HrDataProvider;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

/**
 * 人员检索工具
 * <p>
 * 用途：支持多条件组合检索员工，包括按姓名模糊匹配、部门、岗位、绩效等级、司龄范围、用工类型、职级筛选。
 * 处理问题：管理者需要快速定位特定条件的员工群体，如"查找技术部绩效A的员工"；
 * 缺少组合检索能力只能通过多次单条件查询拼接，效率低。
 * 处理方式：
 * 1. 从MockHrDataProvider获取全量员工数据；
 * 2. 根据ToolRequest中的parameters构建Stream过滤链（每个条件可选）；
 * 3. 支持模糊匹配（name字段用contains）、精确匹配（deptId/position/performanceGrade等）；
 * 4. 司龄范围支持minTenure/maxTenure过滤；
 * 5. 返回匹配的员工列表和总数。
 * </p>
 */
@Component
public class EmployeeSearchTool implements AgentTool {

    public static final String TOOL_NAME = "employee_search";

    private final HrDataProvider dataProvider;

    public EmployeeSearchTool(HrDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> params = request.getParameters();

        // R2 数据权限前置：按管辖范围在查询层过滤员工
        UserContext ctx = UserContextHolder.get();
        List<String> scope = ctx != null ? ctx.getManagedOrgIds() : null;
        if (scope == null || scope.isEmpty()) {
            return ToolResult.fail("无数据权限范围，拒绝访问员工检索");
        }

        Stream<Map<String, Object>> stream = dataProvider.getEmployees(scope).stream();

        stream = applyFilters(stream, params);

        List<Map<String, Object>> results = stream.toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", results.size());
        data.put("employees", results);

        return ToolResult.success(data);
    }

    private Stream<Map<String, Object>> applyFilters(Stream<Map<String, Object>> stream,
                                                      Map<String, Object> params) {
        if (params.containsKey("name") && params.get("name") != null) {
            String nameFilter = params.get("name").toString();
            stream = stream.filter(e -> e.get("name").toString().contains(nameFilter));
        }
        if (params.containsKey("deptId") && params.get("deptId") != null) {
            String deptFilter = params.get("deptId").toString();
            stream = stream.filter(e -> deptFilter.equals(e.get("deptId")));
        }
        if (params.containsKey("position") && params.get("position") != null) {
            String posFilter = params.get("position").toString();
            stream = stream.filter(e -> e.get("position").toString().contains(posFilter));
        }
        if (params.containsKey("performanceGrade") && params.get("performanceGrade") != null) {
            String gradeFilter = params.get("performanceGrade").toString();
            stream = stream.filter(e -> gradeFilter.equals(e.get("performanceGrade")));
        }
        if (params.containsKey("employmentType") && params.get("employmentType") != null) {
            String typeFilter = params.get("employmentType").toString();
            stream = stream.filter(e -> typeFilter.equals(e.get("employmentType")));
        }
        if (params.containsKey("minTenure") && params.get("minTenure") instanceof Number minTenure) {
            stream = stream.filter(e -> ((Number) e.get("tenureYears")).intValue() >= minTenure.intValue());
        }
        if (params.containsKey("maxTenure") && params.get("maxTenure") instanceof Number maxTenure) {
            stream = stream.filter(e -> ((Number) e.get("tenureYears")).intValue() <= maxTenure.intValue());
        }
        if (params.containsKey("jobLevel") && params.get("jobLevel") != null) {
            String levelFilter = params.get("jobLevel").toString();
            stream = stream.filter(e -> e.get("jobLevel").toString().contains(levelFilter));
        }
        return stream;
    }
}
