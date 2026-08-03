package com.cyril.llm.springai.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档清洗服务
 *
 * 将 {@link RagController} 中原有的私有清洗逻辑抽取为可复用的公共阶段，
 * 使 /rag/read 和 /rag/split 都能复用同一清洗规则，避免后续 embedding 链路绕开清洗。
 *
 * 清洗是一个「管道」：原始文本 → 去特殊字符 → 逐行压缩空白 → 去空行 → 顺序去重 → 干净文本
 */
@Service
public class DocumentCleaningService {

    /**
     * 清洗文档列表，保持原 Controller 中 cleanDocuments 的完整行为不变。
     *
     * @param documents 原始文档列表（可为 null 或空）
     * @return 清洗后的文档列表，每条文本非空且 metadata 保留
     */
    public List<Document> clean(List<Document> documents) {
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
}
