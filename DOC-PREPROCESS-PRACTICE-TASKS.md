# 📄 文档预处理 实战练习任务

> 任务驱动学习 —— 你写代码，我验收
> 前置：视频已看完，现在需要动手把每个环节敲一遍



## 🎯 练完你能掌握什么

- Spring AI 提供的 7 种 DocumentReader 各有什么特点、怎么选
- 策略模式如何优雅地消除 if-else，支持随时扩展新的文档类型
- 数据清洗为什么必须在分片/向量化之前做，以及怎么设计清洗管线
- 最终能跑通一个完整的「上传文件 → 解析 → 清洗 → 返回干净 Document」的接口

---

## 📌 练习顺序

```
任务 1（补齐依赖）
  ↓
任务 2（写策略接口 + 7 种读取器实现）
  ↓
任务 3（写策略选择器）
  ↓
任务 4（写 Controller + 测试接口）
  ↓
任务 5（写数据清洗逻辑）
  ↓
任务 6（用真实文档跑通全流程）
```

> ⚠️ **重要**：每个任务的代码我给了骨架和关键提示，但 **TODO 部分你必须自己写**。写完一个任务就跑一下验收命令，确认通过再往下走。

---

## 任务 1：补齐 Maven 依赖

### 目标

在 `llm-spring-ai/pom.xml` 中加入所有文档读取器依赖。

### 需求

**打开 `llm-spring-ai/pom.xml`**，在 `<dependencies>` 中追加以下依赖：

```xml
<!-- PDF 读取器 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
</dependency>

<!-- HTML 读取器（基于 Jsoup） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-jsoup-document-reader</artifactId>
</dependency>

<!-- Markdown 读取器 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-markdown-document-reader</artifactId>
</dependency>

<!-- JSON 读取器（Spring AI 内置，不需要额外加，但你可以记一下类名：JsonReader） -->

<!-- Tika 通用读取器（Word/PPT 等） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>

<!-- 工具类 -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-core</artifactId>
</dependency>
```

> 💡 **为什么不需要写版本号？**
> 因为父 pom.xml 里已经通过 `spring-ai-bom` 统一管理了所有 Spring AI 依赖的版本。你只需要写 groupId 和 artifactId，版本自动对齐。

### 验收标准

```bash
cd "/Users/yangxu/idea Projects/llm"
./mvnw -pl llm-spring-ai dependency:tree | grep "spring-ai.*document-reader"
```

应该能看到 4 个 document-reader 出现在依赖树中 ✅

---

## 任务 2：写策略接口 + 实现所有读取器

### 目标

用策略模式封装 7 种文档类型的读取逻辑，消除 if-else。

### 2.1 先建目录

在 `llm-spring-ai/src/main/java/com/cyril/llm/springai/` 下新建两个包：

```
rag/           ← 策略接口 + 所有实现 + 选择器放这里
rag/reader/    ← 各类型 Reader 策略实现放这里
```

> 在 IDEA 里右键 `com.cyril.llm.springai` → New → Package，输入 `rag`，再在 rag 下建 `reader`。

### 2.2 写策略接口

**新建文件**：`rag/DocumentReaderStrategy.java`

```java
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
```

### 2.3 写 7 种读取器实现

> 每种读取器都是一个 `@Component`，Spring 会自动发现它们。

---

#### ① TextReader 策略

**新建文件**：`rag/reader/TextReaderStrategy.java`

```java
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
        // TODO: 根据文件扩展名判断 —— 返回 true 当文件名以 .txt / .text / .tex 结尾
        // 提示：file.getName().toLowerCase().endsWith(...)
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO:
        // 1. 把 File 包装成 Resource: new FileSystemResource(file)
        // 2. 创建 TextReader: new TextReader(resource)
        // 3. 调用 .get() 返回 List<Document>
        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

---

#### ② PDF 读取器（Page 模式）

**新建文件**：`rag/reader/PdfReaderStrategy.java`

```java
package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.ExtractedTextFormatter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * PDF 读取策略 —— 按页切分
 *
 * PagePdfDocumentReader 的核心配置：
 * - pageTopMargin / pageBottomMargin：裁剪页眉页脚区域（单位是点，1点≈0.35mm）
 * - pagesPerDocument：每页单独生成一个 Document（=1 时每页一个）
 * - ExtractedTextFormatter：进一步控制每页文本的格式化
 *
 * 还有个 ParagraphPdfDocumentReader 是按段落切分，更适合语义检索场景，
 * 但对 PDF 本身的排版质量要求更高。
 */
