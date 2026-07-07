package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Tika 通用读取策略
 *
 * Apache Tika 是一个「万能文件解析器」，能自动检测文件类型并提取文本。
 * 支持：Word (.doc/.docx)、PPT (.ppt/.pptx)、Excel (.xls/.xlsx)、
 *       PDF、HTML、XML、EPUB 等等。
 *
 * 优点：一个 Reader 搞定几十种格式
 * 缺点：没有针对特定格式的精细化控制（不能像 PDF Reader 那样配置页眉裁剪等）
 *
 * 最佳实践：对有精细化需求的格式（如 PDF、HTML）用专用 Reader，
 * 对其他格式（如 Word、PPT）用 Tika 兜底。
 */
@Component
public class TikaReaderStrategy implements DocumentReaderStrategy {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".doc", ".docx", ".ppt", ".pptx"
    );

    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        return new TikaDocumentReader(resource).get();
    }
}
