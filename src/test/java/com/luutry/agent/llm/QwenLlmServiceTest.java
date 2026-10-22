package com.luutry.agent.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QwenLlmService.
 */
@ExtendWith(MockitoExtension.class)
class QwenLlmServiceTest {

    @Mock
    private ChatModel chatModel;

    private QwenLlmService llmService;

    @BeforeEach
    void setUp() {
        llmService = new QwenLlmService(chatModel);
    }

    @Test
    void generate_withSystemPromptAndUserMessage_returnsLlmResponse() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from("测试回答");
        when(mockResponse.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        String result = llmService.generate("你是一个助手", "你好");

        assertEquals("测试回答", result);
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void generate_withOnlyUserMessage_usesDefaultSystemPrompt() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from("默认系统提示回答");
        when(mockResponse.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        String result = llmService.generate("你好");

        assertEquals("默认系统提示回答", result);
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void generate_nullUserMessage_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> llmService.generate("system", null));
    }

    @Test
    void generate_chatModelThrows_wrapsInLlmServiceException() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("Connection timeout"));

        QwenLlmService.LlmServiceException exception = assertThrows(
                QwenLlmService.LlmServiceException.class,
                () -> llmService.generate("system", "test")
        );
        assertTrue(exception.getMessage().contains("Failed to generate response"));
    }

    @Test
    void generateAnswer_withObservations_callsLlmAndReturnsAnswer() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from("技术部有15人，离职率为5%");
        when(mockResponse.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        List<String> observations = List.of(
                "技术部有15人",
                "离职率为5%"
        );

        String result = llmService.generateAnswer("查看技术部人员情况", observations, "tool");

        assertEquals("技术部有15人，离职率为5%", result);
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void generateAnswer_llmFails_returnsFallbackConcatenation() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("API error"));

        List<String> observations = List.of(
                "技术部有15人",
                "离职率为5%"
        );

        String result = llmService.generateAnswer("查看技术部", observations, "tool");

        assertEquals("技术部有15人\n离职率为5%", result);
    }

    @Test
    void generateAnswer_emptyObservations_returnsFallbackMessage() {
        String result = llmService.generateAnswer("查看技术部", Collections.emptyList(), "tool");
        assertNotNull(result);
    }

    @Test
    void generateAnswer_nullQuery_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> llmService.generateAnswer(null, List.of("obs"), "tool"));
    }

    @Test
    void estimateTokens_chineseText_returnsApproximateCount() {
        String chineseText = "技术部有十五名员工";
        int tokens = llmService.estimateTokens(chineseText);
        assertTrue(tokens > 0, "Token count should be positive for Chinese text");
        assertEquals(14, tokens);
    }

    @Test
    void estimateTokens_englishText_returnsApproximateCount() {
        String englishText = "hello world test ok";
        int tokens = llmService.estimateTokens(englishText);
        assertTrue(tokens > 0, "Token count should be positive for English text");
    }

    @Test
    void estimateTokens_emptyText_returnsZero() {
        assertEquals(0, llmService.estimateTokens(""));
        assertEquals(0, llmService.estimateTokens(null));
    }

    @Test
    void estimateTokens_mixedText_returnsReasonableEstimate() {
        String mixedText = "技术部有10名员工，turnover rate 5%";
        int tokens = llmService.estimateTokens(mixedText);
        assertTrue(tokens > 0, "Token count should be positive for mixed text");
    }

    @Test
    void generateAnswer_nullIntent_treatedAsUnknown() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from("回答内容");
        when(mockResponse.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        String result = llmService.generateAnswer("测试", List.of("数据"), null);

        assertEquals("回答内容", result);
    }
}