@Component
public class PdfReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        // TODO: 判断文件是否以 .pdf 结尾
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO: 构建 PdfDocumentReaderConfig，要求：
        // 1. 顶部、底部各裁剪 50 个单位（去掉页眉页脚）
        // 2. 每页生成一个 Document（pagesPerDocument = 1）
        // 3. 设置 ExtractedTextFormatter，不删除顶部行
        //
        // 参考代码：
        // PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
        //     .withPageTopMargin(50)
        //     .withPageBottomMargin(50)
        //     .withPagesPerDocument(1)
        //     .withPageExtractedTextFormatter(
        //         new ExtractedTextFormatter.Builder()
        //             .withNumberOfTopTextLinesToDelete(0)
        //             .build()
        //     )
        //     .build();
        //
        // 然后：
        // Resource resource = new FileSystemResource(file);
        // return new PagePdfDocumentReader(resource, config).get();

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

> 🤔 **思考题**（不用写代码，想一下就行）：
> 什么时候应该用 `PagePdfDocumentReader`，什么时候应该用 `ParagraphPdfDocumentReader`？
> 如果你面对一批扫描版 PDF（没有文字层），这两种 Reader 还能工作吗？为什么？
PagePdfDocumentReader —— 按物理页切分 
> ParagraphPdfDocumentReader —— 按语义段落切分
它分析 PDF 内部的排版结构（段落间距、缩进、字体变化等），把语义完整的段落归成一个 Document。
---

#### ③ HTML 读取器

**新建文件**：`rag/reader/HtmlReaderStrategy.java`

```java
package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * HTML 读取策略 —— 基于 Jsoup
 *
 * JsoupDocumentReader 的亮点：
 * - selector：用 CSS 选择器指定要提取哪些标签的内容（如 "p" 只提取段落）
 * - charset：文件编码（中文内容一定要设 UTF-8）
 * - includeLinkUrls：是否保留超链接
 * - metadataTags：提取哪些 <meta> 标签的值作为元数据
 *
 * 一个常见坑：selector 如果写得太宽（如 "body"），会把导航栏、尾部等
 * 无关内容也提取进来，影响检索质量。建议根据网页结构精细化选择。
 */
@Component
public class HtmlReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        // TODO: 支持 .html 和 .htm
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO: 构建 JsoupDocumentReaderConfig，要求：
        // 1. 只用 CSS 选择器提取 <p> 标签内容
        // 2. 字符编码设为 UTF-8
        // 3. 保留超链接
        // 4. 提取 meta 标签中的 author 和 date
        // 5. 添加文件名作为自定义元数据
        //
        // 参考：
        // JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
        //     .selector("p")
        //     .charset("UTF-8")
        //     .includeLinkUrls(true)
        //     .metadataTags(List.of("author", "date"))
        //     .additionalMetadata("filename", file.getName())
        //     .build();

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

---

#### ④ Markdown 读取器

**新建文件**：`rag/reader/MarkdownReaderStrategy.java`

```java
package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Markdown 读取策略
 *
 * MarkdownDocumentReader 的核心配置：
 * - horizontalRuleCreateDocument：遇到水平线（---）时是否切分为新 Document
 * - includeCodeBlock：是否保留代码块
 * - includeBlockquote：是否保留引用块
 *
 * 对于 RAG 场景，通常建议 includeCodeBlock = false，
 * 因为代码块的语义和自然语言差异很大，混在一起会影响向量检索的精度。
 * 但如果你做的是代码知识库，那就应该保留。
 */
