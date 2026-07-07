package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 纯文本读取器
 * 适用：.txt / .text / .tex
 *
 * Spring AI 的 TextReader 非常简单，直接把文件内容读成字符串，
 * 封装成一个 Document 就返回了。不做任何分页或结构化处理。
 */
@Component
public class TextReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".text") || name.endsWith(".tex");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        TextReader textReader = new TextReader(resource);
        return textReader.get();
    }
}
