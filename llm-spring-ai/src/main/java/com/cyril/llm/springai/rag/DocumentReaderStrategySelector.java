package com.cyril.llm.springai.rag;


import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
public class DocumentReaderStrategySelector {

    private final List<DocumentReaderStrategy> strategies;

    public DocumentReaderStrategySelector(List<DocumentReaderStrategy> strategies) {
        this.strategies = strategies;
    }

    public DocumentReaderStrategy select(File file) {
        Objects.requireNonNull(file, "file 不能为空");

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是支持的类型");
        }

        return strategies.stream()
                .filter(strategy -> strategy.supports(file))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件类型: " + file.getName()));
    }

    public List<Document> read(File file) throws IOException {
        return select(file).read(file);
    }

}
