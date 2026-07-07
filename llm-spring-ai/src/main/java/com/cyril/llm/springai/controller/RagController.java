package com.cyril.llm.springai.controller;

import com.cyril.llm.springai.rag.DocumentReaderStrategySelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/rag")
public class RagController {

    private final DocumentReaderStrategySelector selector;

    @Autowired
    public RagController(DocumentReaderStrategySelector selector) {
        this.selector = selector;
    }
    /**
     * 文本清洗
     *
     * 清洗是一个「管道」：原始文本 → 去空白 → 去乱码 → 去重 → 干净文本
     * 你可以根据业务需求调整每一步的顺序或增减步骤。
     *
     * @param documents 原始文档列表
     * @return 清洗后的文档列表
     */
    private List<Document> cleanDocuments(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return List.of();
        }

        return documents.stream()
                // a. 跳过 null document / null text
                .filter(document -> document != null && document.getText() != null)

                // b/c/d/e. 清洗文本并重新构造 Document
                .map(document -> {
                    String cleanedText = Arrays.stream(
                                    document.getText()
                                            // 先去掉乱码/特殊符号，但保留换行
                                            .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "")
                                            // 按换行拆分
                                            .split("\\R")
                            )
                            // 每一行压缩空白
                            .map(line -> line.replaceAll("\\s+", " ").trim())
                            // 去掉空行
                            .filter(line -> !line.isEmpty())
                            // 用 LinkedHashSet 去重且保持顺序
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toCollection(LinkedHashSet::new),
                                    lines -> String.join("\n", lines)
                            ));

                    return new Document(cleanedText, document.getMetadata());
                })

                .collect(Collectors.toList());
    }


    /**
     * 读取文档
     *
     * 用法示例：
     * GET /rag/read?path=/Users/xxx/documents/test.pdf
     *
     * @param path 文件的绝对路径
     * @return 解析后的 Document 列表（JSON 格式）
     */
    @GetMapping("/read")
    public List<Document> readDocument(@RequestParam("path") String path) {
        // 1. 用 path 创建一个 File 对象
        File file = new File(path);
        // 2. 检查文件是否存在 && 是否真的是文件（不是目录）
        if (!file.exists() || !file.isFile()){
            throw new IllegalArgumentException("不是文件/文件不存在");
        }

        try {
            return cleanDocuments(selector.read(file));
        }catch (IOException e){
            log.error("文档读取失败, path={}", path, e);
            throw new RuntimeException("文档读取失败: " + path, e);
        }

    }
}
