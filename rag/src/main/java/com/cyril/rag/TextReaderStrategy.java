package com.cyril.rag;

import org.apache.commons.io.FilenameUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 纯文本读取器
 */
@Component
public class TextReaderStrategy implements DocumentReaderStrategy{

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "text", "tex");

    @Override
    public boolean supports(File file) {
        if (file == null){
            return false;
        }
        String ext = FilenameUtils.getExtension(file.getName());
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    @Override
    public List<Document> read(File file) throws IOException {
        if (file == null){
            throw new UnsupportedOperationException("未读取到文件");
        }
        Resource resource = new FileSystemResource(file);
        return new TextReader(resource).get();
    }
}
