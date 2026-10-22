package com.luutry.agent.rag.vectorstore;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 基于 DashScope（阿里云百炼平台）OpenAI 兼容 API 的 Embedding 服务实现。
 */
@Slf4j
public class DashScopeEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public DashScopeEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel must not be null");
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new EmbeddingException("Embedding input text must not be null or blank");
        }

        try {
            Embedding embedding = embeddingModel.embed(text).content();
            float[] vector = embedding.vector();
            log.debug("Embedding 生成成功: textLength={}, vectorDim={}", text.length(), vector.length);
            return vector;
        } catch (Exception e) {
            log.error("Embedding 调用失败: {}", e.getMessage(), e);
            throw new EmbeddingException("Failed to generate embedding via DashScope API", e);
        }
    }
}
