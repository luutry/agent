package com.luutry.agent.critique.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反思校验判定枚举，表示反思校验的结果。
 * <p>
 * 用途：对Agent回答经过验证检查后的质量分类。
 * 处理问题：需要标准化的判定结果来决定是通过、警告还是拒绝回答。
 * 处理方式：三个级别 - PASS（所有检查通过）、WARNING（检测到轻微问题）、
 * REJECT（检测到严重幻觉或不忠实内容）。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum CritiqueVerdict {

    /**
     * 回答通过所有验证检查，置信度足够
     */
    PASS("pass", "All checks passed"),

    /**
     * 回答存在轻微问题（部分幻觉、缺失上下文等）
     * 但基本可接受，带有风险标志
     */
    WARNING("warning", "Minor issues detected, use with caution"),

    /**
     * 回答包含严重幻觉或完全不忠实于上下文。
     * 应被拒绝并替换为安全回答。
     */
    REJECT("reject", "Severe issues detected, answer rejected");

    private final String code;
    private final String description;
}
