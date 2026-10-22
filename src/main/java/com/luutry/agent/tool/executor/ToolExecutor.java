package com.luutry.agent.tool.executor;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.observability.trace.AgentTrace;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import com.luutry.agent.tool.exception.ToolExecutionException;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具执行引擎
 * <p>
 * 用途：统一执行AgentTool，提供超时控制、重试机制和简易熔断能力。
 * 处理问题：工具执行可能耗时过长或偶发失败，直接调用会导致整个Agent链路阻塞或失败；
 * 需要统一的超时截断、失败重试和熔断保护机制。
 * 处理方式：
 * 1. 超时控制：使用CompletableFuture.orTimeout()，超时时间从AgentConstants读取（默认30s）；
 * 2. 重试机制：失败后指数退避重试，重试次数从AgentConstants读取（默认2次）；
 * 3. 熔断保护：连续失败超过5次临时标记工具不可用，60秒后自动恢复；
 * 4. 加@AgentTrace注解，被Phase 3的AOP切面自动捕获执行日志。
 * </p>
 */
@Slf4j
@Component
public class ToolExecutor {

    /**
     * 熔断状态表：toolName -> 连续失败次数
     */
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

    /**
     * 熔断开启时间戳表：toolName -> 最后一次失败时间
     */
    private final Map<String, Long> lastFailureTimes = new ConcurrentHashMap<>();

    /**
     * 熔断失败阈值
     */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    /**
     * 熔断恢复时间（毫秒，60秒）
     */
    private static final long CIRCUIT_BREAKER_RECOVERY_MS = 60_000L;

    /**
     * 工具执行专用线程池：有界队列 + 命名守护线程，替代 ForkJoinPool.commonPool，
     * 避免一个 3s 阻塞工具占满 commonPool 拖垮 JVM 内其他并行任务（复审 N3.2）。
     * 饱和时 AbortPolicy 抛 RejectedExecutionException，由 execute() 的失败路径降级并保留可观测信号（R8）。
     */
    private static final int POOL_CORE = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int POOL_MAX = POOL_CORE * 2;
    private static final int POOL_QUEUE_CAPACITY = 128;
    private final ThreadPoolExecutor toolPool = new ThreadPoolExecutor(
            POOL_CORE, POOL_MAX, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
            new NamedDaemonThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * 执行工具，包含超时控制、重试机制和熔断保护。
     *
     * @param tool    待执行的工具实现
     * @param request 工具调用请求
     * @return 工具执行结果
     * @throws ToolExecutionException 所有重试失败后抛出
     */
    @AgentTrace(stage = "tool_execution")
    public ToolResult execute(AgentTool tool, ToolRequest request) {
        if (tool == null || request == null) {
            return ToolResult.fail("Tool and request must not be null");
        }

        String toolName = tool.getToolName();

        if (isCircuitOpen(toolName)) {
            log.warn("Circuit breaker is open for tool: {}, rejecting execution", toolName);
            return ToolResult.fail("Tool circuit breaker is open, try again later: " + toolName);
        }

        int maxRetries = AgentConstants.DEFAULT_TOOL_RETRY_COUNT;
        long timeoutMs = AgentConstants.DEFAULT_TOOL_TIMEOUT_MS;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ToolResult result = executeWithTimeout(tool, request, timeoutMs);
                onSuccess(toolName);
                return result;
            } catch (ToolExecutionException e) {
                log.warn("Tool {} execution failed on attempt {}/{}: {}",
                        toolName, attempt + 1, maxRetries + 1, e.getMessage());
                if (attempt == maxRetries) {
                    onFailure(toolName);
                    return ToolResult.fail("Tool execution failed after "
                            + (maxRetries + 1) + " attempts: " + e.getMessage());
                }
                sleepBeforeRetry(attempt);
            } catch (Exception e) {
                log.error("Unexpected error executing tool {}: {}", toolName, e.getMessage(), e);
                if (attempt == maxRetries) {
                    onFailure(toolName);
                    return ToolResult.fail("Unexpected error: " + e.getMessage());
                }
                sleepBeforeRetry(attempt);
            }
        }

        onFailure(toolName);
        return ToolResult.fail("Tool execution exhausted all retries");
    }

    /**
     * 使用CompletableFuture执行工具并控制超时
     *
     * @param tool     工具实现
     * @param request  工具请求
     * @param timeoutMs 超时时间（毫秒）
     * @return 工具结果
     * @throws ToolExecutionException 超时或执行错误时抛出
     */
    private ToolResult executeWithTimeout(AgentTool tool, ToolRequest request, long timeoutMs) {
        // 工具在 ForkJoinPool 异步线程执行，ThreadLocal 不会自动传递；
        // 显式捕获调用线程的 UserContext 并在执行线程重建，否则工具层拿不到数据权限范围（R2）。
        UserContext callerContext = UserContextHolder.get();
        CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> {
            UserContext previous = UserContextHolder.get();
            UserContextHolder.set(callerContext);
            try {
                return tool.execute(request);
            } finally {
                if (previous != null) {
                    UserContextHolder.set(previous);
                } else {
                    UserContextHolder.clear();
                }
            }
        }, toolPool);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw ToolExecutionException.timeout(tool.getToolName());
        } catch (Exception e) {
            throw new ToolExecutionException(tool.getToolName(),
                    "Execution error: " + e.getMessage(), e);
        }
    }

    /**
     * 重试前指数退避等待
     *
     * @param attempt 当前尝试次数（从0开始）
     */
    private void sleepBeforeRetry(int attempt) {
        long sleepMs = (long) Math.pow(2, attempt) * 100;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检查工具的熔断器是否开启
     *
     * @param toolName 工具名称
     * @return 熔断器开启（工具暂时不可用）时返回true
     */
    private boolean isCircuitOpen(String toolName) {
        Integer count = failureCounts.get(toolName);
        if (count == null || count < CIRCUIT_BREAKER_THRESHOLD) {
            return false;
        }
        Long lastFailure = lastFailureTimes.get(toolName);
        if (lastFailure == null) {
            return true;
        }
        if (System.currentTimeMillis() - lastFailure > CIRCUIT_BREAKER_RECOVERY_MS) {
            failureCounts.remove(toolName);
            lastFailureTimes.remove(toolName);
            return false;
        }
        return true;
    }

    /**
     * 记录成功执行，重置失败计数
     *
     * @param toolName 工具名称
     */
    private void onSuccess(String toolName) {
        failureCounts.remove(toolName);
        lastFailureTimes.remove(toolName);
    }

    /**
     * 记录失败执行，累加失败计数
     *
     * @param toolName 工具名称
     */
    private void onFailure(String toolName) {
        failureCounts.merge(toolName, 1, Integer::sum);
        lastFailureTimes.put(toolName, System.currentTimeMillis());
    }

    /**
     * 容器关闭时释放专用线程池，避免线程泄漏。
     */
    @PreDestroy
    void shutdownPool() {
        toolPool.shutdownNow();
    }

    /**
     * 命名守护线程工厂：便于线程转储定位，守护属性保证测试 JVM 可正常退出。
     */
    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "tool-exec-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
