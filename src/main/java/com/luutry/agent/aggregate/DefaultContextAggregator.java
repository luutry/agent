package com.luutry.agent.aggregate;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.tool.model.ToolResult;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ContextAggregator默认实现。
 * <p>
 * 用途：将多源数据（Tool、RAG、Memory、对话）合并为统一上下文。
 * 处理问题：不同数据源的原始数据格式和优先级不同；
 * 需要转换为通用模型，进行优先级排序、去重和Token预算控制。
 * 处理方式：4步流水线：
 * 1. 将每个数据源转换为ContextSegment列表
 * 2. 按DataSource优先级排序（TOOL > RAG > MEMORY），再按分数降序
 * 3. 使用Jaccard相似度去重，超过阈值的视为重复
 * 4. 裁剪以适应MAX_TOKEN_BUDGET
 * </p>
 */
@Service
public class DefaultContextAggregator implements ContextAggregator {

    @Override
    public AggregatedContext aggregate(
            List<ToolResult> toolResults,
            List<RetrievalResult> ragResults,
            List<MemoryEntry> memoryEntries,
            List<ConversationTurn> recentTurns) {

        List<ContextSegment> allSegments = new ArrayList<>();

        allSegments.addAll(convertToolResults(toolResults));
        allSegments.addAll(convertRagResults(ragResults));
        allSegments.addAll(convertMemoryEntries(memoryEntries));
        allSegments.addAll(convertConversationTurns(recentTurns));

        return aggregateSegments(allSegments);
    }

    @Override
    public AggregatedContext aggregateSegments(List<ContextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return AggregatedContext.builder()
                    .segments(Collections.emptyList())
                    .totalTokens(0)
                    .provenanceList(Collections.emptyList())
                    .sourceDistribution(Collections.emptyMap())
                    .truncated(false)
                    .build();
        }

