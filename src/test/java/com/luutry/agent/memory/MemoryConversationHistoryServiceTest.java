package com.luutry.agent.memory;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MemoryConversationHistoryService 单元测试。
 * <p>
 * 覆盖：最新在前收敛、空白轮次过滤、空会话、读取失败降级、非法 sessionId 不触达存储。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MemoryConversationHistoryServiceTest {

    private static final String SESSION = "sess_1";
    private static final int WINDOW = AgentConstants.COREFERENCE_HISTORY_WINDOW;

    @Mock
    private MemoryManager memoryManager;

    private MemoryConversationHistoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryConversationHistoryService(memoryManager);
    }

    @Test
    void recentUserQueries_reversesToNewestFirst() {
        // 存储按时间顺序返回（最旧在前），服务须收敛为最新在前
        when(memoryManager.getRecentTurns(SESSION, WINDOW)).thenReturn(List.of(
                ConversationTurn.builder().turnNumber(1).userMessage("第一轮").build(),
                ConversationTurn.builder().turnNumber(2).userMessage("第二轮").build()));

        List<String> result = service.recentUserQueries(SESSION);

        assertEquals(List.of("第二轮", "第一轮"), result, "应最新在前");
    }

    @Test
    void recentUserQueries_filtersBlankAndNullUserMessages() {
        when(memoryManager.getRecentTurns(SESSION, WINDOW)).thenReturn(List.of(
                ConversationTurn.builder().userMessage("有效轮次").build(),
                ConversationTurn.builder().userMessage("  ").build(),
                ConversationTurn.builder().userMessage(null).build()));

        List<String> result = service.recentUserQueries(SESSION);

        assertEquals(List.of("有效轮次"), result, "空白/缺失 userMessage 应被过滤");
    }

    @Test
    void recentUserQueries_emptySession_returnsEmpty() {
        when(memoryManager.getRecentTurns(SESSION, WINDOW)).thenReturn(Collections.emptyList());

        assertTrue(service.recentUserQueries(SESSION).isEmpty());
    }

    @Test
    void recentUserQueries_allBlank_returnsEmpty() {
        when(memoryManager.getRecentTurns(SESSION, WINDOW)).thenReturn(List.of(
                ConversationTurn.builder().userMessage("").build()));

        assertTrue(service.recentUserQueries(SESSION).isEmpty());
    }

    @Test
    void recentUserQueries_storeThrows_degradesToEmpty() {
        // R8：读取失败降级为无历史，不向上抛
        when(memoryManager.getRecentTurns(SESSION, WINDOW)).thenThrow(new RuntimeException("memory down"));

        assertTrue(service.recentUserQueries(SESSION).isEmpty(), "读取失败应降级为空");
    }

    @Test
    void recentUserQueries_nullSessionId_returnsEmptyWithoutStoreCall() {
        assertTrue(service.recentUserQueries(null).isEmpty());
        verifyNoInteractions(memoryManager);
    }

    @Test
    void recentUserQueries_blankSessionId_returnsEmptyWithoutStoreCall() {
        assertTrue(service.recentUserQueries("  ").isEmpty());
        verifyNoInteractions(memoryManager);
    }
}
