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
 * 组织树查询工具
 * <p>
 * 用途：查询公司部门层级结构，支持按部门ID查询详情及其子部门列表。
 * 处理问题：管理者需要了解组织架构，如"技术部下面有哪些子部门"；
 * 需要支持根节点查询（不传deptId返回所有顶级部门）和指定部门下钻。
 * 处理方式：
 * 1. 从ToolRequest获取可选的deptId参数；
 * 2. 如果未传deptId，返回所有顶级部门（parentDeptId为null）；
 * 3. 如果传了deptId，查询该部门详情并附带其子部门列表；
 * 4. 每个部门节点包含deptId、deptName、headEmployeeId、headcount、currentStaff等字段；
 * 5. 返回部门树结构数据。
 * </p>
 */
@Component
public class OrgTreeTool implements AgentTool {

    public static final String TOOL_NAME = "org_tree";

    private final MockHrDataProvider dataProvider;

    public OrgTreeTool(MockHrDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> params = request.getParameters();
        String deptId = params.containsKey("deptId") && params.get("deptId") != null
                ? params.get("deptId").toString() : null;

        // R2 数据权限前置：按管辖范围过滤组织树
        UserContext ctx = UserContextHolder.get();
        List<String> scope = ctx != null ? ctx.getManagedOrgIds() : null;
        if (scope == null || scope.isEmpty()) {
            return ToolResult.fail("无数据权限范围，拒绝访问组织树");
        }

        if (deptId == null) {
            return buildRootTree(scope);
        } else {
            // R2：请求的部门必须在管辖范围内
            if (!scope.contains(deptId)) {
                return ToolResult.fail("无权访问该部门组织树: " + deptId);
            }
            return buildDeptTree(deptId, scope);
        }
    }

    private ToolResult buildRootTree(List<String> scope) {
        List<Map<String, Object>> topDepts = dataProvider.getDepartments(scope).stream()
                .filter(d -> d.get("parentDeptId") == null)
                .map(d -> enrichDeptNode(d, scope))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalTopDepartments", topDepts.size());
        data.put("departments", topDepts);

        return ToolResult.success(data);
    }

    private ToolResult buildDeptTree(String deptId, List<String> scope) {
        Map<String, Object> dept = dataProvider.findDepartmentById(deptId);
        if (dept == null) {
            return ToolResult.fail("Department not found: " + deptId);
        }

        Map<String, Object> enrichedDept = enrichDeptNode(dept, scope);

        List<Map<String, Object>> subDepts = dataProvider.getSubDepartments(deptId, scope).stream()
                .map(d -> enrichDeptNode(d, scope))
                .toList();
        enrichedDept.put("subDepartments", subDepts);

        return ToolResult.success(enrichedDept);
    }

    private Map<String, Object> enrichDeptNode(Map<String, Object> dept, List<String> scope) {
        Map<String, Object> node = new LinkedHashMap<>(dept);

        String deptId = dept.get("deptId").toString();
        // 按管辖范围过滤后的员工计数
        long staffCount = dataProvider.getEmployees(scope).stream()
                .filter(e -> deptId.equals(e.get("deptId")))
                .count();
        node.put("actualStaffCount", staffCount);

        String headId = dept.get("headEmployeeId") != null
                ? dept.get("headEmployeeId").toString() : null;
        if (headId != null) {
            Map<String, Object> head = dataProvider.findEmployeeById(headId);
            if (head != null) {
                node.put("headName", head.get("name"));
            }
        }

        return node;
    }
}
