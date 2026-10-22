package com.luutry.agent.tool.dispatcher;

import com.luutry.agent.security.auth.FunctionPermissionChecker;
import com.luutry.agent.security.auth.PermissionDeniedException;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.executor.ToolExecutor;
import com.luutry.agent.tool.model.ToolDefinition;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import com.luutry.agent.tool.registry.ToolRegistry;
import com.luutry.agent.tool.schema.ToolInputValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 工具调度器
 * <p>
 * 用途：作为工具调用的统一入口，负责权限校验、入参校验、委托执行器执行工具并返回结果。
 * 处理问题：Planner调用工具时不应关心权限校验、参数校验、超时重试等细节，
 * 需要一个调度层统一处理这些横切关注点，保证工具调用的安全性和正确性。
 * 处理方式：
 * 1. 根据toolName从Registry查找ToolDefinition，不存在则返回失败结果；
 * 2. 校验当前用户是否拥有工具所需的权限标签（白名单校验），无权限则抛出PermissionDeniedException；
 * 3. 使用ToolInputValidator校验入参是否符合inputSchema，校验失败返回错误结果；
 * 4. 委托ToolExecutor执行工具（含超时/重试/熔断），返回ToolResult。
 * </p>
 */
@Slf4j
@Component
public class ToolDispatcher {

    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final FunctionPermissionChecker permissionChecker;

    public ToolDispatcher(ToolRegistry registry,
                          ToolExecutor executor,
                          FunctionPermissionChecker permissionChecker) {
        this.registry = registry;
        this.executor = executor;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 调度工具调用请求，执行完整流水线：
     * 查找 -> 权限校验 -> 入参校验 -> 执行
     *
     * @param request 包含工具名称和参数的调用请求
     * @return 工具执行结果
     */
    public ToolResult dispatch(ToolRequest request) {
        if (request == null || request.getToolName() == null) {
            return ToolResult.fail("Tool request and tool name must not be null");
        }

        String toolName = request.getToolName();
        log.info("Dispatching tool call: {}", toolName);

        // 步骤1：查找工具定义
        Optional<ToolDefinition> defOpt = registry.getDefinition(toolName);
        if (defOpt.isEmpty()) {
            log.warn("Tool not found: {}", toolName);
            return ToolResult.fail("Tool not registered: " + toolName);
        }
        ToolDefinition definition = defOpt.get();

        // 步骤2：权限白名单校验
        checkPermissions(definition);

        // 步骤3：入参校验
        List<String> validationErrors = ToolInputValidator.validate(definition, request.getParameters());
        if (!validationErrors.isEmpty()) {
            log.warn("Tool {} input validation failed: {}", toolName, validationErrors);
            return ToolResult.fail("Input validation failed: " + String.join("; ", validationErrors));
        }

        // 步骤4：通过ToolExecutor执行
        Optional<AgentTool> toolOpt = registry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            return ToolResult.fail("Tool implementation not found: " + toolName);
        }

        return executor.execute(toolOpt.get(), request);
    }

    /**
     * 检查当前用户是否拥有工具所需的全部权限。
     * 如果未设置用户上下文（如测试场景），则跳过校验。
     *
     * @param definition 包含所需权限的工具定义
     * @throws PermissionDeniedException 用户缺少所需权限时抛出
     */
    private void checkPermissions(ToolDefinition definition) {
        if (definition.getRequiredPermissions() == null || definition.getRequiredPermissions().isEmpty()) {
            return;
        }
        if (UserContextHolder.get() == null) {
            log.debug("No user context set, skipping permission check");
            return;
        }
        for (String perm : definition.getRequiredPermissions()) {
            try {
                permissionChecker.checkPermission(perm);
            } catch (PermissionDeniedException e) {
                throw new PermissionDeniedException(
                        "User lacks required permission '" + perm + "' for tool: " + definition.getName());
            }
        }
    }
}
