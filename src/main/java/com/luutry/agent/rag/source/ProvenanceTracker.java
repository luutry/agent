package com.luutry.agent.rag.source;

import com.luutry.agent.rag.model.RagDocument;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG检索来源追踪器
 * <p>
 * 用途：追踪每个召回Chunk的来源文档、版本和章节，用于Agent回答时的溯源引用。
 * 处理问题：用户需要验证信息来源；Agent回答必须标明数据来自Tool业务数据、RAG文档还是记忆。
 * 对于RAG结果，需要指明具体来自哪个文档的哪个章节。
 * 处理方式：维护文档注册表（docId -> RagDocument）；构建RetrievalResult时，
 * 从注册表中补充来源元数据（文档标题、版本、章节路径）。
 * </p>
 */
@Slf4j
@Component
public class ProvenanceTracker {

    /**
     * 文档注册表：docId -> RagDocument
     */
    private final Map<String, RagDocument> documentRegistry = new ConcurrentHashMap<>();

    /**
     * 在来源追踪系统中注册文档
     *
     * @param document 待注册的文档
     */
    public void registerDocument(RagDocument document) {
        documentRegistry.put(document.getId(), document);
        log.debug("Registered document for provenance: id='{}', title='{}', version='{}'",
                document.getId(), document.getTitle(), document.getVersion());
    }

    /**
     * 使用文档注册表中的来源信息丰富RetrievalResult
     *
     * @param result 待丰富的检索结果
     * @return 填充了来源字段的检索结果
     */
    public RetrievalResult enrichProvenance(RetrievalResult result) {
        TextChunk chunk = result.getChunk();
        RagDocument doc = documentRegistry.get(chunk.getDocId());

        if (doc != null) {
            result.setDocTitle(doc.getTitle());
            result.setDocVersion(doc.getVersion());
            result.setProvenance(RetrievalResult.buildProvenance(
                    doc.getTitle(), doc.getVersion(), chunk.getSectionPath()));
        } else {
            result.setDocTitle("Unknown");
            result.setDocVersion("");
            result.setProvenance(RetrievalResult.buildProvenance(
                    "Unknown", "", chunk.getSectionPath()));
        }

        return result;
    }

    /**
     * 获取Chunk的来源文档
     *
     * @param docId 文档标识符
     * @return 来源文档，未注册时返回null
     */
    public RagDocument getDocument(String docId) {
        return documentRegistry.get(docId);
    }

    /**
     * 获取已注册文档总数
     *
     * @return 文档数量
     */
    public int getDocumentCount() {
        return documentRegistry.size();
    }

    /**
     * 清除所有已注册文档
     */
    public void clear() {
        documentRegistry.clear();
    }
}
