# 🧬 向量模型 & 向量数据库 实战练习任务

> 任务驱动学习 —— 你写代码，我验收
> 前置：已完成《文档预处理》练习（`DOC-PREPROCESS-PRACTICE-TASKS.md`），现在手里有一批干净的 `List<Document>`

---

## 🎯 练完你能掌握什么

- `EmbeddingModel` 接口怎么用，向量化的本质是什么
- Spring AI 怎么接入 PGvector，`VectorStore` 这个 bean 是怎么来的
- 为什么向量化要分批处理，`max-document-batch-size` 到底控制的是哪一段
- 最终跑通「读取 → 清洗 → 分片 → 向量化 → 入库 → 检索」的完整 RAG 索引链路

---

## 📌 练习顺序

```
任务 0（环境已就位，你要做的事）
  ↓
任务 1（加依赖）
  ↓
任务 2（写配置）
  ↓
任务 3（写 EmbeddingService）
  ↓
任务 4（Controller 里串联 split + embed）
  ↓
任务 5（验收：查库 + 检索）
```

> ⚠️ 和上次一样：所有 TODO 必须自己写，代码骨架和注释提示是给你的地图，不是答案。

---

## 任务 0：环境说明

### 向量数据库：PGvector 容器已经帮你起好了

我用 Docker 起了一个容器，信息如下（你不需要自己再起一个）：

| 项目 | 值 |
|---|---|
| 容器名 | `pgvector-rag` |
| 数据库名 | `rag_test` |
| 用户名/密码 | `pgvector` / `pgvector` |
| 端口 | 本机 `5433` → 容器 `5432` |
| 数据挂载目录 | `~/docker_data/pgvector_rag` |
| 镜像 | `ankane/pgvector:v0.5.0` |
| `vector` 扩展 | 已执行 `CREATE EXTENSION vector;` |

验证一下容器是不是活的：

```bash
docker ps --filter "name=pgvector-rag"
```

如果之前关机了容器停了，用这条命令重新启动（不会丢数据，数据在挂载目录里）：

```bash
docker start pgvector-rag
```

想直接用命令行看库里的数据（不装 Navicat 也行）：

```bash
docker exec -it pgvector-rag psql -U pgvector -d rag_test
```

### 向量模型：用阿里百炼 DashScope

