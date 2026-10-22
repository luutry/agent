package com.luutry.agent.tool.builtin;

import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 团队指标聚合工具
 * <p>
 * 用途：按部门聚合计算人力指标，包括编制使用率、离职率、转正率、绩效等级分布、平均司龄等。
 * 处理问题：管理者需要了解团队整体人力状况，如"技术部的编制使用率和离职率是多少"；
 * 缺少聚合能力只能逐人查询再手动统计，效率极低。
 * 处理方式：
 * 1. 按当前用户管辖范围（DataScope）在查询层前置过滤部门（R2），空范围或越权一律拒绝；
 * 2. 从MockHrDataProvider获取部门信息和员工列表；
 * 3. 计算编制使用率 = currentStaff / headcount；
 * 4. 统计绩效等级分布（A/B+/B/C各多少人）；
 * 5. 计算平均司龄、在职人数、用工类型分布；
 * 6. 返回聚合指标Map。
 * </p>
 */
@Component
public class TeamMetricsTool implements AgentTool {

    public static final String TOOL_NAME = "team_metrics";

    private final MockHrDataProvider dataProvider;

    public TeamMetricsTool(MockHrDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> params = request.getParameters();
        String deptId = params.containsKey("deptId") ? params.get("deptId").toString() : null;

        // R2 数据权限前置：按管辖范围在查询层过滤，禁止查完再过滤、禁止越权、禁止静默截断
        // TODO(doc/TODO/010_数据权限真实模型接入.md): 真实实现应把 scope 下推到 SQL 条件，而非内存过滤
        UserContext ctx = UserContextHolder.get();
        List<String> scope = ctx != null ? ctx.getManagedOrgIds() : null;
        if (scope == null || scope.isEmpty()) {
            return ToolResult.fail("无数据权限范围，拒绝访问团队指标");
        }

        List<Map<String, Object>> deptList;
        String scopeNotice = null;
        if (deptId != null) {
            if (!scope.contains(deptId)) {
                return ToolResult.fail("无权访问该部门团队指标: " + deptId);
            }
            Map<String, Object> dept = dataProvider.findDepartmentById(deptId);
            deptList = dept != null ? List.of(dept) : Collections.emptyList();
        } else {
            List<Map<String, Object>> all = dataProvider.getDepartments();
            deptList = all.stream()
                    .filter(d -> scope.contains(String.valueOf(d.get("deptId"))))
                    .toList();
            // N4 透明化：请求"各部门"但按权限只返回部分时附声明，避免用户误以为"公司只有这些部门"
            if (deptList.size() < all.size()) {
                scopeNotice = "（仅显示您管辖的部门，未含全公司其他部门）";
            }
        }

        List<Map<String, Object>> metricsList = deptList.stream()
                .map(this::computeDeptMetrics)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalDepartments", metricsList.size());
        data.put("metrics", metricsList);
        if (scopeNotice != null) {
            data.put(ToolResult.SCOPE_NOTICE_KEY, scopeNotice);
        }

        return ToolResult.success(data);
    }

    private Map<String, Object> computeDeptMetrics(Map<String, Object> dept) {
        String deptId = dept.get("deptId").toString();
        List<Map<String, Object>> deptEmployees = dataProvider.getEmployees().stream()
                .filter(e -> deptId.equals(e.get("deptId")))
                .toList();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("deptId", deptId);
        metrics.put("deptName", dept.get("deptName"));

        int headcount = ((Number) dept.get("headcount")).intValue();
        int currentStaff = ((Number) dept.get("currentStaff")).intValue();
        metrics.put("headcount", headcount);
        metrics.put("currentStaff", currentStaff);
        metrics.put("headcountUsageRate", headcount > 0
                ? Math.round((double) currentStaff / headcount * 100) / 100.0 : 0.0);

        metrics.put("turnoverRate", dept.get("turnoverRate"));
        metrics.put("probationPassRate", dept.get("probationPassRate"));

        Map<String, Long> perfDistribution = deptEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.get("performanceGrade").toString(),
                        Collectors.counting()));
        metrics.put("performanceDistribution", perfDistribution);

        double avgTenure = deptEmployees.stream()
                .mapToInt(e -> ((Number) e.get("tenureYears")).intValue())
                .average()
                .orElse(0.0);
        metrics.put("averageTenureYears", Math.round(avgTenure * 10) / 10.0);

        Map<String, Long> empTypeDistribution = deptEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.get("employmentType").toString(),
                        Collectors.counting()));
        metrics.put("employmentTypeDistribution", empTypeDistribution);

        metrics.put("totalEmployees", deptEmployees.size());

        return metrics;
    }
}
