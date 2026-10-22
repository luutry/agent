package com.luutry.agent.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Agent对话API的请求DTO。
 * <p>
 * 用途：封装Agent对话端点的用户输入。
 * 处理问题：需要结构化的请求格式，包含用户身份、会话追踪以及可选的多轮对话历史。
 * 处理方式：包含query（必填）、userId、sessionId和历史对话轮次。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * 用户自然语言Query（必填）
     */
    private String query;

    /**
     * 用户标识，用于权限过滤和记忆隔离
     */
    private String userId;

    /**
     * 会话标识，用于对话连续性
     */
    private String sessionId;

    /**
     * 近期对话历史，按时间顺序排列（最旧在前），每条为之前轮次的用户消息。
     * <p>
     * 服务端会反转成"最新在前"后交给指代消解，且只取最近
     * {@code AgentConstants.COREFERENCE_HISTORY_WINDOW} 轮。
     * 该字段属于客户端不可信上下文：会话记忆中有数据时以记忆为准。
     * </p>
     */
    @Builder.Default
    private List<String> history = Collections.emptyList();

    /**
     * 校验请求是否包含必填字段。
     *
     * @return 请求有效时返回true
     */
    public boolean isValid() {
        return query != null && !query.isBlank()
                && userId != null && !userId.isBlank()
                && sessionId != null && !sessionId.isBlank();
    }
}
