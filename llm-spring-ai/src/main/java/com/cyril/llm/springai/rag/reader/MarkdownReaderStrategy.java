package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Markdown 读取策略
 *
 * MarkdownDocumentReader 的核心配置：
 * - horizontalRuleCreateDocument：遇到水平线（---）时是否切分为新 Document
 * - includeCodeBlock：是否保留代码块
 * - includeBlockquote：是否保留引用块
 *
 * 对于 RAG 场景，通常建议 includeCodeBlock = false，
 * 因为代码块的语义和自然语言差异很大，混在一起会影响向量检索的精度。
 * 但如果你做的是代码知识库，那就应该保留。
 */
@Component
public class MarkdownReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        return file.getName().toLowerCase().endsWith(".md");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("filename", file.getName())
                .build();

        Resource resource = new FileSystemResource(file);
        return new MarkdownDocumentReader(resource, config).get();
    }
}
