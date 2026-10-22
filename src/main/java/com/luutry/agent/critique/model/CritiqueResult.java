package com.luutry.agent.critique.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent回答的反思校验结果。
 * <p>
 * 用途：封装对Agent响应执行的幻觉检测、冲突检测和完整性检查的结果。
 * 处理问题：控制器层需要关于回答质量的结构化反馈，
 * 以决定是原样返回、添加警告还是完全替换。
 * 处理方式：包含判定结果（PASS/WARNING/REJECT）、总体置信度、
 * 发现的具体问题列表、每种检查类型的风险标志，以及可选的修正安全回答。
 * </p>
 */
@Data
@Builder
public class CritiqueResult {

    /**
     * 所有检查完成后的最终判定
     */
    @Builder.Default
    private CritiqueVerdict verdict = CritiqueVerdict.PASS;

    /**
     * 对回答质量的总体置信度（0.0到1.0）
     */
    @Builder.Default
    private double confidence = 1.0;

    /**
     * 反思校验过程中检测到的具体问题列表
     */
    @Builder.Default
    private List<String> issues = new ArrayList<>();

    /**
     * 幻觉分数：回答句子被上下文支持的比例（0.0到1.0）
     */
    @Builder.Default
    private double hallucinationScore = 1.0;

    /**
     * 是否检测到数据源之间的冲突
     */
    @Builder.Default
    private boolean hasConflict = false;

    /**
     * 回答是否不完整（缺失Query中的关键实体）
     */
    @Builder.Default
    private boolean incomplete = false;

    /**
     * 修正后的安全回答（当判定为REJECT时使用）
     */
    private String revisedAnswer;

    /**
     * 创建完全置信度的PASS结果。
     */
    public static CritiqueResult pass() {
        return CritiqueResult.builder()
                .verdict(CritiqueVerdict.PASS)
                .confidence(1.0)
                .build();
    }

    /**
     * 创建带修正安全回答的REJECT结果。
     */
    public static CritiqueResult reject(String reason, String revisedAnswer) {
        List<String> issues = new ArrayList<>();
        issues.add(reason);
        return CritiqueResult.builder()
                .verdict(CritiqueVerdict.REJECT)
                .confidence(0.0)
                .issues(issues)
                .revisedAnswer(revisedAnswer)
                .build();
    }

    /**
     * 检查此结果是否存在任何问题。
     */
    public boolean hasIssues() {
        return issues != null && !issues.isEmpty();
    }

    /**
     * 以不可变列表形式获取问题。
     */
    public List<String> getIssues() {
        return issues != null ? Collections.unmodifiableList(issues) : Collections.emptyList();
    }

    /**
     * 向问题列表添加一个问题。
     */
    public void addIssue(String issue) {
        if (issues == null) {
            issues = new ArrayList<>();
        }
        if (issue != null && !issue.isBlank()) {
            issues.add(issue);
        }
    }
}
