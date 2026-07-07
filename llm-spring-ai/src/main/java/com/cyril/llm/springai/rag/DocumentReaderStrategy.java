package com.cyril.llm.springai.rag;

import org.springframework.ai.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 文档读取策略接口 —— 所有读取器都必须实现它
 *
 * 核心思想：
 * - supports() 判断"这个文件我能处理吗？"
 * - read()    执行实际的读取 + 解析，返回统一的 Document 列表
 *
 * 有了这个接口，新增一种文档类型只需要加一个实现类，
 * 不用改动任何现有代码 —— 这就是「开闭原则」。
 */
public interface DocumentReaderStrategy {

    /**
     * 判断是否支持该文件类型
     * 实现要点：根据文件扩展名来判断，不要根据文件内容（太慢）
     */
    boolean supports(File file);

    /**
     * 读取文件并返回 Document 列表
     * 一个 Document = 一段文本 + 一堆元数据（文件名、页码等）
     */
    List<Document> read(File file) throws IOException;
}
