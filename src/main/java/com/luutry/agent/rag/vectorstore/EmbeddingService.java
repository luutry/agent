package com.luutry.agent.rag.vectorstore;

/**
 * 文本向量化（Embedding）服务抽象接口。
 * <p>
 * 将具体的 Embedding 模型提供商与业务逻辑解耦。
 * </p>
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量表示。
     *
     * @param text 输入文本（不应为 null 或空白）
     * @return 浮点数组，维度取决于模型配置
     * @throws EmbeddingException 调用失败时抛出
     */
    float[] embed(String text);

    /**
     * Embedding 调用异常
     */
    class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) {
            super(message);
        }

        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
