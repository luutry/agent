package com.luutry.agent.memory;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 {@link MemoryManager} 短期记忆的会话历史读取实现。
 * <p>
 * 只负责「取原始用户轮次 + 收敛为最新在前 + 失败降级」；历史文本的 PII 清洗留在消费方
 * （指代消解器持有 QueryCleaner），避免 memory 层反向依赖 preprocessor 层。
 * </p>
 */
@Slf4j
@Service
public class MemoryConversationHistoryService implements ConversationHistoryService {

    private final MemoryManager memoryManager;

    public MemoryConversationHistoryService(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    @Override
    public List<String> recentUserQueries(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }
        int window = AgentConstants.COREFERENCE_HISTORY_WINDOW;
        try {
            List<ConversationTurn> memoryTurns = memoryManager.getRecentTurns(sessionId, window);
            if (memoryTurns == null || memoryTurns.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> turns = new ArrayList<>();
            for (ConversationTurn turn : memoryTurns) {
                if (turn != null && turn.getUserMessage() != null && !turn.getUserMessage().isBlank()) {
                    turns.add(turn.getUserMessage());
                }
            }
            if (turns.isEmpty()) {
                return Collections.emptyList();
            }
            // 存储按时间顺序返回（最旧在前），消解器约定最新在前
            Collections.reverse(turns);
            return turns;
        } catch (Throwable t) {
            // R8：读取失败降级为无历史，指代消解退化为单轮，不打挂主流程
            log.warn("读取会话记忆失败，降级为无历史: {}", t.getMessage());
            return Collections.emptyList();
        }
    }
}
