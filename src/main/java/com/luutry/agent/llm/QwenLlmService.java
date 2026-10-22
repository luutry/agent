package com.luutry.agent.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 LangChain4j ChatModel 的 Qwen LLM 服务实现。
 */
@Slf4j
public class QwenLlmService implements LlmService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个企业HR管理者智能助手，专门帮助管理者进行人事管理和团队盘点。
            核心职责：
            1. 基于提供的数据和上下文信息，准确回答HR相关问题
            2. 回答必须忠实于提供的数据，禁止编造、猜测任何数据或指标
            3. 如果信息不足，明确告知用户并建议补充查询方向
            4. 涉及人事制度问题时，引用具体制度条款
            5. 所有回答使用中文，语言简洁专业
            """;

    private static final double CHINESE_TOKEN_RATIO = 1.5;
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;

    private final ChatModel chatModel;

    public QwenLlmService(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "ChatModel must not be null");
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        Objects.requireNonNull(userMessage, "userMessage must not be null");

        String prompt = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        log.debug("LLM generate: systemPrompt length={}, userMessage length={}",
                prompt.length(), userMessage.length());

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(prompt));
            messages.add(UserMessage.from(userMessage));

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .build();
            ChatResponse response = chatModel.chat(request);
            String result = response.aiMessage().text();

            log.debug("LLM generate response: length={}", result != null ? result.length() : 0);
            return result;

        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            throw new LlmServiceException("Failed to generate response from LLM", e);
        }
    }

    @Override
    public String generate(String userMessage) {
        return generate(DEFAULT_SYSTEM_PROMPT, userMessage);
    }

    @Override
    public String generateAnswer(String query, List<String> observations, String intent) {
        Objects.requireNonNull(query, "query must not be null");

        String contextSection = buildContextSection(observations);
        String userPrompt = buildAnswerPrompt(query, contextSection, intent);

        try {
            return generate(DEFAULT_SYSTEM_PROMPT, userPrompt);
        } catch (LlmServiceException e) {
            log.warn("LLM answer generation failed, falling back to raw concatenation: {}", e.getMessage());
            return fallbackAnswer(observations);
        }
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int englishCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else if (Character.isLetterOrDigit(c)) {
                englishCount++;
            }
        }

        return (int) Math.ceil(chineseCount * CHINESE_TOKEN_RATIO + englishCount / ENGLISH_CHARS_PER_TOKEN);
    }

    private String buildContextSection(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "（无可用上下文数据）";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < observations.size(); i++) {
            sb.append("【数据").append(i + 1).append("】").append(observations.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildAnswerPrompt(String query, String context, String intent) {
        return """
                用户问题：%s
                查询意图类型：%s
                
                以下是从业务系统/知识库/记忆中获取的上下文数据：
                %s
                
                请基于以上上下文数据，准确、完整地回答用户的问题。
                要求：
                1. 仅使用提供的数据进行回答，不要编造数据
                2. 如果数据不足以完整回答，请说明哪些部分信息不足
                3. 回答使用中文，语言简洁专业
                4. 如果涉及具体数值指标，请准确引用原始数据
                """.formatted(query, intent != null ? intent : "unknown", context);
    }

    private String fallbackAnswer(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "抱歉，暂时无法获取相关信息。";
        }
        StringBuilder sb = new StringBuilder();
        for (String obs : observations) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(obs);
        }
        return sb.toString().trim();
    }

    public static class LlmServiceException extends RuntimeException {
        public LlmServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
