package com.cyril.llm.springai.controller;

import com.cyril.llm.springai.controller.dto.DocumentChunkingResponse;
import com.cyril.llm.springai.rag.DocumentChunkingService;
import com.cyril.llm.springai.rag.DocumentCleaningService;
import com.cyril.llm.springai.rag.DocumentReaderStrategySelector;
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

@Slf4j
@RestController
@RequestMapping("/rag")
public class RagController {

    private final DocumentReaderStrategySelector selector;
    private final DocumentCleaningService cleaningService;
    private final DocumentChunkingService chunkingService;

    @Autowired
    public RagController(DocumentReaderStrategySelector selector,
                         DocumentCleaningService cleaningService,
                         DocumentChunkingService chunkingService) {
        this.selector = selector;
        this.cleaningService = cleaningService;
        this.chunkingService = chunkingService;
    }

    // ---- 公共阶段 ----

    /**
     * 读取并清洗 —— /rag/read 和 /rag/split 共用的编排方法。
     *
     * @param path 文件的绝对路径
     * @return 读取并清洗后的 Document 列表
     */
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

    /**
     * 读取并清洗文档
     *
     * <pre>
     * GET /rag/read?path=/path/to/file.pdf
     * </pre>
     *
     * @param path 文件的绝对路径
     * @return 读取并清洗后的 Document 列表（JSON 格式）
     */
    @GetMapping("/read")
    public List<Document> readDocument(@RequestParam("path") String path) {
        return readAndClean(path);
    }

    /**
     * 读取 → 清洗 → Token 分片
     *
     * 注意：这是纯本地计算，不调用 embedding 模型，不访问向量数据库。
     * chunkSize 是 token 预算，不是 Java 字符数。
     *
     * <pre>
     * GET /rag/split?path=/path/to/file.txt&chunkSize=80
     * </pre>
     *
     * @param path     文件的绝对路径
     * @param chunkSize 每片的 token 预算（默认 800；练习建议传较小值以观察多 chunk 效果）
     * @return 包含分片统计、每片文本、字符长度和 metadata 的响应
     */
    @GetMapping("/split")
    public DocumentChunkingResponse split(@RequestParam("path") String path,
                                          @RequestParam(name = "chunkSize", defaultValue = "800") int chunkSize) {
        List<Document> cleaned = readAndClean(path);
        List<Document> chunks = chunkingService.split(cleaned, chunkSize);
        return DocumentChunkingResponse.from(chunks, chunkSize, cleaned.size());
    }
}
