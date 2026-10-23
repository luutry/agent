package com.luutry.agent.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent对话API的请求DTO。
 * <p>
 * 用途：封装Agent对话端点的用户输入。
 * 处理问题：需要结构化的请求格式，包含用户身份与会话追踪。
 * 处理方式：包含query（必填）、userId、sessionId；多轮历史以服务端会话记忆为准，客户端不再传历史，
 * sessionId 缺省由服务端签发。
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
     * 会话标识，用于对话连续性（可选：缺省由服务端签发并随响应返回）
     */
    private String sessionId;

    /**
     * 校验请求是否包含必填字段。
     *
     * @return 请求有效时返回true
     */
    public boolean isValid() {
        return query != null && !query.isBlank()
                && userId != null && !userId.isBlank();
    }
}
