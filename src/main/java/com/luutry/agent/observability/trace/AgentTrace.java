package com.luutry.agent.observability.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent链路追踪注解
 * <p>
 * 用途：标注在需要自动记录执行日志的方法上，AOP切面会自动捕获方法执行的入参、出参、耗时和异常。
 * 处理问题：各模块（Tool/RAG/Memory等）手动编写埋点代码重复且容易遗漏，
 * 需要一种声明式方式让开发者只需加注解即可自动记录执行链路。
 * 处理方式：通过AOP切面拦截标注了此注解的方法，自动创建span、记录执行日志、上报耗时和异常；
 * stage属性标识当前方法所属的Agent阶段名称。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTrace {

    /**
     * 被追踪方法所属的阶段名称（如 "tool_execution"、"rag_retrieval"）
     *
     * @return 阶段名称
     */
    String stage() default "";
}