@Component
public class MarkdownReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        // TODO: 支持 .md
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO: 构建 MarkdownDocumentReaderConfig，要求：
        // 1. 水平线分割生成新文档
        // 2. 不包含代码块
        // 3. 不包含引用块
        // 4. 添加文件名为元数据
        //
        // Resource resource = new FileSystemResource(file);
        // return new MarkdownDocumentReader(resource, config).get();

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

---

#### ⑤ JSON 读取器

**新建文件**：`rag/reader/JsonReaderStrategy.java`

```java
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
        // TODO: 支持 .json
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO:
        // 假设我们要提取 JSON 中的 "description" 和 "content" 两个字段
        // Resource resource = new FileSystemResource(file);
        // JsonReader jsonReader = new JsonReader(resource, "description", "content");
        // return jsonReader.get();

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

---

#### ⑥ Tika 通用读取器（Word 等）

**新建文件**：`rag/reader/TikaReaderStrategy.java`

```java
package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tika 通用读取策略
 *
 * Apache Tika 是一个「万能文件解析器」，能自动检测文件类型并提取文本。
 * 支持：Word (.doc/.docx)、PPT (.ppt/.pptx)、Excel (.xls/.xlsx)、
 *       PDF、HTML、XML、EPUB 等等。
 *
 * 优点：一个 Reader 搞定几十种格式
 * 缺点：没有针对特定格式的精细化控制（不能像 PDF Reader 那样配置页眉裁剪等）
 *
 * 最佳实践：对有精细化需求的格式（如 PDF、HTML）用专用 Reader，
 * 对其他格式（如 Word、PPT）用 Tika 兜底。
 */
@Component
public class TikaReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        // TODO: 支持 .doc、.docx、.ppt、.pptx
        // 提示：可以用一个 Set 存所有扩展名，然后判断
        throw new UnsupportedOperationException("TODO: 你来写");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO:
        // Resource resource = new FileSystemResource(file);
        // return new TikaDocumentReader(resource).get();

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

---

#### ⑦ 兜底读取器（可选，加分项）

> 当前面 6 种都匹配不上时，用 TextReader 兜底尝试读取。
> 这能处理那些没专门适配的纯文本格式（如 .log、.csv、.xml 等）。

**新建文件**：`rag/reader/FallbackReaderStrategy.java`

```java
package com.cyril.llm.springai.rag.reader;

import com.cyril.llm.springai.rag.DocumentReaderStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 兜底策略 —— 当所有专用 Reader 都不匹配时，拿 TextReader 尝试
 *
 * @Order(Ordered.LOWEST_PRECEDENCE) 确保这个兜底策略排在最后
 *
 * 为什么需要兜底？如果用户传了一个 .log 文件，前面 6 个 Reader
 * 都不认识，直接抛 "不支持的文件类型" 体验不好。兜底策略至少能
 * 把文件内容读出来，让系统不至于完全不可用。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FallbackReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        // 兜底策略：永远返回 true
        // 因为其他策略都不匹配时，由它来兜底
        return true;
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // TODO: 用 TextReader 读取，打印一行警告日志表示用了兜底策略
        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

> ⚠️ 如果不用 Fallback，选择器会直接抛异常。加不加兜底取决于你的业务需求。
> 如果你希望「只处理明确支持的类型」，可以不加这个。

### 验收标准

```bash
./mvnw -pl llm-spring-ai compile
```

`BUILD SUCCESS` ✅ 就算接口方法还是抛异常也能编译通过。
当然，你应该把 TODO 替换为真实代码 😄

---

## 任务 3：写策略选择器

### 目标

把前面 7 个策略串起来，根据文件类型自动路由到正确的读取器。

### 需求

**新建文件**：`rag/DocumentReaderStrategySelector.java`

```java
package com.cyril.llm.springai.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 文档读取策略选择器
 *
 * 核心逻辑很简单：
 * 1. Spring 自动注入所有实现了 DocumentReaderStrategy 的 Bean
 * 2. 遍历这些策略，找到第一个 supports() 返回 true 的
 * 3. 调用它的 read() 方法
 *
 * 这里利用了 Spring 的依赖注入：你新增一个 Reader 实现类，加了 @Component，
 * 这个选择器会自动感知到它，不需要修改任何代码。
 */
