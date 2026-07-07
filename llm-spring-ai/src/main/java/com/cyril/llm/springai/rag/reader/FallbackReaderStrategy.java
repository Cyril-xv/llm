package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 兜底策略 —— 当所有专用 Reader 都不匹配时，拿 TextReader 尝试
 *
 * @Order(Ordered.LOWEST_PRECEDENCE) 确保这个兜底策略排在最后
 *
 * 为什么需要兜底？如果用户传了一个 .log 文件，前面 6 个 Reader
 * 都不认识，直接抛 "不支持的文件类型" 体验不好。兜底策略至少能
 * 把文件内容读出来，让系统不至于完全不可用。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FallbackReaderStrategy implements DocumentReaderStrategy {

    private static final Logger log = LoggerFactory.getLogger(FallbackReaderStrategy.class);

    @Override
    public boolean supports(File file) {
        // 兜底策略：永远返回 true
        // 因为其他策略都不匹配时，由它来兜底
        return true;
    }

    @Override
    public List<Document> read(File file) throws IOException {
        log.warn("没有专用读取器匹配文件 [{}]，使用 TextReader 兜底读取", file.getName());
        Resource resource = new FileSystemResource(file);
        TextReader textReader = new TextReader(resource);
        return textReader.get();
    }
}
