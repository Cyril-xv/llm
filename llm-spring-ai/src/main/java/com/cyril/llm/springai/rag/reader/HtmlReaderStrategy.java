package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * HTML 读取策略 —— 基于 Jsoup
 *
 * JsoupDocumentReader 的亮点：
 * - selector：用 CSS 选择器指定要提取哪些标签的内容（如 "p" 只提取段落）
 * - charset：文件编码（中文内容一定要设 UTF-8）
 * - includeLinkUrls：是否保留超链接
 * - metadataTags：提取哪些 <meta> 标签的值作为元数据
 *
 * 一个常见坑：selector 如果写得太宽（如 "body"），会把导航栏、尾部等
 * 无关内容也提取进来，影响检索质量。建议根据网页结构精细化选择。
 */
@Component
public class HtmlReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                .selector("p")
                .charset("UTF-8")
                .includeLinkUrls(true)
                .metadataTags(List.of("author", "date"))
                .additionalMetadata("filename", file.getName())
                .build();

        Resource resource = new FileSystemResource(file);
        return new JsoupDocumentReader(resource, config).get();
    }
}
