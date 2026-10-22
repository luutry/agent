package com.luutry.agent.common.model.vo;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.critique.model.CritiqueVerdict;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent处理完成后返回给客户端的对话响应VO。
 * <p>
 * 用途：封装Agent的最终回答及展示所需的元数据。
 * 处理问题：客户端不仅需要回答文本，还需要数据源引用、
 * 反思校验结果以保证透明性，以及执行追踪信息用于调试。
 * 处理方式：包含answer、sources、critique信息和可选的结构化数据。
 * </p>
 */
@Data
@Builder
public class ChatResponse {

    /**
     * Agent对用户Query的文本回答
     */
    private String answer;

    /**
     * 生成回答时使用的数据源列表
     */
    private List<DataSource> sources;

    /**
     * 反思校验结果，表示回答质量
     */
    private CritiqueVerdict verdict;

    /**
     * 回答的置信度分数（0.0到1.0）
     */
    private double confidence;

    /**
     * 反思校验过程中检测到的具体问题列表（PASS时为空）
     */
    private List<String> issues;

    /**
     * 生成此响应的任务ID
     */
    private String taskId;

    /**
     * 执行的ReAct总轮次
     */
    private int totalRounds;

    /**
     * 工具结果产生的结构化数据体
     */
    private Map<String, Object> data;

    /**
     * 创建简单的成功响应。
     */
    public static ChatResponse success(String answer, String taskId) {
        return ChatResponse.builder()
                .answer(answer)
                .taskId(taskId)
                .verdict(CritiqueVerdict.PASS)
                .confidence(1.0)
                .totalRounds(0)
                .build();
    }

    /**
     * 创建错误响应。
     */
    public static ChatResponse error(String errorMessage) {
        return ChatResponse.builder()
                .answer(errorMessage)
                .verdict(CritiqueVerdict.REJECT)
                .confidence(0.0)
                .totalRounds(0)
                .build();
    }
}
