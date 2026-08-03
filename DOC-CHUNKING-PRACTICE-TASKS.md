# ✂️ 文档分片 实战练习任务

> 任务驱动学习 —— 你写代码，我验收
> 前置：已完成《文档预处理》练习（`DOC-PREPROCESS-PRACTICE-TASKS.md`），目前有一个 `/rag/read` 可以返回清洗后的 `List<Document>`
> 后续：本章结束后，进入《向量模型 & 向量数据库》练习（`VECTOR-EMBEDDING-PRACTICE-TASKS.md`）

---

## 🎯 练完你能掌握什么

- 为什么分片（chunking）是清洗和向量化之间不可跳过的独立阶段
- `TokenTextSplitter`（Spring AI 固定 token 预算分片）的 builder API 和真实行为
- 看清楚每个 chunk 的 metadata（`parent_document_id`、`chunk_index`、`total_chunks`）是怎么来的
- `chunkSize` 是 **token 预算**，不是 Java 字符数 —— 亲手验证这个区别
- 三种主流分片方式（固定 token、递归字符、语义分片）的原理、调用成本和依赖边界
- 不依赖 Embedding 模型、不连接向量数据库，就能独立测试和验证分片效果

---

## 📌 练习顺序

```
任务 0（先搞清楚项目现状：到底有没有分片？）
  ↓
任务 1（抽取 DocumentCleaningService）
  ↓
任务 2（实现 DocumentChunkingService）
  ↓
任务 3（观察 metadata 和分片顺序）
  ↓
任务 4（添加 DTO + /rag/split 接口）
  ↓
任务 5（写测试）
  ↓
任务 6（用真实长文档实验不同 chunkSize）
  ↓
加分项 A（Spring AI Alibaba 递归字符分片 —— 研究门禁）
  ↓
加分项 B（LangChain4j 语义分片 —— 研究门禁）
```

> ⚠️ 和之前一样：所有 TODO 必须自己写。这次绝大部分代码已经帮你写了（`DocumentCleaningService`、`DocumentChunkingService`、DTO、`RagController`、`RagExceptionHandler`），你的任务是 **理解每一行、跑验证、做实验、写加分项研究**。

---

## 任务 0：先搞清楚项目现状

### 当前项目到底有没有分片？

**严格来说，没有通用的 RAG 文档分片器。** 但有几种容易混淆的"局部切分"：

| 位置 | 做了什么 | 算不算 RAG 分片 |
|---|---|---|
| `PdfReaderStrategy` 第 40 行 | `withPagesPerDocument(1)`，每页独立成一个 `Document` | ❌ 是 Reader 按物理页拆文档，可控性差（一页可能有几百字也可能只有一行） |
| `MarkdownReaderStrategy` 第 38 行 | `withHorizontalRuleCreateDocument(true)`，遇到 `---` 拆新 `Document` | ❌ 是 Reader 按 Markdown 结构拆文档，无法按 token 预算精确控制 |
| `RagController.cleanDocuments()` 第 56 行 | `split("\\R")` 按换行拆分 | ❌ 是清洗管线的实现细节（按行去重），不是 chunking |
| **项目源码的任何位置** | `TokenTextSplitter` / `DocumentSplitter` / `/rag/split` | ❌ **不存在** |

所以本章的目标很明确：在现有 `List<Document>`（读取并清洗后）的基础上，加一层 **可配置、可独立验证、不依赖模型和数据库** 的通用分片阶段。

### 为什么分片必须在向量化之前独立练习？

```
文档读取 → 文档清洗 → 文档分片 → 向量化 → 向量数据库 → 相似度检索
                         ↑
                    你在这里
```

1. chunk 是 embedding 的输入单位，也是向量数据库的一行 —— 它决定了一切下游行为。
2. 分片不调模型、不连数据库，可以在 0 网络请求下完整验证。
3. 如果跳过独立分片练习，后续检索效果不好时，你分不清是清洗、分片参数、embedding 模型还是数据库的问题。
4. 先建立确定性基线，再引入不确定的向量相似度比较，才符合工程直觉。

---

## 📖 三种分片方式对照

你在文章中看到三类分片，这里先把它们的原理、成本和依赖边界讲清楚。

