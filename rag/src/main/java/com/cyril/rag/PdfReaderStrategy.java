package com.cyril.rag;

import org.apache.commons.io.FilenameUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class PdfReaderStrategy implements DocumentReaderStrategy {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf");

    @Override
    public boolean supports(File file) {
        if (file == null){
            return false;
        }

        String ext = FilenameUtils.getExtension(file.getName());
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

//    @Override
//    public List<Document> read(File file) throws IOException {
//        if (file == null){
//            throw new UnsupportedOperationException("未读取到文件");
//        }
//        // TODO: 构建 PdfDocumentReaderConfig，要求：
//        // 1. 顶部、底部各裁剪 50 个单位（去掉页眉页脚）
//        // 2. 每页生成一个 Document（pagesPerDocument = 1）
//        // 3. 设置 ExtractedTextFormatter，不删除顶部行
//        //
//
//
//        // 参考代码：
//        // PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
//        //     .withPageTopMargin(50)
//        //     .withPageBottomMargin(50)
//        //     .withPagesPerDocument(1)
//        //     .withPageExtractedTextFormatter(
//        //         new ExtractedTextFormatter.Builder()
//        //             .withNumberOfTopTextLinesToDelete(0)
//        //             .build()
//        //     )
//        //     .build();
//        //
//        // 然后：
//        // Resource resource = new FileSystemResource(file);
//        // return new PagePdfDocumentReader(resource, config).get();
//
//        throw new UnsupportedOperationException("TODO: 你来写");
//    }