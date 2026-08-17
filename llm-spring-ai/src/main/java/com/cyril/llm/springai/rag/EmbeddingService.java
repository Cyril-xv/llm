package com.cyril.llm.springai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量化 + 向量存储服务
 *
 * embed()：只做向量化，不落库，用来验证/调试向量本身
 * embedAndStore()：向量化 + 存进 PGvector，真正构建索引用这个
 * search()：相似度检索
 */
@Slf4j
@Service
public class EmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ChatModel chatModel;


    /**
     * 向量化 + 存储到 PGvector
     *
     * ⚠️ 有坑：text-embedding-v4 单批次最多 10 条，
     * 如果你一次传几百个 document 给 vectorStore.add()，
     * 它内部会一口气全发给 embedding 模型，直接报"批次超限"。
     * 所以必须手动分批，每批不超过 9 个（留 1 个余量）。
     */
    public void embedAndStore(List<Document> documents) {
        if (documents == null || documents.isEmpty()){
            log.info("embedAndStore: 输入为空，跳过");
            return;
        }
        int total = documents.size();
        int batchSize = 9;
        log.info("embedAndStore: 开始，共 {} 条，每批 {} 个", total, batchSize);
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<Document> batch = documents.subList(i, end);
            log.info("embedAndStore: 入库 [{}-{}/{}]", i + 1, end, total);
            vectorStore.add(batch);
        }
        log.info("embedAndStore: 完成，共 {} 条", total);
    }

    /**
     * 相似度检索
     *
     * TODO:
     * 1. 用 SearchRequest.builder().query(query).topK(topK).build() 构造请求
     * 2. 调用 vectorStore.similaritySearch(request)
     * 3. log.info 打印结果数量和每条分数
     * 4. 返回 List<Document>
     */
    public List<Document> search(String query, int topK, String fileName) {
        SearchRequest build = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.3)
//                .filterExpression("fileName == '" + fileName + "'")
                .build();
        List<Document> documents = vectorStore.similaritySearch(build);
        log.info("SearchRequest:{} | documents:{}", build, documents.size());
        return documents;
    }

    public String retrieve(String query, int topK, String fileName) {
        // 1. 检索
        List<Document> documents = search(query, topK, fileName);

        // 2. 把检索到的文档拼成字符串，填入 {document} 占位符
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 3. PromptTemplate 负责把 {document} 和 {query} 替换成实际值
        String templateStr = """
                你是一名资深的后端程序员，请你根据rag知识库中的知识进行回答用户。
                当 rag 知识库中没有此项，你要告知用户暂无该知识。

                rag知识库：
                {document}

                用户的问题：{query}
                """;
        PromptTemplate template = new PromptTemplate(templateStr);
        Prompt prompt = template.create(Map.of("document", context, "query", query));

        // 4. 调大模型
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