| 类型 | 边界依据 | 是否调用模型 | 是否需要向量库 | 依赖与成本 | 本轮范围 |
|---|---:|---:|---|---|---|
| **固定字符分片** | 每 N 个 Java 字符/code point 切一刀 | 否 | 否 | 最低，但容易切断词句 | 仅作概念基线，不落代码 |
| **固定 token 预算分片** | 本地 tokenizer 估算 token 数，向标点附近回退 | 否 | 否 | 本地 CPU；tokenizer 可能与最终模型不完全一致 | ✅ **完整实现**（用 Spring AI `TokenTextSplitter`） |
| **递归字符分片** | 按段落 → 换行 → 空格 → 字符 逐级回退 | 否 | 否 | 本地 CPU；更保留文本结构 | 加分项 A（Spring AI Alibaba，需先验证依赖） |
| **语义分片** | 对句子/段落生成 embedding，在相邻语义变化大的位置断开 | 是 | 否（不需要先有向量库，但要调用 embedding 模型） | 有模型费用、延迟和阈值调参成本 | 加分项 B（LangChain4j，需先确认 API） |

### 两个常见误区

1. **"Token 分片调用的是 embedding 模型"** —— 错。`TokenTextSplitter` 用的是本地 tokenizer（和模型无关），不需要任何网络请求。
2. **"语义分片必须先有向量数据库"** —— 错。语义分片只需要 embedding 模型来判断"相邻两段话的语义变化大不大"，不需要把中间结果存进向量库。不过如果之后还要把最终 chunks 向量化入库，确实会有"分片 embedding + 入库 embedding"两阶段成本。

---

## 任务 1：理解已写好的 `DocumentCleaningService`

### 目标

理解清洗逻辑为什么从 Controller 私有方法升级为独立的 `@Service`，以及它和分片的关系。

### 先看代码

我已经帮你建好了：

```
llm-spring-ai/src/main/java/com/cyril/llm/springai/rag/DocumentCleaningService.java
```

打开它，对照原来 `RagController.cleanDocuments()` 看一遍。两者的逻辑完全一致：

1. 判空 → 跳过 null document / null text
2. `replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "")` → 去掉乱码
3. `split("\\R")` → 按换行拆分
4. 每行 `replaceAll("\\s+", " ").trim()` → 压缩空白
5. 过滤空行
6. `LinkedHashSet` → 顺序去重
7. 重建 `Document`，保留原 metadata

### 为什么必须抽取？

```java
// 原来（坏）—— 清洗是 Controller 私有方法
// 后续 /rag/embed 需要在 Controller 里再写一遍 cleaningService.clean() 调用
// 或者把 split 也写成私有方法塞进 Controller，最后 Controller 变成几百行的上帝类

// 现在（好）—— 清洗是可复用服务
// /rag/read 调它，/rag/split 也调它，未来的 embedding 链路还是调它
// 只改了一个 Bean，所有接口统一受益
```

### 验收标准

```bash
cd "/Users/yangxu/idea Projects/llm"

# 跑清洗服务的专项测试
./mvnw -f llm-spring-ai/pom.xml -Dtest=DocumentCleaningServiceTest test
```

9 个 case 全部通过 ✅

---

## 任务 2：读懂 `DocumentChunkingService`

### 目标

理解 `TokenTextSplitter` 的 builder API、`split()` 行为和 token 预算的真正含义。

### 代码已写好，你需要读

```
llm-spring-ai/src/main/java/com/cyril/llm/springai/rag/DocumentChunkingService.java
```

核心就三行：

```java
TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(chunkSize)   // ← 这是 token 预算，不是字符数！
        .build();
return splitter.split(documents);
```

### 你必须理解的点

#### `chunkSize` 是 token 数，不是字符数

`TokenTextSplitter` 内部用本地 tokenizer 估算每片的 token 数量（近似于 OpenAI cl100k_base），然后在接近 `chunkSize` 时寻找标点附近的断开位置。所以：

- **不能断言 `characterCount == chunkSize`** —— 字符数和 token 数不是线性关系
- **不能断言每片的字符数相等** —— splitter 会在语义边界（句号、换行等）调整
- **中文尤其明显** —— 一个中文字 ≈ 1-2 个 token，但分词边界影响很大

