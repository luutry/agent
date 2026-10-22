package com.luutry.agent.preprocessor.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 意图分类器权重加载器：从 classpath JSON 文件读取离线训练的权重矩阵。
 * <p>
 * 权重由 Python 训练脚本（python/03_export.py）导出为 intent-weights.json，
 * 放在 src/main/resources/ 下随 jar 打包。启动时一次性加载到内存，
 * 避免将 4×1024 浮点数据塞进 application.yml。
 * </p>
 */
@Slf4j
@Component
public class IntentWeightsLoader {

    private static final String DEFAULT_RESOURCE_PATH = "intent-weights.json";

    @Getter
    private final List<List<Double>> weights;

    @Getter
    private final List<Double> bias;

    @Getter
    private final int trainedSamples;

    @Getter
    private final int embeddingDim;

    /**
     * 默认构造：从 classpath:intent-weights.json 加载。
     * 文件不存在时权重为空，EmbeddingIntentClassifier 将降级到种子/关键词路径。
     */
    public IntentWeightsLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    /**
     * 指定资源路径构造（便于测试和自定义部署）。
     */
    public IntentWeightsLoader(String resourcePath) {
        List<List<Double>> loadedWeights = new ArrayList<>();
        List<Double> loadedBias = new ArrayList<>();
        int samples = 0;
        int dim = 0;

        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.info("权重文件 {} 不存在，将使用种子/关键词降级路径", resourcePath);
            } else {
                ObjectMapper mapper = new ObjectMapper();
                try (InputStream is = resource.getInputStream()) {
                    JsonNode root = mapper.readTree(is);

                    JsonNode weightsNode = root.get("weights");
                    if (weightsNode != null && weightsNode.isArray()) {
                        for (JsonNode row : weightsNode) {
                            List<Double> weightRow = new ArrayList<>();
                            for (JsonNode val : row) {
                                weightRow.add(val.asDouble());
                            }
                            loadedWeights.add(weightRow);
                        }
                    }

                    JsonNode biasNode = root.get("bias");
                    if (biasNode != null && biasNode.isArray()) {
                        for (JsonNode val : biasNode) {
                            loadedBias.add(val.asDouble());
                        }
                    }

                    JsonNode metaNode = root.get("metadata");
                    if (metaNode != null) {
                        samples = metaNode.path("trained_samples").asInt(0);
                        dim = metaNode.path("embedding_dim").asInt(0);
                    }
                }
                log.info("意图分类权重加载成功: {} 行 × {} 维, 训练样本={}, bias={}",
                        loadedWeights.size(),
                        loadedWeights.isEmpty() ? 0 : loadedWeights.get(0).size(),
                        samples, loadedBias.size());
            }
        } catch (Exception e) {
            log.warn("意图分类权重加载失败，将降级到种子/关键词路径: {}", e.getMessage());
        }

        this.weights = loadedWeights;
        this.bias = loadedBias;
        this.trainedSamples = samples;
        this.embeddingDim = dim;
    }

    /**
     * 权重是否可用（4 行非空矩阵）。
     */
    public boolean hasWeights() {
        return weights.size() == 4 && !weights.get(0).isEmpty();
    }

    /**
     * 直接指定权重构造（测试专用，避免 classpath 文件依赖）。
     */
    public IntentWeightsLoader(List<List<Double>> weights, List<Double> bias) {
        this.weights = weights;
        this.bias = bias;
        this.trainedSamples = 0;
        this.embeddingDim = weights.isEmpty() ? 0 : weights.get(0).size();
    }
}
