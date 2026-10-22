package com.luutry.agent.preprocessor.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentWeightsLoader 单元测试。
 * 覆盖：classpath JSON 加载、空权重降级、直接构造（测试用）。
 */
class IntentWeightsLoaderTest {

    @Nested
    @DisplayName("classpath JSON 加载")
    class ClasspathLoadTests {

        @Test
        void shouldLoadWeightsFromIntentWeightsJson() {
            // intent-weights.json 在 src/main/resources/ 下，测试 classpath 包含它
            IntentWeightsLoader loader = new IntentWeightsLoader();

            assertTrue(loader.hasWeights(), "intent-weights.json 存在且含 4×1024 权重");
            assertEquals(4, loader.getWeights().size(), "权重矩阵应有 4 行");
            assertEquals(1024, loader.getWeights().get(0).size(), "每行应有 1024 维");
            assertEquals(4, loader.getBias().size(), "偏置向量应有 4 个值");
            assertEquals(167, loader.getTrainedSamples(), "训练样本数应与导出数据一致");
            assertEquals(1024, loader.getEmbeddingDim(), "向量维度应为 1024");
        }

        @Test
        void shouldHandleNonExistentFileGracefully() {
            IntentWeightsLoader loader = new IntentWeightsLoader("non-existent-file.json");

            assertFalse(loader.hasWeights(), "文件不存在时权重应为空");
            assertTrue(loader.getWeights().isEmpty(), "权重列表应为空");
            assertTrue(loader.getBias().isEmpty(), "偏置列表应为空");
        }
    }

    @Nested
    @DisplayName("直接构造（测试用）")
    class DirectConstructorTests {

        @Test
        void shouldCreateWithExplicitWeights() {
            List<List<Double>> weights = List.of(
                    List.of(1.0, 0.0, 0.0, 0.0),
                    List.of(0.0, 1.0, 0.0, 0.0),
                    List.of(0.0, 0.0, 1.0, 0.0),
                    List.of(0.0, 0.0, 0.0, 1.0)
            );
            List<Double> bias = List.of(0.1, 0.2, 0.3, 0.4);

            IntentWeightsLoader loader = new IntentWeightsLoader(weights, bias);

            assertTrue(loader.hasWeights());
            assertEquals(weights, loader.getWeights());
            assertEquals(bias, loader.getBias());
            assertEquals(4, loader.getEmbeddingDim());
        }

        @Test
        void shouldReportNoWeights_whenEmptyList() {
            IntentWeightsLoader loader = new IntentWeightsLoader(new ArrayList<>(), List.of());

            assertFalse(loader.hasWeights());
        }

        @Test
        void shouldReportNoWeights_whenLessThanFourRows() {
            List<List<Double>> weights = List.of(
                    List.of(1.0, 0.0),
                    List.of(0.0, 1.0)
            );
            IntentWeightsLoader loader = new IntentWeightsLoader(weights, List.of(0.0, 0.0));

            assertFalse(loader.hasWeights(), "不足 4 行应判定为不可用");
        }

        @Test
        void shouldReportNoWeights_whenRowEmpty() {
            List<List<Double>> weights = List.of(
                    List.of(), List.of(), List.of(), List.of()
            );
            IntentWeightsLoader loader = new IntentWeightsLoader(weights, List.of(0.0, 0.0, 0.0, 0.0));

            assertFalse(loader.hasWeights(), "行维度为空应判定为不可用");
        }
    }
}