#### Builder 还有哪些参数（本次不给入口，但你应该知道）

```java
TokenTextSplitter.builder()
    .withChunkSize(800)                  // token 预算
    .withMinChunkSizeChars(350)          // 每片最少保留多少字符
    .withMinChunkLengthToEmbed(5)        // 短于此字符数的片不送给 embedding
    .withMaxNumChunks(10000)             // 最多产生多少片
    .withKeepSeparator(true)             // 是否在片末尾保留分隔符
    .withPunctuationMarks(List.of('。', '.', '！', '？'))  // 标点列表
    .build();
```

#### Split 后的 metadata 自动注入

`TextSplitter.split()` 会给每个输出 chunk 自动添加三个 metadata 字段：

| 字段 | 含义 | 示例值 |
|---|---|---|
| `parent_document_id` | 源文档的 ID（UUID） | `"a1b2c3d4-..."` |
| `chunk_index` | 该 chunk 在父文档内的序号（从 0 开始） | `0`, `1`, `2` |
| `total_chunks` | 该父文档一共被切成了多少片 | `5` |

**原 metadata 也会保留。** 比如原文档的 `filename`、`author` 等字段，每个 chunk 都会带着。

### 验收标准

```bash
./mvnw -f llm-spring-ai/pom.xml -Dtest=DocumentChunkingServiceTest test
```

14 个 case 全部通过 ✅。特别关注：

- `chunkIndicesAreConsecutiveWithinParent` —— chunk_index 是连续的
- `totalChunksIsConsistentWithinParent` —— total_chunks 在所有同源 chunk 上一致
- `multipleSourceDocumentsKeepOrder` —— 多个源文档的输出不交错
- `inputDocumentsAreNotModified` —— 原始 Document 不会被修改

---

## 任务 3：观察 metadata 与分片顺序

### 目标

用单元测试验证的结果去理解"Spring AI 1.1.6 的 `TextSplitter` 到底在 metadata 里写了什么"，而不是凭文档猜。

### 你需要观察的

打开 `DocumentChunkingServiceTest.java`，重点看这几个测试的断言：

1. **`splitterMetadataIsPresent`** —— 确认 `parent_document_id`、`chunk_index`、`total_chunks` 三个字段都存在
2. **`originalMetadataIsCopied`** —— 原文档的 `author` 字段在每个 chunk 里都有
3. **`chunkIndicesAreConsecutiveWithinParent`** —— 同一个父文档的 chunk 从 0 连续递增
4. **`totalChunksIsConsistentWithinParent`** —— 同源 chunks 的 total_chunks 值一致
5. **`multipleSourceDocumentsKeepOrder`** —— 先输出的全是文档1的 chunks，然后才是文档2的 chunks

### 思考题

> 如果你有两个源 Document A 和 B，A 被切成 3 片，B 被切成 2 片。最终 `List<Document>` 的输出顺序是怎样的？每个 chunk 的 `chunk_index` 和 `total_chunks` 分别是什么？

<details><summary>点击看答案</summary>

```
chunk 0: text=A片段1, metadata={parent_document_id=A.id, chunk_index=0, total_chunks=3}
chunk 1: text=A片段2, metadata={parent_document_id=A.id, chunk_index=1, total_chunks=3}
chunk 2: text=A片段3, metadata={parent_document_id=A.id, chunk_index=2, total_chunks=3}
chunk 3: text=B片段1, metadata={parent_document_id=B.id, chunk_index=0, total_chunks=2}
chunk 4: text=B片段2, metadata={parent_document_id=B.id, chunk_index=1, total_chunks=2}
```

**关键不变式**：同一父文档的所有 chunk 是连续的，不会和别的文档交错；`chunk_index` 在每个父文档内从 0 重新开始。

</details>

---

## 任务 4：理解 DTO 和 `/rag/split` 接口

### 目标

理解为什么 `/rag/split` 不直接返回 `List<Document>`，而要经过一层 DTO 包装。

### 代码已写好

- DTO: `llm-spring-ai/src/main/java/com/cyril/llm/springai/controller/dto/DocumentChunkingResponse.java`
- Controller: `llm-spring-ai/src/main/java/com/cyril/llm/springai/controller/RagController.java`（已修改）
- Exception handler: `llm-spring-ai/src/main/java/com/cyril/llm/springai/RagExceptionHandler.java`（新增）

