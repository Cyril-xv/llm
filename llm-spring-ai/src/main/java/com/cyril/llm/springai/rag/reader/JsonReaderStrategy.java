package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * JSON 读取策略
 *
 * ⚠️ 重要限制：Spring AI 的 JsonReader 不支持嵌套字段！
 * 它只能提取 JSON 对象第一层的指定字段。
 *
 * 比如这个 JSON：
 * {"title": "xxx", "content": "yyy", "author": {"name": "zzz"}}
 *
 * 你只能提取 title 和 content，无法直接提取 author.name。
 * 如果 JSON 结构复杂（嵌套、数组嵌套对象），建议用 Jackson/Fastjson
 * 手动解析后再构造 Document。
 */
@Component
public class JsonReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        return file.getName().toLowerCase().endsWith(".json");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        JsonReader jsonReader = new JsonReader(resource, "description", "content");
        return jsonReader.get();
    }
}
