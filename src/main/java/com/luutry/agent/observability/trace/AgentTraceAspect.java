package com.luutry.agent.observability.trace;

import com.luutry.agent.observability.log.AgentLog;
import com.luutry.agent.observability.log.AgentLogCollector;
import com.luutry.agent.security.filter.PiiMaskerFactory;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Agent链路追踪AOP切面
 * <p>
 * 用途：自动拦截标注了@AgentTrace注解的方法，记录执行日志（入参、出参、耗时、异常）。
 * 处理问题：各模块（Tool/RAG/Memory等）手动编写埋点代码重复且容易遗漏，
 * 需要声明式自动埋点能力，开发者只需加注解即可记录执行链路。
 * 处理方式：
 * 1. 通过@Around环绕通知拦截@AgentTrace注解的方法；
 * 2. 从TraceContextHolder获取当前traceId（无则自动生成）；
 * 3. 记录方法执行前后的耗时，捕获异常信息；
 * 4. 构建AgentLog对象，通过AgentLogCollector上报；
 * 5. 异常时记录FAILED状态并重新抛出，不影响业务逻辑。
 * </p>
 */
@Slf4j
@Aspect
@Component
public class AgentTraceAspect {

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AgentLogCollector logCollector;

    public AgentTraceAspect(AgentLogCollector logCollector) {
        this.logCollector = logCollector;
    }

    /**
     * 拦截标注了@AgentTrace的方法，记录执行链路
     *
     * @param joinPoint  方法连接点
     * @param agentTrace 注解实例
     * @return 原方法的返回值
     * @throws Throwable 原方法抛出异常时
     */
    @Around("@annotation(agentTrace)")
    public Object traceMethod(ProceedingJoinPoint joinPoint, AgentTrace agentTrace) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String stageName = agentTrace.stage().isEmpty() ? methodName : agentTrace.stage();

        TraceContext traceContext = TraceContextHolder.get();
        String traceId = (traceContext != null) ? traceContext.getTraceId() : TraceIdGenerator.generateTraceId();
        String spanId = TraceIdGenerator.generateSpanId();

        String inputSummary = buildInputSummary(joinPoint);

        String status = AgentLog.STATUS_SUCCESS;
        String outputSummary = null;
        String errorMessage = null;

        try {
            Object result = joinPoint.proceed();
            outputSummary = truncate(safeMask(String.valueOf(result)));
            return result;
        } catch (Throwable t) {
            status = AgentLog.STATUS_FAILED;
            errorMessage = t.getMessage();
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;

            AgentLog agentLog = AgentLog.builder()
                    .traceId(traceId)
                    .spanId(spanId)
                    .stageName(stageName)
                    .inputSummary(inputSummary)
                    .outputSummary(outputSummary)
                    .durationMs(durationMs)
                    .status(status)
                    .errorMessage(errorMessage)
                    .timestamp(System.currentTimeMillis())
                    .build();

            logCollector.collect(agentLog);
            log.info("[TRACE] traceId={}, stage={}, status={}, duration={}ms",
                    traceId, stageName, status, durationMs);
        }
    }

    /**
     * 根据方法参数构建输入摘要。
     * 入参先脱敏再截断：先截断会把手机号截成半截，导致正则匹配不到而泄漏（R4）。
     */
    private String buildInputSummary(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i] != null ? args[i].toString() : "null");
        }
        return truncate(safeMask(sb.toString()));
    }

    /**
     * 埋点统一脱敏：任何入参/出参摘要都不得写原始 PII（R4）。
     * 脱敏失败时返回占位符（fail-closed），不写原文。
     */
    private String safeMask(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PiiMaskerFactory.maskText(value);
        } catch (Throwable t) {
            log.warn("埋点摘要脱敏失败，已丢弃摘要: {}", t.getMessage());
            return "[REDACTED]";
        }
    }

    /**
     * 截断字符串到最大摘要长度
     */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_SUMMARY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SUMMARY_LENGTH) + "...(truncated)";
    }
}