### DTO 的设计意图

如果直接返回 `List<Document>`，你只能看到一堆 JSON，很难一眼看出：

- 请求的 chunkSize 是多少
- 源文档有几个
- 分了多少片
- 每片的 ID、字符长度、metadata

`DocumentChunkingResponse` 把这些全放在顶层，一目了然。而且它**明确区分** `requestedChunkSizeTokens`（token 预算）和 `characterCount`（Unicode 字符数），防止你把字符数误认成 token 数。

### `/rag/split` 的完整链路

```
GET /rag/split?path=/path/to/file.txt&chunkSize=80

RagController.split()
  → readAndClean(path)           ← 复用 DocumentCleaningService
  → chunkingService.split(..)     ← TokenTextSplitter
  → DocumentChunkingResponse.from(..) ← 包装成可观察格式
  → JSON 响应
```

**整个链路不调用任何模型，不需要数据库。** 你可以在离线环境下完整验证。

### 验收标准

```bash
# 准备一个测试用的长文本文件
python3 -c 'print("\n".join(f"第{i:03d}段：这是用于验证文档分片顺序、长度和元数据的测试内容。" * 4 for i in range(1, 121)))' \
  > /tmp/chunk-practice.txt

# 启动模块
./mvnw -f llm-spring-ai/pom.xml spring-boot:run
```

如果你当前 Chat/OpenAI 自动配置有问题导致无法启动（比如 API key 未配但 ChatClient Bean 创建失败），**不要把这当成分片实现的失败**。分片的正确性由专项单元测试保证。你可以只依赖测试结果，或者在 application.yml 中暂时禁用 Chat 自动配置。

假设启动成功（端口 8081），验证：

```bash
# 1. 基本分片请求
curl -sG "http://localhost:8081/rag/split" \
  --data-urlencode "path=/tmp/chunk-practice.txt" \
  --data-urlencode "chunkSize=80" \
  | jq '{splitter, requestedChunkSizeTokens, sourceDocumentCount, chunkCount}'
```

预期：`splitter` 为 `"TokenTextSplitter"`，`sourceDocumentCount` 为 1，`chunkCount` > 1。

```bash
# 2. 验证顺序和 metadata
curl -sG "http://localhost:8081/rag/split" \
  --data-urlencode "path=/tmp/chunk-practice.txt" \
  --data-urlencode "chunkSize=80" \
  | jq '
      . as $result
      | {
          countMatches: ($result.chunkCount == ($result.chunks | length)),
          orderIsContinuous:
            ([$result.chunks[].order] == [range(0; $result.chunkCount)]),
          metadataPresent:
            (all($result.chunks[];
              (.metadata | has("parent_document_id"))
              and (.metadata | has("chunk_index"))
              and (.metadata | has("total_chunks"))
            )),
          textIsNonEmpty:
            (all($result.chunks[]; (.text | length) > 0))
        }'
```

所有布尔值应为 `true`。

```bash
# 3. 比较不同 token 预算
for size in 40 80 160; do
  curl -sG "http://localhost:8081/rag/split" \
    --data-urlencode "path=/tmp/chunk-practice.txt" \
    --data-urlencode "chunkSize=$size" \
    | jq --arg size "$size" '{
        requestedChunkSizeTokens: $size,
        chunkCount,
        characterCounts: [.chunks[].characterCount]
      }'
done
```

预期趋势：token 预算越小，chunk 通常越多、每片字符数通常越短。但**不要期待每片字符数固定相等**。

```bash
# 4. 空文件
printf '' > /tmp/chunk-empty.txt
curl -sG "http://localhost:8081/rag/split" \
  --data-urlencode "path=/tmp/chunk-empty.txt" \
  --data-urlencode "chunkSize=80" \
  | jq '{sourceDocumentCount, chunkCount, chunks}'
```

预期 `chunkCount` 为 0，`chunks` 为空数组。

```bash
# 5. `/rag/read` 兼容性 —— 原来的接口行为不变
curl -sG "http://localhost:8081/rag/read" \
  --data-urlencode "path=/tmp/chunk-practice.txt" \
  | jq 'length'
```

应返回 1（清洗后的一个 Document）。