        List<ContextSegment> sorted = sortByPriorityAndScore(new ArrayList<>(segments));
        List<ContextSegment> deduplicated = deduplicate(sorted);
        return trimToBudget(deduplicated);
    }

    /**
     * 将ToolResult列表转换为ContextSegment列表。
     */
    private List<ContextSegment> convertToolResults(List<ToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContextSegment> segments = new ArrayList<>();
        for (ToolResult result : toolResults) {
            if (!result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                continue;
            }

            String content = formatToolResult(result);
            if (content.isBlank()) {
                continue;
            }

            segments.add(ContextSegment.fromTool(
                    content,
                    1.0,
                    "Tool:" + inferToolName(result)
            ));
        }
        return segments;
    }

    /**
     * 将RAG RetrievalResult列表转换为ContextSegment列表。
     */
    private List<ContextSegment> convertRagResults(List<RetrievalResult> ragResults) {
        if (ragResults == null || ragResults.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContextSegment> segments = new ArrayList<>();
        for (RetrievalResult result : ragResults) {
            TextChunk chunk = result.getChunk();
            if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            String provenance = result.getProvenance() != null
                    ? result.getProvenance()
                    : RetrievalResult.buildProvenance(result.getDocTitle(), result.getDocVersion(), null);

            segments.add(ContextSegment.fromRag(
                    chunk.getContent(),
                    result.getScore(),
                    provenance
            ));
        }
        return segments;
    }

    /**
     * 将MemoryEntry列表转换为ContextSegment列表。
     */
    private List<ContextSegment> convertMemoryEntries(List<MemoryEntry> memoryEntries) {
        if (memoryEntries == null || memoryEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContextSegment> segments = new ArrayList<>();
        for (MemoryEntry entry : memoryEntries) {
            if (entry.isExpired() || entry.getContent() == null || entry.getContent().isBlank()) {
                continue;
            }

            String provenance = entry.getTag() != null
                    ? "Memory:" + entry.getTag()
                    : "Memory:" + entry.getType().name();

            segments.add(ContextSegment.fromMemory(
                    entry.getContent(),
                    entry.getDecayScore(),
                    provenance
            ));
        }
        return segments;
    }

    /**
     * 将近期对话轮次列表转换为ContextSegment列表。
     */
    private List<ContextSegment> convertConversationTurns(List<ConversationTurn> recentTurns) {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContextSegment> segments = new ArrayList<>();
        for (ConversationTurn turn : recentTurns) {
            String content = formatConversationTurn(turn);
            if (content.isBlank()) {
                continue;
            }

            segments.add(ContextSegment.fromMemory(
                    content,
                    0.5,
                    "Conversation:turn_" + turn.getTurnNumber()
            ));
        }
        return segments;
    }

    /**
     * 按DataSource优先级（数值越小优先级越高）排序，再按分数降序。
     */
    private List<ContextSegment> sortByPriorityAndScore(List<ContextSegment> segments) {
        segments.sort(Comparator
                .comparingInt((ContextSegment s) -> s.getSource().getPriority())
                .thenComparing(Comparator.comparingDouble(ContextSegment::getScore).reversed()));
        return segments;
    }

    /**
     * 使用Jaccard相似度对片段去重。
     * 发现重复时保留分数较高的那个。
     */
    private List<ContextSegment> deduplicate(List<ContextSegment> sortedSegments) {
        if (sortedSegments.size() <= 1) {
            return sortedSegments;
        }

        List<ContextSegment> result = new ArrayList<>();
        result.add(sortedSegments.get(0));

        for (int i = 1; i < sortedSegments.size(); i++) {
            ContextSegment current = sortedSegments.get(i);
            boolean isDuplicate = false;

            for (int j = 0; j < result.size(); j++) {
                ContextSegment existing = result.get(j);
                double similarity = computeJaccardSimilarity(existing.getContent(), current.getContent());

                if (similarity >= AgentConstants.CONTEXT_DEDUP_THRESHOLD) {
                    isDuplicate = true;
                    if (current.getScore() > existing.getScore()) {
                        result.set(j, current);
                    }
                    break;
                }
            }

            if (!isDuplicate) {
                result.add(current);
            }
        }

        return result;
    }

    /**
     * 裁剪片段以适应Token预算。
     */
    private AggregatedContext trimToBudget(List<ContextSegment> segments) {
        int budget = AgentConstants.MAX_TOKEN_BUDGET;
        List<ContextSegment> trimmed = new ArrayList<>();
        List<String> provenanceList = new ArrayList<>();
        int totalTokens = 0;
        boolean wasTruncated = false;

        for (ContextSegment segment : segments) {
            if (trimmed.size() >= AgentConstants.CONTEXT_MAX_SEGMENTS) {
                wasTruncated = true;
                break;
            }

            int segmentTokens = segment.getTokenCount();
            if (segmentTokens == 0) {
                segment.recalculateTokens();
                segmentTokens = segment.getTokenCount();
            }

            if (totalTokens + segmentTokens > budget) {
                String truncatedContent = TokenEstimator.truncateToFit(
                        segment.getContent(),
                        budget - totalTokens
                );
                if (truncatedContent != null && !truncatedContent.isBlank()) {
                    ContextSegment truncatedSegment = ContextSegment.builder()
                            .content(truncatedContent)
                            .source(segment.getSource())
                            .score(segment.getScore())
                            .tokenCount(TokenEstimator.estimate(truncatedContent))
                            .provenance(segment.getProvenance())
                            .timestamp(segment.getTimestamp())
                            .build();
                    trimmed.add(truncatedSegment);
                    provenanceList.add(segment.getProvenance());
                    totalTokens += truncatedSegment.getTokenCount();
                }
                wasTruncated = true;
                break;
            }

            trimmed.add(segment);
            provenanceList.add(segment.getProvenance());
            totalTokens += segmentTokens;
        }

        Map<DataSource, Integer> distribution = trimmed.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ContextSegment::getSource,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.counting(),
                                Long::intValue
                        )
                ));

        return AggregatedContext.builder()
                .segments(trimmed)
                .totalTokens(totalTokens)
                .provenanceList(provenanceList)
                .sourceDistribution(distribution)
                .truncated(wasTruncated)
                .build();
    }

    /**
     * 基于字符bigram计算两个字符串的Jaccard相似度。
     *
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return 相似度分数，范围[0.0, 1.0]
     */
    private double computeJaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isBlank() || s2.isBlank()) {
            return 0.0;
        }

        java.util.Set<String> bigrams1 = getBigrams(s1);
        java.util.Set<String> bigrams2 = getBigrams(s2);

        if (bigrams1.isEmpty() && bigrams2.isEmpty()) {
            return 1.0;
        }
        if (bigrams1.isEmpty() || bigrams2.isEmpty()) {
            return 0.0;
        }

        long intersectionSize = bigrams1.stream().filter(bigrams2::contains).count();
        long unionSize = bigrams1.size() + bigrams2.size() - intersectionSize;

        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }

    /**
     * 从字符串中提取字符bigram。
     */
    private java.util.Set<String> getBigrams(String text) {
        java.util.Set<String> bigrams = new java.util.HashSet<>();
        String normalized = text.replaceAll("\\s+", "").toLowerCase();
        for (int i = 0; i < normalized.length() - 1; i++) {
            bigrams.add(normalized.substring(i, i + 2));
        }
        return bigrams;
    }

    /**
     * 将ToolResult格式化为可读文本内容。
     */
    private String formatToolResult(ToolResult result) {
        if (result.getData() == null || result.getData().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : result.getData().entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=");
            appendValue(sb, entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 递归展开值：List/Map 逐条写出内部字段，保留真实数值与实体名，
     * 避免压成 "[N items]" 导致校验方丢失全部数据（两份真相 P0-3）。
     */
    private void appendValue(StringBuilder sb, Object value) {
        if (value instanceof List<?> list) {
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                appendValue(sb, list.get(i));
            }
            sb.append("]");
        } else if (value instanceof Map<?, ?> map) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(e.getKey()).append(":");
                appendValue(sb, e.getValue());
            }
            sb.append("}");
        } else {
            sb.append(value);
        }
    }

    /**
     * 将对话轮次格式化为可读文本内容。
     */
    private String formatConversationTurn(ConversationTurn turn) {
        StringBuilder sb = new StringBuilder();
        if (turn.getUserMessage() != null && !turn.getUserMessage().isBlank()) {
            sb.append("Q: ").append(turn.getUserMessage());
        }
        if (turn.getAssistantMessage() != null && !turn.getAssistantMessage().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("A: ").append(turn.getAssistantMessage());
        }
        return sb.toString();
    }

    /**
     * 从ToolResult数据中推断工具名称（尽力而为）。
     */
    private String inferToolName(ToolResult result) {
        if (result.getData() != null && result.getData().containsKey("toolName")) {
            return result.getData().get("toolName").toString();
        }
        return "unknown_tool";
    }
}
