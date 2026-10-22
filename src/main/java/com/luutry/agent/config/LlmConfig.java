package com.luutry.agent.config;

import com.luutry.agent.preprocessor.resolver.LlmCoreferenceResolver;
import com.luutry.agent.preprocessor.resolver.ResolvePolicy;
import com.luutry.agent.preprocessor.router.EmbeddingIntentClassifier;
import com.luutry.agent.preprocessor.router.IntentClassificationPolicy;
import com.luutry.agent.preprocessor.router.IntentClassifier;
import com.luutry.agent.preprocessor.router.IntentWeightsLoader;
import com.luutry.agent.preprocessor.router.KeywordIntentClassifier;
import com.luutry.agent.llm.LlmService;
import com.luutry.agent.llm.QwenLlmService;
import com.luutry.agent.rag.vectorstore.DashScopeEmbeddingService;
import com.luutry.agent.rag.vectorstore.EmbeddingService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Qwen（DashScope）大模型集成配置类。
 * <p>
 * 将 LLM 提供商的所有配置（API 密钥、模型名称、端点地址）集中管理，
 * 通过 application.yml 中 "llm.qwen.*" 属性驱动。
 * </p>
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "llm.qwen")
public class LlmConfig {

    private String apiKey;
    private String modelName;
    private String embeddingModelName = "text-embedding-v3";
    private String baseUrl;
    private double temperature = 0.7;
    private int maxTokens = 2048;
    private int timeoutSeconds = 60;

    @Bean
    public ChatModel chatModel() {
        log.info("Initializing Qwen ChatModel: baseUrl={}, model={}", baseUrl, modelName);

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        log.info("Qwen ChatModel initialized successfully");
        return model;
    }

    @Bean
    public LlmService llmService(ChatModel chatModel) {
        log.info("Initializing QwenLlmService");
        return new QwenLlmService(chatModel);
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Initializing DashScope EmbeddingModel: baseUrl={}, model={}", baseUrl, embeddingModelName);
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Bean
    public EmbeddingService embeddingService(EmbeddingModel embeddingModel) {
        log.info("Initializing DashScopeEmbeddingService");
        return new DashScopeEmbeddingService(embeddingModel);
    }

    @Bean
    public KeywordIntentClassifier keywordIntentClassifier(IntentClassificationPolicy policy) {
        log.info("Initializing KeywordIntentClassifier (fallback)");
        return new KeywordIntentClassifier(
                policy.getFallbackToolKeywords(),
                policy.getFallbackRagKeywords(),
                policy.getFallbackChitchatKeywords()
        );
    }

    @Bean
    @Primary
    public IntentClassifier intentClassifier(EmbeddingService embeddingService,
                                             IntentClassificationPolicy policy,
                                             IntentWeightsLoader weightsLoader,
                                             KeywordIntentClassifier keywordIntentClassifier) {
        log.info("Initializing EmbeddingIntentClassifier (primary)");
        return new EmbeddingIntentClassifier(embeddingService, policy, weightsLoader, keywordIntentClassifier);
    }

    @Bean
    public LlmCoreferenceResolver llmCoreferenceResolver(LlmService llmService, ResolvePolicy resolvePolicy) {
        log.info("Initializing LlmCoreferenceResolver");
        return new LlmCoreferenceResolver(llmService, resolvePolicy);
    }

    // ==================== @ConfigurationProperties 属性设置方法 ====================

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        this.embeddingModelName = embeddingModelName;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    // ==================== 属性获取方法 ====================

    public String getApiKey() {
        return apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public String getEmbeddingModelName() {
        return embeddingModelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
