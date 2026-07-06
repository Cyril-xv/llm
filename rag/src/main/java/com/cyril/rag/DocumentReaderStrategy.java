package com.cyril.rag;


import org.springframework.ai.document.Document;
import java.io.File;
import java.io.IOException;
import java.util.List;

public interface DocumentReaderStrategy {

    // 判断读取文件支持格式
    boolean supports(File file);

    // 读取并返回列表
    List<Document> read(File file) throws IOException;

}
