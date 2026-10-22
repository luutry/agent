package com.luutry.agent.tool.builtin;

import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效记录查询工具
 * <p>
 * 用途：根据员工ID和可选的绩效周期查询绩效评分、等级、评语和申诉记录。
 * 处理问题：管理者需要了解员工绩效情况，如"查看张三最近两次绩效"；
 * 需要支持按周期过滤，也支持查全部历史记录。
 * 处理方式：
 * 1. 从ToolRequest获取employeeId（必填）和cycle（可选）；
 * 2. 从MockHrDataProvider查询该员工的绩效记录；
 * 3. 如果指定了cycle参数，进一步按周期过滤；
 * 4. 返回绩效记录列表和统计信息（平均分、最高分）。
 * </p>
 */
@Component
public class PerformanceRecordTool implements AgentTool {

    public static final String TOOL_NAME = "performance_record";

    private final MockHrDataProvider dataProvider;

    public PerformanceRecordTool(MockHrDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> params = request.getParameters();
        String employeeId = params.get("employeeId").toString();
        String cycle = params.containsKey("cycle") && params.get("cycle") != null
                ? params.get("cycle").toString() : null;

        // R2 数据权限前置：校验目标员工是否在当前用户管辖范围内
        UserContext ctx = UserContextHolder.get();
        List<String> scope = ctx != null ? ctx.getManagedOrgIds() : null;
        if (scope == null || scope.isEmpty()) {
            return ToolResult.fail("无数据权限范围，拒绝访问绩效记录");
        }

        // 通过 scope 感知的 Mapper 方法查询绩效记录
        List<Map<String, Object>> records = dataProvider.getPerformanceByEmployeeId(employeeId, scope);
        if (records.isEmpty()) {
            // 可能是员工不在管辖范围，也可能确实无绩效记录
            Map<String, Object> emp = dataProvider.findEmployeeById(employeeId);
            if (emp == null || !scope.contains(String.valueOf(emp.get("deptId")))) {
                return ToolResult.fail("无权访问该员工绩效记录: " + employeeId);
            }
        }

        if (cycle != null) {
            records = records.stream()
                    .filter(r -> cycle.equals(r.get("cycle")))
                    .toList();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeId", employeeId);
        data.put("records", records);
        data.put("totalRecords", records.size());

        if (!records.isEmpty()) {
            double avgScore = records.stream()
                    .mapToInt(r -> ((Number) r.get("score")).intValue())
                    .average()
                    .orElse(0.0);
            data.put("averageScore", Math.round(avgScore * 10) / 10.0);

            int maxScore = records.stream()
                    .mapToInt(r -> ((Number) r.get("score")).intValue())
                    .max()
                    .orElse(0);
            data.put("maxScore", maxScore);
        }

        return ToolResult.success(data);
    }
}