@Service
public class DocumentReaderStrategySelector {

    private final List<DocumentReaderStrategy> strategies;

    /**
     * 构造器注入 —— Spring 会把所有 DocumentReaderStrategy 的实现
     * 自动收集成一个 List 注入进来。
     *
     * 这里是你要理解的关键点：
     * 为什么不用 @Autowired 字段注入？
     * → 构造器注入更方便单元测试（你可以手动 new 一个 List 传进去）
     * → 构造器注入能保证依赖不可变（final）
     */
    public DocumentReaderStrategySelector(List<DocumentReaderStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 根据文件类型选择合适的策略读取文档
     *
     * @param file 要读取的文件
     * @return 解析后的 Document 列表
     * @throws IOException 读取失败
     * @throws IllegalArgumentException 没有匹配的策略
     */
    public List<Document> read(File file) throws IOException {
        // TODO:
        // 1. 遍历 strategies
        // 2. 找到第一个 strategy.supports(file) == true 的
        // 3. 调用 strategy.read(file) 并返回结果
        // 4. 如果遍历完了都没找到匹配的，抛出 IllegalArgumentException
        //
        // 这是策略模式的核心 —— 你来写！

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

### 验收标准

```bash
./mvnw -pl llm-spring-ai compile
```

`BUILD SUCCESS` ✅

---

## 任务 4：写 Controller + 测试接口

### 目标

写一个 REST 接口，输入文件路径，返回解析后的 Document 列表。

### 需求

**新建文件**：`controller/RagController.java`

> 在 IDEA 中：右键 `com.cyril.llm.springai.controller` → New → Java Class → 输入 `RagController`

```java
package com.cyril.llm.springai.controller;

import com.cyril.llm.springai.rag.DocumentReaderStrategySelector;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * RAG 文档处理接口
 *
 * 当前只有一个 /rag/read 端点，后续可以扩展：
 * - /rag/split  → 文档分片
 * - /rag/embed  → 向量化
 * - /rag/search → 语义检索
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final DocumentReaderStrategySelector selector;

    @Autowired
    public RagController(DocumentReaderStrategySelector selector) {
        this.selector = selector;
    }

    /**
     * 读取文档
     *
     * 用法示例：
     * GET /rag/read?path=/Users/xxx/documents/test.pdf
     *
     * @param path 文件的绝对路径
     * @return 解析后的 Document 列表（JSON 格式）
     */
    @GetMapping("/read")
    public List<Document> readDocument(@RequestParam("path") String path) {
        // TODO:
        // 1. 用 path 创建一个 File 对象
        // 2. 检查文件是否存在 && 是否真的是文件（不是目录）
        //    - 不存在或不是文件 → 抛出 IllegalArgumentException
        // 3. 调用 selector.read(file) 并返回结果
        // 4. IOException 包装成 RuntimeException 抛出
        //
        // 你来写！

        throw new UnsupportedOperationException("TODO: 你来写");
    }
}
```

### 验收标准

```bash
# 启动应用
./mvnw -pl llm-spring-ai spring-boot:run
```

然后新开一个终端：

```bash
# 先用一个简单的 txt 文件测试（如果还没有测试文件，先随便建一个）
echo "Hello World, 这是测试文本。" > /tmp/test.txt
curl "http://localhost:8080/rag/read?path=/tmp/test.txt"
```

应该返回一个 JSON 数组，里面包含 Document 对象 ✅

> ⚠️ 如果你还没写代码（只有 throw），启动会成功但调用会报错。先把 TODO 写完。

---

## 任务 5：写数据清洗逻辑

### 目标

写一个清洗方法，处理原始 Document 中的噪声数据。

### 背景知识

直接从 Reader 出来的文本通常包含：
- **多余空白**：连续空格、制表符、多余换行
- **乱码符号**：从 PDF 提取时混入的控制字符、不可见字符
- **重复段落**：尤其是 PDF 的页眉页脚会在每页重复出现
- **大小写不统一**：英文大小写混乱影响后续检索

如果不做清洗直接分片+向量化，这些噪声会严重降低检索质量。比如：
- "机器学习" 和 "机器  学习" 向量化后距离很远
- 重复的页眉内容会让每页的向量都包含"第X页"，干扰相似度计算

### 需求

在 `RagController` 中增加 `cleanDocuments` 方法，并在 `readDocument` 的返回前调用它。

```java
/**
 * 文本清洗
 *
 * 清洗是一个「管道」：原始文本 → 去空白 → 去乱码 → 去重 → 干净文本
 * 你可以根据业务需求调整每一步的顺序或增减步骤。
 *
 * @param documents 原始文档列表
 * @return 清洗后的文档列表
 */
public List<Document> cleanDocuments(List<Document> documents) {
    // TODO:
    // 1. 判空：如果 documents 是 null 或空列表，直接返回
    // 2. 遍历每个 document：
    //    a. 如果 doc 为 null 或 doc.getText() 为 null，跳过
    //    b. 去掉多余空白：text.replaceAll("\\s+", " ").trim()
    //    c. 去掉乱码/特殊符号：只保留字母、数字、标点、空格、换行
    //       提示：text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "")
    //    d. 去重：按换行拆分段落，用 LinkedHashSet 去重，再拼回去
    //    e. 构造新的 Document 放回列表
    // 3. 返回清洗后的列表
    //
    // 你用到的类：
    // - org.springframework.util.CollectionUtils
    // - java.util.stream.Collectors
    // - java.util.LinkedHashSet
    //
    // 你来写！

    throw new UnsupportedOperationException("TODO: 你来写");
}
```

> 💡 **思考**：去重时用 `LinkedHashSet` 而不是 `HashSet`，为什么？
> 答案：`LinkedHashSet` 保持插入顺序，去重后段落还是按原来的先后顺序排列。

### 验收标准

重启应用后，用同一个文件对比清洗前后：

```bash
# 先用 PDF 文件测试（含噪声多，效果明显）
curl "http://localhost:8080/rag/read?path=/path/to/sample.pdf" | python3 -m json.tool | head -50
```

观察返回的 Document：
- 文本没有多余空格
- 没有乱码符号
- 没有重复段落
- 格式整齐

---

## 任务 6：用真实多类型文档跑通全流程 🎯

### 目标

这是最终大考。下载测试文档包，把所有类型的文档都跑一遍，验证整个链路。

### 步骤

**① 下载测试文档**

```bash
# 下载并解压到桌面
cd ~/Desktop
curl -o rag-test.zip "https://nfturbo-file.oss-cn-hangzhou.aliyuncs.com/llm/RAG%E6%9D%90%E6%96%99.zip"
unzip rag-test.zip -d rag-test
ls rag-test/
```

**② 逐一测试每种文件类型**

```bash
# 确保应用在运行中（./mvnw -pl llm-spring-ai spring-boot:run）

# 测试 TXT
curl "http://localhost:8080/rag/read?path=$(ls ~/Desktop/rag-test/*.txt | head -1)" | python3 -m json.tool | head -30

# 测试 PDF
curl "http://localhost:8080/rag/read?path=$(ls ~/Desktop/rag-test/*.pdf | head -1)" | python3 -m json.tool | head -30

# 测试 HTML（如果有）
curl "http://localhost:8080/rag/read?path=$(ls ~/Desktop/rag-test/*.html | head -1)" | python3 -m json.tool | head -30

# 测试 Markdown（如果有）
curl "http://localhost:8080/rag/read?path=$(ls ~/Desktop/rag-test/*.md | head -1)" | python3 -m json.tool | head -30

# 测试 Word（如果有）
curl "http://localhost:8080/rag/read?path=$(ls ~/Desktop/rag-test/*.docx | head -1)" | python3 -m json.tool | head -30
```

### 验收清单

把你测试的结果填一下：

| 文件类型 | 文件名 | 是否成功 | Document 数量 | 清洗效果 |
|---------|--------|---------|-------------|---------|
| TXT     |        | ✅ / ❌  |             |         |
| PDF     |        | ✅ / ❌  |             |         |
| HTML    |        | ✅ / ❌  |             |         |
| MD      |        | ✅ / ❌  |             |         |
| JSON    |        | ✅ / ❌  |             |         |
| Word    |        | ✅ / ❌  |             |         |

全部 ✅ 就算通关 🎉

---

## 🧠 理解自测（不用写代码，但要想清楚）

1. **为什么用策略模式而不是 if-else？**
   给你三分钟想，想不出来再往下看。
   <details><summary>点击看答案</summary>

   1. **开闭原则**：新增格式只需加一个类+@Component，不改选择器
   2. **单一职责**：每个 Reader 只管自己那一种格式
   3. **可测试性**：可以单独 mock 某个策略做单元测试
   4. **Spring 自动发现**：加 @Component 就自动注册，不需要改配置

   </details>

2. **PagePdfDocumentReader 和 ParagraphPdfDocumentReader 的区别，以及什么时候用哪个？**
   <details><summary>点击看答案</summary>

   - Page：按物理页切分，适合需要保留页码信息、或 PDF 排版复杂的场景
   - Paragraph：按语义段落切分，适合 QA 问答场景（RAG 首选），但依赖 PDF 结构标记
   - 如果 PDF 是扫描件（无文字层），两者都不管用，需要先 OCR

   </details>

3. **数据清洗为什么要在分片之前做？分片之后再做行不行？**
   <details><summary>点击看答案</summary>

   - 多余的空白、换行会影响分片算法的判断（比如按段落分片时，多余空行可能导致错误切分）
   - 重复内容如果在分片后去重，会导致语义完整的段落被割裂
   - 先清洗→再分片，保证每个分片都是干净的、语义完整的文本块

   </details>

4. **JsonReader 的限制是什么？如果遇到深层嵌套的 JSON 怎么办？**
   <details><summary>点击看答案</summary>

   - 不支持嵌套字段，只能提取第一层的 key
   - 嵌套 JSON 应该自定义实现：用 Jackson 解析 → 手动展平 → 构造 Document

   </details>

---

## 📖 你可能会用到的 import

```java
// Spring AI Document
import org.springframework.ai.document.Document;

// 各种 Reader
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.ExtractedTextFormatter;
import org.springframework.ai.reader.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;

// Spring 资源抽象
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

// 工具
import org.springframework.util.CollectionUtils;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
```

---

## 📋 文件结构总览（做完之后应该是这样）

```
llm-spring-ai/src/main/java/com/cyril/llm/springai/
├── SpringAiApplication.java              ← 已有
├── controller/
│   ├── ChatController.java               ← 已有
│   └── RagController.java                ← 任务4 + 任务5
└── rag/
    ├── DocumentReaderStrategy.java       ← 任务2
    ├── DocumentReaderStrategySelector.java ← 任务3
    └── reader/
        ├── TextReaderStrategy.java       ← 任务2
        ├── PdfReaderStrategy.java        ← 任务2
        ├── HtmlReaderStrategy.java       ← 任务2
        ├── MarkdownReaderStrategy.java   ← 任务2
        ├── JsonReaderStrategy.java       ← 任务2
        ├── TikaReaderStrategy.java       ← 任务2
        └── FallbackReaderStrategy.java   ← 任务2（可选）
```

---

## 🆘 卡住了怎么办

1. **先看 import**：上面的 import 列表里找需要的类
2. **看原文**：回头翻课程文档里的代码示例
3. **看 Spring AI 源码**：在 IDEA 里 `Cmd+Click` 进 Reader 类看源码，比文档更准确
4. **问我**：每个任务我都留了提示，如果还是写不出来，直接跟我说卡在哪个 TODO，我给你更多提示而不是直接给答案

**记住：写不出来不可怕，copy-paste 才可怕。你现在写的每一行都是以后面试能讲出来的。**