---

## 任务 5：读懂测试

### 目标

理解每个测试在验证什么，而不是只看绿条。

### 三个测试文件

```
llm-spring-ai/src/test/java/com/cyril/llm/springai/
├── rag/
│   ├── DocumentCleaningServiceTest.java   ← 9 个 case：清洗行为不回归
│   └── DocumentChunkingServiceTest.java   ← 14 个 case：分片核心行为
└── controller/
    └── RagControllerTest.java             ← 6 个 case：HTTP 层行为
```

### 每个文件在验证什么

**`DocumentCleaningServiceTest`**（纯逻辑，不依赖 Spring 容器）：
- null/空输入、null document、空白压缩、空行过滤、顺序去重、特殊字符、metadata 保留
- 注意：空文本 Document 在清洗后会保留为空文本（而不是被过滤），这是当前实现的实际行为

**`DocumentChunkingServiceTest`**（纯逻辑）：
- 判空与参数校验、长文多 chunk、chunkSize 增减趋势、非空文本、原 metadata 保留、分片 metadata 注入、chunk_index 连续性、total_chunks 一致性、多源文档顺序、输入不可变性

**`RagControllerTest`**（Mockito + standalone MockMvc，隔离了 Chat/模型自动配置）：
- `/rag/read` 回归、selector → cleaning 委托链
- `/rag/split` 参数传递、响应 JSON 结构、空内容返回空数组
- 坏路径返回 400（`IllegalArgumentException` → handler）和 500（`RuntimeException` → handler）

### 验收标准

```bash
./mvnw -f llm-spring-ai/pom.xml test
```

30 个 case 全部通过 ✅（29 新 + 1 原有 contextLoads）。

---

## 任务 6：真实文档实验

### 目标

用你项目里已有的测试文档（或者自己找的长文）对比不同 `chunkSize`，建立直觉。

### 实验步骤

1. 找一个 2000 字以上的中文或英文文本文件
2. 分别用 `chunkSize=40`、`chunkSize=80`、`chunkSize=200` 请求 `/rag/split`
3. 记录每次的：
   - chunk 数量
   - 每片字符数范围（最小～最大）
   - 有没有句子在中间被截断
   - metadata 是否完整
4. 回答：如果下游 embedding 模型的 token 上限是 512，你应该选多大的 `chunkSize`？为什么？

<details><summary>点击看提示</summary>

`chunkSize` 是给本地 tokenizer 的预算，不等于最终模型的真实 token 数。本地 tokenizer（近似 cl100k_base）和你的实际 embedding 模型（比如 `text-embedding-v4`）的 tokenizer 可能不同。所以一般会留 10-20% 余量：如果模型上限 512，`chunkSize` 可以设在 400-450。

</details>

---

## 🧠 理解自测（不用写代码，但要想清楚）

1. **PdfReaderStrategy 的 `withPagesPerDocument(1)` 为什么不等于 RAG 分片？**
   <details><summary>点击看答案</summary>

   按页切分完全不考虑 token 预算 —— 一页可能有 50 字也可能有 2000 字，chunk 大小完全不受控。而 embedding 模型和向量库对每条记录的 token 长度有硬性限制。另外页码不是语义边界，一句话可能被切在两页。

   </details>

2. **`chunkSize=80` 时，为什么有的 chunk 有 200 个字符，有的只有 80？**
   <details><summary>点击看答案</summary>

   `chunkSize` 是 token 预算，不是字符数。一个中文字 ≈ 1-2 个 token，但 tokenizer 的分词粒度会影响最终统计。另外 splitter 会尽量在标点符号处断开，所以不会刚好在 token 预算处硬切。

   </details>

3. **TokenTextSplitter 有没有调用 OpenAI 或任何远程模型？**
   <details><summary>点击看答案</summary>

   没有。它用的是内置的本地 tokenizer（近似 cl100k_base），在 JVM 内完成所有计算。所以分片可以在不配置任何 API key 的情况下独立工作 —— 这也是为什么分片应该独立练习。

   </details>

4. **清洗为什么必须在分片之前做，而不是反过来？**
   <details><summary>点击看答案</summary>

   如果先分片再清洗，每个 chunk 内部可能有乱码、空行、重复行。而且去重是按"行"去重 —— 如果先分片，同一行出现在两个不同的 chunk 里不会被去重。先清洗 = 给 splitter 输入干净的文本，输出也更干净。

   </details>

