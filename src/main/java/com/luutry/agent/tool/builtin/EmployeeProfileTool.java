package com.luutry.agent.tool.builtin;

import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import com.luutry.agent.repository.HrDataProvider;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工档案查询工具
 * <p>
 * 用途：根据员工ID查询完整个人档案信息，包含个人信息、工作履历、教育背景，并对敏感PII字段自动脱敏。
 * 处理问题：管理者需要查看某个员工的详细档案，如"看看张三的简历"；
 * 档案中包含手机号、邮箱、身份证号等PII信息，直接返回有数据泄露风险。
 * 处理方式：
 * 1. 根据employeeId参数从MockHrDataProvider查找员工；
 * 2. 未找到返回失败结果；
 * 3. PII 脱敏由 HrDataProvider 出域口统一处理（phone 掩码、email 明文、idNumber 不返回）；
 * 4. 补充部门名称等关联信息（绩效明细不得进入档案产物，R3.1）；
 * 5. 返回完整的脱敏档案数据。
 * </p>
 */
@Component
public class EmployeeProfileTool implements AgentTool {

    public static final String TOOL_NAME = "employee_profile";

    private final HrDataProvider dataProvider;

    public EmployeeProfileTool(HrDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String employeeId = request.getParameters().get("employeeId").toString();

        // R2 数据权限前置：校验目标员工是否在当前用户管辖范围内
        UserContext ctx = UserContextHolder.get();
        List<String> scope = ctx != null ? ctx.getManagedOrgIds() : null;
        if (scope == null || scope.isEmpty()) {
            return ToolResult.fail("无数据权限范围，拒绝访问员工档案");
        }

        Map<String, Object> employee = dataProvider.findEmployeeById(employeeId);
        if (employee == null) {
            return ToolResult.fail("Employee not found: " + employeeId);
        }

        // R2：员工所属部门必须在管辖范围内
        String empDeptId = String.valueOf(employee.get("deptId"));
        if (!scope.contains(empDeptId)) {
            return ToolResult.fail("无权访问该员工档案: " + employeeId);
        }

        Map<String, Object> profile = new LinkedHashMap<>(employee);
        // PII 脱敏已在 HrDataProvider 出域口统一完成（phone 掩码、email 明文、idNumber 不返回，R3）

        // 补充部门名称
        String deptId = employee.get("deptId").toString();
        Map<String, Object> dept = dataProvider.findDepartmentById(deptId);
        if (dept != null) {
            profile.put("deptName", dept.get("deptName"));
        }

        return ToolResult.success(profile);
    }
}