你需要自己去 [百炼平台](https://bailian.console.aliyun.com/) 申请一个 API Key（免费额度够用），然后在终端里设置环境变量（后面配置会用到）：

```bash
export DASHSCOPE_API_KEY=你的key
```

> 💡 为什么用 OpenAI 兼容模式而不是 `spring-ai-alibaba-starter-dashscope`？
> 因为你 `llm-spring-ai` 模块已经引入了 `spring-ai-starter-model-openai`（ChatController 在用），DashScope 恰好兼容 OpenAI 协议，直接换 `base-url` 就行，不用再加新的依赖。这也是文档里 `OpenAiEmbeddingModel` 那一节讲的场景。

---

## 任务 1：加依赖

### 目标

在 `llm-spring-ai/pom.xml` 里加入 PGvector 向量数据库的 starter。

### 需求

打开 `llm-spring-ai/pom.xml`，在 `<dependencies>` 里追加依赖。你需要加两个：

1. **PGvector 向量存储 starter** —— artifactId 是 `spring-ai-starter-vector-store-pgvector`，groupId 是 `org.springframework.ai`。版本号可以不写（父 pom 的 `spring-ai-bom` 已经管理了），但看你现有的其它 `spring-ai-*` 依赖都手写了 `${spring-ai.version}`，保持风格一致就好。

2. **JDBC 支持** —— `PgVectorStore` 底层要用 `JdbcTemplate` 做 CRUD，需要 `spring-boot-starter-jdbc`（`org.springframework.boot`）。

> 💡 想一下：为什么加了 `spring-ai-starter-vector-store-pgvector` 还需要单独加 `spring-boot-starter-jdbc`？
> 提示：翻一下文档里"引入pgvector"那一节，`PgVectorStore` 这个 bean 需要注入哪两个东西？

### 验收标准

```bash
cd "/Users/yangxu/idea Projects/llm"
./mvnw -pl llm-spring-ai dependency:tree | grep -i "pgvector\|jdbc"
```

应该能看到 `spring-ai-pgvector-store`、`postgresql`（JDBC 驱动）、`HikariCP`（连接池）都出现在依赖树里 ✅

---

## 任务 2：写配置

### 目标

在 `llm-spring-ai/src/main/resources/application.yml` 里补上 embedding 模型、数据源、向量库的配置。

### 需求

现在的 `application.yml` 只有 chat 相关配置。你需要新增三块：

#### 2.1 Embedding 模型配置

放在 `spring.ai.openai.embedding` 下（和 `spring.ai.openai.chat` 平级）。需要配置：
- `base-url`：DashScope 的 OpenAI 兼容地址是 `https://dashscope.aliyuncs.com/compatible-mode`
- `api-key`：用 `${DASHSCOPE_API_KEY}` 引用环境变量
- `options.model`：向量模型名，用 `text-embedding-v4`
- `options.dimensions`：向量维度，用 `768`

> 💡 注意：`spring.ai.openai.chat` 和 `spring.ai.openai.embedding` 是两套独立配置，可以指向不同的 `base-url`/`api-key`（比如 chat 用 OpenAI 官方，embedding 用 DashScope）。但既然你 DashScope 两个都能用，统一指向 DashScope 也可以——这个自己决定。

#### 2.2 数据源配置

放在 `spring.datasource` 下。需要配置：
- `url`：`jdbc:postgresql://localhost:5433/rag_test`（对应任务0的容器信息）
- `username` / `password`：都是 `pgvector`

#### 2.3 向量库配置

放在 `spring.ai.vectorstore.pgvector` 下。参考文档里给的示例，需要配置这几项，自己查一下每项含义再填：
- `index-type`：索引类型，用 `HNSW`
- `distance-type`：距离度量方式，用 `COSINE_DISTANCE`
- `dimensions`：**必须和 2.1 的 embedding dimensions 一致**，否则向量维度不匹配会报错
- `max-document-batch-size`：一批最多往数据库写多少个 document，先设成一个较小的数字（比如 `10`），后面任务3你会理解为什么
- `initialize-schema`：是否启动时自动建表，设 `true`（第一次跑必须为 true，表才会被创建）
- `table-name`：自己起一个名字，比如 `vector_store`

> 🤔 **思考题**：文档里提到 `text-embedding-v4` 单批次最多处理 10 个文档（批次大小限制）。如果 `max-document-batch-size` 设置的值比这个限制大，会发生什么？先记住这个疑问，任务3会验证。

### 验收标准

```bash
./mvnw -pl llm-spring-ai spring-boot:run
```

启动日志里应该能看到 Hikari 连接池初始化成功、没有报错。可以用 Navicat 或者：

```bash
docker exec -it pgvector-rag psql -U pgvector -d rag_test -c "\dt"
```

看到你配置的 `table-name` 对应的表已经被自动创建 ✅（说明 `initialize-schema: true` 生效了）

---

## 任务 3：写 EmbeddingService

### 目标

写一个 Service，把 embedding 模型和向量库串起来，提供"只向量化不存储"和"向量化并存储"两个能力。

### 需求

**新建文件**：`llm-spring-ai/src/main/java/com/cyril/llm/springai/rag/EmbeddingService.java`

```java
package com.cyril.llm.springai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 向量化 + 向量存储服务
 *
 * embed()：只做向量化，不落库，用来验证/调试向量本身
 * embedAndStore()：向量化 + 存进 PGvector，真正构建索引用这个
 */
@Service
public class EmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 向量化（不存储）
     *
     * TODO:
     * 1. 判空：documents 为空直接返回空列表
     * 2. 遍历 documents，对每个 document 调用 embeddingModel.embed(document.getText())
     *    —— 注意 embed 方法既能传 Document 也能传 String，这里选哪个？想一下区别。
     * 3. 收集成 List<float[]> 返回
     */
    public List<float[]> embed(List<Document> documents) {
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    /**
     * 向量化 + 存储到 PGvector
     *
     * ⚠️ 有坑注意（务必先读文档里"有坑注意"那一节再写）：
     * 不同 embedding 模型对"一次批量处理多少条文本"有硬性限制。
     * text-embedding-v4 的批次大小限制是 10。
     * 而 application.yml 里配置的 max-document-batch-size 控制的是
     * "一批发送给【数据库】多少个 document"，跟发给【向量模型】多少个文档
     * 完全不是一回事 —— PgVectorStore.doAdd() 内部是先把整批 documents
     * 一口气全部传给 embeddingModel 做向量化，再分批插入数据库的。
     *
     * 所以如果你直接把几百个 document 一次性传给 vectorStore.add()，
     * 向量模型那边会直接报"批次超限"的错。
     *
     * TODO:
     * 1. 判空
     * 2. 手动分批：每批不超过 9 个（留 1 个余量，别刚好等于模型上限 10）
     * 3. 对每一批调用 vectorStore.add(batch)
     *
     * 提示：用 for 循环 + subList，参考你在 DOC-PREPROCESS 练习里
     * cleanDocuments 用过的 stream 写法，这里更适合普通 for 循环。
     */
    public void embedAndStore(List<Document> documents) {
        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

### 验收标准

```bash
./mvnw -pl llm-spring-ai compile
```

`BUILD SUCCESS` ✅（哪怕方法体还是抛异常也算过，但你应该把 TODO 填完）

---

## 任务 4：Controller 里串联 split + embed

### 目标

在 `RagController` 里新增两个端点：`/rag/split`（分片）和 `/rag/embed`（分片+向量化+入库），完整走通索引构建链路。

### 4.1 先加分片方法

Spring AI 内置了 `TokenTextSplitter`（按 token 数切分，不需要额外加依赖，你现有的 `spring-ai-starter-model-openai` 已经带了这个类）。

在 `RagController` 里加一个私有方法：

```java
/**
 * 文档分片
 *
 * TODO:
 * 1. 判空：documents 为空返回空列表
 * 2. 创建一个 TokenTextSplitter：
 *    可以用无参构造 new TokenTextSplitter()，也可以用 builder 自定义 chunkSize
 * 3. 调用 splitter.split(documents) 返回分片后的 List<Document>
 *
 * 提示：TokenTextSplitter 实现了 DocumentTransformer，
 * split() 方法签名是 List<Document> split(List<Document> documents)
 */
private List<Document> splitDocuments(List<Document> documents) {
    throw new UnsupportedOperationException("TODO: 你来写");
}
```

> 🤔 思考题：为什么清洗（clean）要在分片（split）之前做，而不是反过来？
> 回去翻一下 `DOC-PREPROCESS-PRACTICE-TASKS.md` 里"理解自测"第3题，答案就在那。

### 4.2 加 `/rag/split` 端点（可选，方便你调试分片效果）

```java
@GetMapping("/split")
public List<Document> split(@RequestParam("path") String path) {
    // TODO: 复用 readDocument 里读文件的逻辑，读出来 -> clean -> split，返回结果
    // 可以观察一下：清洗后的一个大 Document，分片之后变成了几个小 Document？
    // 每个分片的字数大概是多少？
    throw new UnsupportedOperationException("TODO: 你来写");
}
```

### 4.3 加 `/rag/embed` 端点（这是本次练习的终点）

需要先注入 `EmbeddingService`：

```java
@Autowired
private EmbeddingService embeddingService;
```

```java
/**
 * 完整索引构建链路：读取 -> 清洗 -> 分片 -> 向量化 -> 存入向量库
 *
 * TODO:
 * 1. 用 selector.read(file) 读取文档
 * 2. 调用 cleanDocuments 清洗
 * 3. 调用 splitDocuments 分片
 * 4. 调用 embeddingService.embedAndStore(...) 向量化并入库
 * 5. 返回一个简单的成功信息（比如返回分片后的文档数量）
 *
 * IOException 记得按你在 readDocument 里已经写过的方式处理
 */
@GetMapping("/embed")
public String embed(@RequestParam("path") String path) {
    throw new UnsupportedOperationException("TODO: 你来写");
}
```

### 验收标准

先重新跑一遍任务2的验收（确认 pgvector 容器和表都正常），然后：

```bash
./mvnw -pl llm-spring-ai spring-boot:run
```

```bash
# 用一个内容多一点的文本文件测试（几百字以上，能看出分片效果）
curl "http://localhost:8081/rag/embed?path=/tmp/test.txt"
```

> ⚠️ 注意你项目里 `application.yml` 配的端口是 `8081`，不是文档示例里的 `8080`。

预期：返回成功信息，没有报错。如果报了"批次超限"之类的错误，回头检查任务3里 `embedAndStore` 的分批逻辑。

---

## 任务 5：验收 —— 查库 + 检索

### 5.1 查库确认数据写进去了

```bash
docker exec -it pgvector-rag psql -U pgvector -d rag_test -c "SELECT id, content, embedding FROM vector_store LIMIT 3;"
```

（`vector_store` 换成你任务2里自己起的 `table-name`）

你应该能看到：
- `content` 列是清洗+分片后的文本
- `embedding` 列是一长串浮点数（这就是文本的向量表示）

### 5.2（加分项）写一个检索接口，闭环验证

存进去的向量如果查不出来，等于白存。加一个 `/rag/search` 端点：

```java
/**
 * 相似度检索
 *
 * TODO:
 * 1. 用 SearchRequest.builder().query(query).topK(3).build() 构造检索请求
 *    （query 是用户输入的查询文本，topK 是返回最相似的几条）
 * 2. 调用 vectorStore.similaritySearch(request) 拿到最相似的 Document 列表
 * 3. 返回结果
 *
 * 想一下：这一步内部是不是也要把 query 先向量化，再去数据库里做相似度比较？
 * 用的是哪种距离度量？（回去看任务2里 distance-type 配的是什么）
 */
@GetMapping("/search")
public List<Document> search(@RequestParam("query") String query) {
    throw new UnsupportedOperationException("TODO: 你来写");
}
```

验收：

```bash
curl "http://localhost:8081/rag/search?query=你文档里提到的某个关键词或者一句话"
```

如果返回的 Document 里有语义相关的内容（哪怕没有关键词完全匹配），说明你的向量检索链路真正跑通了 —— 这就是 RAG 的核心能力。

---

## 🧠 理解自测（不用写代码，但要想清楚）

1. **`max-document-batch-size` 到底控制的是什么？为什么光设置它控制不住向量模型报错？**
   <details><summary>点击看答案</summary>

   它控制的是"一批插入数据库的 document 数量"，作用于 `PgVectorStore.doAdd()` 内部的 `batchDocuments()` 方法。但这个方法是在 `embeddingModel.embed(documents, ...)` **之后**才被调用的 —— 也就是说向量化那一步拿到的永远是你调 `vectorStore.add()` 时传入的整个列表，跟这个配置项无关。所以你必须在调用 `add()` 之前，自己手动把大列表切成向量模型能接受的小批次。

   </details>

2. **`embed(Document document)` 和 `embed(String text)` 有什么区别？为什么 `EmbeddingService.embed()` 应该选其中一个？**
   <details><summary>点击看答案</summary>

   `embed(String)` 直接把传入的字符串向量化。`embed(Document)` 内部实际调的是 `getEmbeddingContent(document)`，默认实现也是取 `document.getText()`，但预留了扩展点（比如某些场景想把 metadata 拼进向量化的文本里）。语义上更贴近"给文档做向量化"用 `embed(Document)`，更直接可控用 `embed(document.getText())`，两种写法结果一样，选哪个是风格问题。

   </details>

3. **`distance-type` 设成 `COSINE_DISTANCE` 和设成欧氏距离（`EUCLIDEAN_DISTANCE`）有什么本质区别？**
   <details><summary>点击看答案</summary>

   余弦距离衡量两个向量的"方向"是否相似（不受向量长度/模长影响），适合文本语义检索场景——两段文字表达的意思相近，向量方向就会接近，即使因为长度不同导致模长不一样。欧氏距离衡量的是两点间的直线距离，会受向量长度影响，更适合数值特征场景。文本 embedding 检索几乎总是用余弦距离。

   </details>

4. **HNSW 索引和不建索引（顺序扫描）比，好在哪，代价是什么？**
   <details><summary>点击看答案</summary>

   HNSW（Hierarchical Navigable Small World）是一种近似最近邻（ANN）索引，能把相似度检索从 O(n) 全表扫描降到近似 O(log n)，数据量大时查询速度天差地别。代价是：建索引本身要花时间和内存，而且是"近似"最近邻，不保证 100% 精确（极少数情况下会漏掉真正最相似的一条，但召回率通常很高）。小数据量（几千条以内）其实用不用索引差别不大。

   </details>

---

## 📖 你可能会用到的 import

```java
// Embedding
import org.springframework.ai.embedding.EmbeddingModel;

// 向量存储
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

// 分片
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

// 已有的
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
```

---

## 📋 文件结构总览（做完之后应该是这样）

```
llm-spring-ai/
├── pom.xml                                        ← 任务1：加 pgvector + jdbc 依赖
├── src/main/resources/application.yml             ← 任务2：加 embedding/datasource/vectorstore 配置
└── src/main/java/com/cyril/llm/springai/
    ├── controller/
    │   └── RagController.java                     ← 任务4：新增 split/embed/search 端点
    └── rag/
        ├── DocumentReaderStrategy.java             ← 已有
        ├── DocumentReaderStrategySelector.java      ← 已有
        ├── EmbeddingService.java                   ← 任务3：新建
        └── reader/...                              ← 已有
```

---

## 🆘 卡住了怎么办

1. **先回看《向量模型&向量数据库&向量存储》文档**，代码示例几乎是逐行对应的
2. **看 Spring AI 源码**：`Cmd+Click` 进 `PgVectorStore`、`EmbeddingModel`、`TokenTextSplitter` 看源码，比猜参数含义靠谱
3. **看报错信息**：向量维度不匹配、批次超限，这两种错误信息都很直白，仔细读
4. **问我**：卡在哪个 TODO 直接说，我给提示不给答案

**这一章比文档预处理更容易踩"配置项含义理解错"的坑，慢一点没关系，把每个参数为什么这么设想清楚，比赶进度更重要。**
