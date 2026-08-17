package com.cyril.llm.springai.controller;

import com.cyril.llm.springai.controller.dto.DocumentChunkingResponse;
import com.cyril.llm.springai.rag.DocumentChunkingService;
import com.cyril.llm.springai.rag.DocumentCleaningService;
import com.cyril.llm.springai.rag.DocumentReaderStrategySelector;
import com.cyril.llm.springai.rag.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/rag")
public class RagController {

    private final DocumentReaderStrategySelector selector;
    private final DocumentCleaningService cleaningService;
    private final DocumentChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    @Autowired
    public RagController(DocumentReaderStrategySelector selector,
                         DocumentCleaningService cleaningService,
                         DocumentChunkingService chunkingService,
                         EmbeddingService embeddingService) {
        this.selector = selector;
        this.cleaningService = cleaningService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    // ---- 公共阶段 ----

    private List<Document> readAndClean(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("不是文件/文件不存在");
        }

        try {
            return cleaningService.clean(selector.read(file));
        } catch (IOException e) {
            log.error("文档读取失败, path={}", path, e);
            throw new RuntimeException("文档读取失败: " + path, e);
        }
    }

    // ---- endpoints ----

    @GetMapping("/read")
    public List<Document> readDocument(@RequestParam("path") String path) {
        return readAndClean(path);
    }

    @GetMapping("/split")
    public DocumentChunkingResponse split(@RequestParam("path") String path,
                                          @RequestParam(name = "chunkSize", defaultValue = "800") int chunkSize) {
        List<Document> cleaned = readAndClean(path);
        List<Document> chunks = chunkingService.split(cleaned, chunkSize);
        return DocumentChunkingResponse.from(chunks, chunkSize, cleaned.size());
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam("path") String path,
                                     @RequestParam(name = "chunkSize", defaultValue = "200") int chunkSize) {
        List<Document> cleaned = readAndClean(path);
        List<Document> chunks = chunkingService.split(cleaned, chunkSize);
        embeddingService.embedAndStore(chunks);
        return Map.of("success", true, "chunks", chunks.size(), "chunkSize", chunkSize);
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam("query") String query,
                                 @RequestParam(name = "fileName", required = false, defaultValue = "") String fileName) {
        return embeddingService.search(query, 3, fileName);
    }

    @GetMapping("/retrieve")
    public String retrieve(@RequestParam("query") String query,
                           @RequestParam(name = "fileName", required = false, defaultValue = "") String fileName) {
        return embeddingService.retrieve(query, 3, fileName);
    }
}
