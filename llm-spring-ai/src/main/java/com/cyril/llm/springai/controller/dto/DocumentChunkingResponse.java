package com.cyril.llm.springai.controller.dto;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /rag/split 的可观察响应，把分片结果转换成便于查看统计、顺序、长度和 metadata 的教学格式。
 */
public class DocumentChunkingResponse {

    private final String splitter = "TokenTextSplitter";
    private final int requestedChunkSizeTokens;
    private final int sourceDocumentCount;
    private final int chunkCount;
    private final List<ChunkInfo> chunks;

    private DocumentChunkingResponse(int requestedChunkSizeTokens, int sourceDocumentCount,
                                     int chunkCount, List<ChunkInfo> chunks) {
        this.requestedChunkSizeTokens = requestedChunkSizeTokens;
        this.sourceDocumentCount = sourceDocumentCount;
        this.chunkCount = chunkCount;
        this.chunks = chunks;
    }

    /**
     * 将 Spring AI 分片结果转换为可观察的响应结构。
     *
     * @param documents      分片后的文档列表
     * @param chunkSize     请求时传入的 token 预算
     * @param sourceDocCount输入清洗后的源文档数
     */
    public static DocumentChunkingResponse from(List<Document> documents, int chunkSize, int sourceDocCount) {
        List<ChunkInfo> infos = new ArrayList<>();
        int order = 0;
        if (documents != null) {
            for (Document doc : documents) {
                String text = doc.getText() != null ? doc.getText() : "";
                int charCount = text.codePointCount(0, text.length());
                Map<String, Object> meta = doc.getMetadata() != null
                        ? new LinkedHashMap<>(doc.getMetadata())
                        : Map.of();
                infos.add(new ChunkInfo(order, doc.getId(), text, charCount, meta));
                order++;
            }
        }
        return new DocumentChunkingResponse(chunkSize, sourceDocCount, infos.size(), infos);
    }

    // ---- getters ----

    public String getSplitter() {
        return splitter;
    }

    public int getRequestedChunkSizeTokens() {
        return requestedChunkSizeTokens;
    }

    public int getSourceDocumentCount() {
        return sourceDocumentCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public List<ChunkInfo> getChunks() {
        return chunks;
    }

    // ---- inner ----

    public static class ChunkInfo {
        private final int order;
        private final String id;
        private final String text;
        private final int characterCount;
        private final Map<String, Object> metadata;

        ChunkInfo(int order, String id, String text, int characterCount, Map<String, Object> metadata) {
            this.order = order;
            this.id = id;
            this.text = text;
            this.characterCount = characterCount;
            this.metadata = metadata;
        }

        public int getOrder() {
            return order;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public int getCharacterCount() {
            return characterCount;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
