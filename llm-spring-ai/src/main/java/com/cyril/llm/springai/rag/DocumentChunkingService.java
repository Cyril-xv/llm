package com.cyril.llm.springai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 文档分片服务
 *
 * 基于 Spring AI {@link TokenTextSplitter} 实现本地 token 预算分片。
 * 不注入 EmbeddingModel，不接触 VectorStore —— 分片是可以脱离模型和数据库独立练习的纯确定性阶段。
 *
 * <h3>关键概念</h3>
 * <ul>
 *   <li>{@code chunkSize} 是 <b>token 预算</b>，不是 Java 字符数。splitter 内部使用本地 tokenizer 估算每片的 token 数量。</li>
 *   <li>splitter 会尝试在标点符号附近结束 chunk，所以每片的字符数不会固定相等。</li>
 *   <li>输出的每个 chunk 会自动携带 {@code parent_document_id}、{@code chunk_index}、{@code total_chunks} 等 metadata。</li>
 * </ul>
 */
@Service
public class DocumentChunkingService {

    /**
     * 将文档列表按 token 预算分片。
     *
     * @param documents 输入文档列表（通常已经过清洗），null 或空列表返回空列表
     * @param chunkSize 每个 chunk 的 token 预算，必须 > 0
     * @return 分片后的文档列表，保持源文档顺序及各源文档内部 chunk 顺序
     * @throws IllegalArgumentException 如果 chunkSize <= 0
     */
    public List<Document> split(List<Document> documents, int chunkSize) {
        if (CollectionUtils.isEmpty(documents)) {
            return List.of();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须 > 0，实际: " + chunkSize);
        }

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .build();

        return splitter.split(documents);
    }
}
