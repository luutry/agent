package com.luutry.agent.memory;

import java.util.List;

/**
 * 会话历史读取服务：向指代消解等消费方按需提供近期用户查询轮次。
 * <p>
 * 存在的意义：把「读会话记忆」收敛成独立职责，由用到历史的一层自行注入、按需拉取，
 * 而不是让 Controller 先捞出来再当参数层层下传。
 * </p>
 */
public interface ConversationHistoryService {

    /**
     * 返回指定会话最近的用户查询轮次，最新在前。
     * <p>
     * 契约：只保留最近 {@link com.luutry.agent.common.constant.AgentConstants#COREFERENCE_HISTORY_WINDOW} 轮；
     * sessionId 为空、无历史或底层读取失败时，一律降级返回空列表，不抛异常。
     * </p>
     *
     * @param sessionId 会话标识
     * @return 用户查询文本列表（最新在前），无数据时为空列表；不会返回 null
     */
    List<String> recentUserQueries(String sessionId);
}
