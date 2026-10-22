package com.luutry.agent.tool.registry;

import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.builtin.*;
import com.luutry.agent.tool.model.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具自动注册器
 * <p>
 * 用途：Spring容器启动完成后，自动将所有AgentTool实现类注册到ToolRegistry中，并附带各自的ToolDefinition元信息。
 * 处理问题：每个AgentTool实现类需要手动调用registry.register()注册，容易遗漏；
 * 需要一种自动化机制在启动时统一注册所有工具及其元信息。
 * 处理方式：
 * 1. 实现CommandLineRunner接口，Spring启动完成后自动执行；
 * 2. 通过构造注入获取所有AgentTool Bean列表；
 * 3. 根据工具类名匹配，为每个内置工具创建对应的ToolDefinition（含inputSchema和requiredPermissions）；
 * 4. 调用registry.register()完成注册。
 * </p>
 */
@Slf4j
@Component
@Order(1)
public class ToolAutoRegistrar implements CommandLineRunner {

    private final ToolRegistry registry;
    private final List<AgentTool> tools;

    public ToolAutoRegistrar(ToolRegistry registry, List<AgentTool> tools) {
        this.registry = registry;
        this.tools = tools;
    }

    @Override
    public void run(String... args) {
        for (AgentTool tool : tools) {
            ToolDefinition definition = buildDefinition(tool);
            if (definition != null) {
                registry.register(tool, definition);
            }
        }
        log.info("Auto-registered {} agent tools", tools.size());
    }

    private ToolDefinition buildDefinition(AgentTool tool) {
        return switch (tool.getToolName()) {
            case EmployeeSearchTool.TOOL_NAME -> buildEmployeeSearchDefinition();
            case TeamMetricsTool.TOOL_NAME -> buildTeamMetricsDefinition();
            case EmployeeProfileTool.TOOL_NAME -> buildEmployeeProfileDefinition();
            case PerformanceRecordTool.TOOL_NAME -> buildPerformanceRecordDefinition();
            case OrgTreeTool.TOOL_NAME -> buildOrgTreeDefinition();
            default -> {
                log.warn("No definition builder for tool: {}", tool.getToolName());
                yield null;
            }
        };
    }

    private ToolDefinition buildEmployeeSearchDefinition() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("name", "string, optional - fuzzy match employee name");
        schema.put("deptId", "string, optional - exact match department ID");
        schema.put("position", "string, optional - fuzzy match position name");
        schema.put("performanceGrade", "string, optional - exact match grade (A/B+/B/C)");
        schema.put("employmentType", "string, optional - exact match type (full_time/intern/contractor)");
        schema.put("minTenure", "int, optional - minimum tenure years");
        schema.put("maxTenure", "int, optional - maximum tenure years");
        schema.put("jobLevel", "string, optional - fuzzy match job level");

        return ToolDefinition.builder()
                .name(EmployeeSearchTool.TOOL_NAME)
                .description("Search employees by multiple criteria: name, department, position, "
                        + "performance grade, tenure range, employment type, job level")
                .inputSchema(schema)
                .requiredPermissions(Set.of("employee:view"))
                .build();
    }

    private ToolDefinition buildTeamMetricsDefinition() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("deptId", "string, optional - department ID to query metrics for; "
                + "omit to get all departments");

        return ToolDefinition.builder()
                .name(TeamMetricsTool.TOOL_NAME)
                .description("Aggregate team HR metrics: headcount usage rate, turnover rate, "
                        + "probation pass rate, performance distribution, average tenure")
                .inputSchema(schema)
                .requiredPermissions(Set.of("team:view"))
                .build();
    }

    private ToolDefinition buildEmployeeProfileDefinition() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("employeeId", "string, required - target employee ID");

        return ToolDefinition.builder()
                .name(EmployeeProfileTool.TOOL_NAME)
                .description("Query detailed employee profile including personal info, "
                        + "work history, education, certificates with PII masking")
                .inputSchema(schema)
                .requiredPermissions(Set.of("employee:view"))
                .build();
    }

    private ToolDefinition buildPerformanceRecordDefinition() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("employeeId", "string, required - target employee ID");
        schema.put("cycle", "string, optional - performance cycle (e.g., 2025-H1, 2025-H2)");

        return ToolDefinition.builder()
                .name(PerformanceRecordTool.TOOL_NAME)
                .description("Query employee performance records including score, grade, "
                        + "comments, and appeal status, optionally filtered by cycle")
                .inputSchema(schema)
                .requiredPermissions(Set.of("performance:view"))
                .build();
    }

    private ToolDefinition buildOrgTreeDefinition() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("deptId", "string, optional - department ID to query; "
                + "omit to get all top-level departments");

        return ToolDefinition.builder()
                .name(OrgTreeTool.TOOL_NAME)
                .description("Query organization tree structure including department hierarchy, "
                        + "head count, staff count, and department head info")
                .inputSchema(schema)
                .requiredPermissions(Set.of("org:view"))
                .build();
    }
}
