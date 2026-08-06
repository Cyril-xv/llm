package com.cyril.llm.springai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    public List<Document> search(String query, int topK) {
        SearchRequest build = SearchRequest.builder().query(query).topK(topK).build();
        List<Document> documents = vectorStore.similaritySearch(build);
        log.info("SearchRequest:{} | doucuments:{}",build,documents);
        return documents;
    }
}
