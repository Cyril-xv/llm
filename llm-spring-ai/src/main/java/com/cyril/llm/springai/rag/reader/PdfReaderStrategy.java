package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * PDF 读取策略 —— 按页切分
 *
 * PagePdfDocumentReader 的核心配置：
 * - pageTopMargin / pageBottomMargin：裁剪页眉页脚区域（单位是点，1点≈0.35mm）
 * - pagesPerDocument：每页单独生成一个 Document（=1 时每页一个）
 * - ExtractedTextFormatter：进一步控制每页文本的格式化
 *
 * 还有个 ParagraphPdfDocumentReader 是按段落切分，更适合语义检索场景，
 * 但对 PDF 本身的排版质量要求更高。
 */
@Component
public class PdfReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        return file.getName().toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(50)
                .withPageBottomMargin(50)
                .withPagesPerDocument(1)
                .withPageExtractedTextFormatter(
                        new ExtractedTextFormatter.Builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build()
                )
                .build();

        Resource resource = new FileSystemResource(file);
        return new PagePdfDocumentReader(resource, config).get();
    }
}
