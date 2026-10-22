package com.luutry.agent.rag.source;

import com.luutry.agent.common.util.JsonUtils;
import com.luutry.agent.rag.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 启动时把 Mock 制度库灌入 RAG 索引，使制度问答链路在没有真实知识库时可独立验证。
 * 数据来自 classpath:mock/policies.json，退场时机见 TODO-001/003，装配由 agent.rag.seed-mock-documents 控制（R11）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agent.rag", name = "seed-mock-documents",
        havingValue = "true", matchIfMissing = true)
public class MockPolicyDocumentLoader implements CommandLineRunner {

    private static final String RESOURCE_PATH = "mock/policies.json";

    private final RagService ragService;

    public MockPolicyDocumentLoader(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(String... args) {
        seed();
    }

    /**
     * 读取并摄入全部制度文档。
     *
     * @return 成功摄入的文档数；资源缺失或解析失败时返回 0
     */
    public int seed() {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<PolicyDocument> documents = JsonUtils.fromJsonList(json, PolicyDocument.class);

            int seeded = 0;
            for (PolicyDocument document : documents) {
                if (document == null || isBlank(document.getTitle()) || isBlank(document.getContent())) {
                    continue;
                }
                ragService.ingestDocument(document.getTitle(), document.getContent(), document.getCategory());
                seeded++;
            }
            log.info("Mock policy documents seeded: {} (resource={})", seeded, RESOURCE_PATH);
            return seeded;
        } catch (Throwable t) {
            // R8：知识库播种失败不得阻断启动，制度问答降级为空召回
            log.error("Failed to seed mock policy documents from {}: {}", RESOURCE_PATH, t.getMessage(), t);
            return 0;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 制度文档内嵌模型，字段与 mock/policies.json 对齐。
     */
    public static class PolicyDocument {

        private String title;
        private String category;
        private String content;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