5. **语义分片为什么不需要向量数据库，却仍有模型成本？**
   <details><summary>点击看答案</summary>

   语义分片只需要对每个句子/段落生成 embedding，然后比较相邻 embedding 的余弦距离，在"语义变化大"的地方断开。整个过程不需要把中间结果存进数据库。但生成 embedding 本身要调模型，有按 token 计费的成本和网络延迟。如果之后还要把这些语义 chunk 再向量化入库，会发生两阶段 embedding 费用。

   </details>

6. **递归字符分片为什么通常比固定字符切分更"自然"？**
   <details><summary>点击看答案</summary>

   固定字符分片在每 N 个字符处硬切，可能切断单词/句子。递归分片按优先级尝试分隔符：先按段落（`\n\n`），切不动再按换行（`\n`），再按空格，最后才按字符。这样能尽量在自然边界处断开，保留更多文本结构。

   </details>

7. **为什么 `chunkSize` 的单位是 token 而不是字符？**
   <details><summary>点击看答案</summary>

   因为下游 embedding 模型的限制按 token 计算（比如 `text-embedding-v4` 最多 8192 tokens），不是按字符。如果你用字符数控制，中英文混排的文本你根本无法估算实际 token 消耗。用 token 预算可以直接对齐模型限制。

   </details>

8. **为什么 metadata 中的 `chunk_index` 和 `total_chunks` 对检索很重要？**
   <details><summary>点击看答案</summary>

   检索时你拿回的是单个 chunk。如果用户想看"上下文"（比如前后 chunk），你需要靠 `parent_document_id` 找到同一文档的其他 chunks，再用 `chunk_index` 定位前一片和后一片。没有这些 metadata，你根本无法重建原始文档的上下文窗口。

   </details>

---

## 加分项 A：Spring AI Alibaba 递归字符分片

> ⚠️ 这不是一个可以直接写 TODO 的任务。当前项目还没验证相关依赖和 API。

### 你需要先做的研究

Spring AI Alibaba 1.0.0.3 的 jar 中**可能存在** `RecursiveCharacterTextSplitter`，但需要在 `llm-spring-ai-alibaba` 模块中逐一验证：

1. **依赖是否在 compile classpath？**
   ```bash
   ./mvnw -f llm-spring-ai-alibaba/pom.xml dependency:tree | grep "spring-ai-alibaba-core"
   ```

2. **类是否真实存在且可用？**
   ```java
   // 在 llm-spring-ai-alibaba 中尝试 import 并编译
   import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
   ```

3. **与项目 Spring AI 1.1.6 是否二进制兼容？**
   Spring AI Alibaba 1.0.0.3 编译时基线是 Spring AI 1.0.0，与项目实际解析的 1.1.6 可能存在方法签名差异。

4. **metadata 行为是否与 TokenTextSplitter 一致？**
   同样用 `parent_document_id`、`chunk_index`、`total_chunks` 吗？还是有自己的字段名？

### 通过以上全部门禁后

参考文章中类似代码：

```java
RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100);
List<String> chunks = splitter.splitText("...");
```

注意：`RecursiveCharacterTextSplitter` 的 API 和 `TokenTextSplitter` 不同 —— 它接收/返回 `String` 而不是 `Document`。这意味着 metadata 处理需要你自己写。

### 验收方式

用同一段长文本分别跑 `TokenTextSplitter`（llm-spring-ai）和 `RecursiveCharacterTextSplitter`（llm-spring-ai-alibaba），对比：
- chunk 数量
- 断点位置（是否更倾向于段落边界）
- metadata 完整度

---

## 加分项 B：LangChain4j 语义分片

> ⚠️ 更大的不确定性。当前 LangChain4j 1.0.0-beta1 已确认存在 `DocumentSplitter`、`TextSegment` 以及多种普通 splitter，但**没有在 jar 中找到名为 "semantic" 的 splitter 实现**。

### 你必须先确认的问题

1. 文章中的"语义分片"到底对应 LangChain4j 的哪个类？
   - 是 `DocumentBySentenceSplitter` + 自定义 embedding 比较？
   - 是较新版本（> 1.0.0-beta1）新增的官方实现？
   - 是社区扩展库（不在 `langchain4j-core` 中）？

2. 如果 LangChain4j 没有内置语义 splitter，你可以自己实现一个吗？
   - 核心算法不难：对句子逐一生成 embedding → 计算相邻句子 embedding 的余弦距离 → 在距离超过阈值的句子之间断开
   - 但需要注入 `EmbeddingModel`，这就是"分片阶段依赖模型"了

3. 要不要升级 LangChain4j 版本？
   - 当前是 `1.0.0-beta1`，如果新版本有官方语义 splitter，升级可能是最简单的路
   - 但升级可能破坏现有代码，需要单独评估

### 在你确认以上问题之前

**不要在 `llm-langchain4j` 中写语义分片代码。** 不要凭文章中的概念名字去猜类名、artifact 名或 API 签名。先把"到底是什么类、什么版本、什么依赖"搞清楚，再动手。

### 如果你确认了 API 可以落地

建议在 `llm-langchain4j` 模块中建一个独立的 `SemanticChunkingService`，用和 `DocumentChunkingService` 相同的输入输出接口（`List<Document>` → `List<Document>`），方便跨框架对比。

---

## 📖 你可能会用到的 import

```java
// 分片（Spring AI）
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

// 文档（Spring AI）
import org.springframework.ai.document.Document;

// 工具
import org.springframework.util.CollectionUtils;
```

---

## 📋 文件结构总览（做完之后应该是这样）

```
llm-spring-ai/
├── pom.xml
├── src/main/resources/application.yml
└── src/main/java/com/cyril/llm/springai/
    ├── SpringAiApplication.java
    ├── RagExceptionHandler.java                    ← 新增：全局异常处理
    ├── controller/
    │   ├── ChatController.java                     ← 已有
    │   ├── RagController.java                      ← 已修改：注入清洗+分片服务，新增 /rag/split
    │   └── dto/
    │       └── DocumentChunkingResponse.java       ← 新增：分片响应 DTO
    └── rag/
        ├── DocumentCleaningService.java            ← 新增：清洗服务
        ├── DocumentChunkingService.java            ← 新增：分片服务
        ├── DocumentReaderStrategy.java             ← 已有
        ├── DocumentReaderStrategySelector.java      ← 已有
        └── reader/...                              ← 已有

llm-spring-ai/src/test/java/com/cyril/llm/springai/
├── SpringAiApplicationTests.java                   ← 已有
├── controller/
│   └── RagControllerTest.java                      ← 新增：Controller 层测试
└── rag/
    ├── DocumentCleaningServiceTest.java            ← 新增：清洗服务测试
    └── DocumentChunkingServiceTest.java            ← 新增：分片服务测试
```

---

## 🆘 卡住了怎么办

1. **先看测试**：每个测试就是一个可执行的行为规格，比文档更精确
2. **看 Spring AI 源码**：`Cmd+Click` 进 `TokenTextSplitter`、`TextSplitter`，看 builder 和 split() 的源码
3. **区分编译错误和逻辑错误**：如果 `/rag/split` 返回 500，先看日志，大概率是依赖注入或文件路径问题
4. **区分模块编译和根聚合编译**：根 `pom.xml` 声明了实际缺失的 `rag` 模块，所以 `./mvnw compile` 在根目录会失败。**这是既有问题，不是分片造成的。** 始终用 `-f llm-spring-ai/pom.xml` 限定模块编译。
5. **问我**：卡在哪里直接说，我给提示不给答案

---

## 🔗 下一步

完成本章后，你的 RAG 链路已经走到：

```
读取 ✅ → 清洗 ✅ → 分片 ✅ → 向量化 ❓ → 向量数据库 ❓ → 检索 ❓
```

下一站：《向量模型 & 向量数据库》练习（`VECTOR-EMBEDDING-PRACTICE-TASKS.md`）。

到了那里，`DocumentChunkingService.split()` 和 `DocumentCleaningService.clean()` 都可以直接复用 —— 你不再需要把 splitter 逻辑塞进 Controller 或者 `EmbeddingService` 里。

**分片是清洗和向量化之间的桥梁。桥修好了，后面的路才好走。**
