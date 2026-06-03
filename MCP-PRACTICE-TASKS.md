# 🛠️ MCP Server & Client 实战练习任务

> 任务驱动学习 —— 你写代码，我验收



## 📌 练习顺序

```
任务 1（建模块）
  ↓
任务 2（手动 Stdio）← 能彻底理解 Client-Server 怎么通信
  ↓
任务 3（自动配置）
  ↓
任务 4（ChatClient 整合）← 做完就知道 MCP 在实际项目怎么用
  ↓
任务 5（SSE 手动连接）
  ↓
任务 6（Streamable 手动连接）
```

---

## 任务 1：新建 llm-mcp-client 模块

### 目标

建一个空的 Maven 子模块，能编译通过。

### 需求

1. **修改父 pom.xml**，在 `<modules>` 中加上：

```xml
<module>llm-mcp-client</module>
```

2. **新建 `llm-mcp-client/pom.xml`**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.cyril</groupId>
        <artifactId>llm</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>llm-mcp-client</artifactId>

    <dependencies>
        <!-- MCP Client 核心依赖 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-client</artifactId>
        </dependency>

        <!-- 传统 Web 项目用 webmvc，响应式项目用 webflux，二选一 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

</project>
```

3. **新建目录结构和启动类**：

```
llm-mcp-client/
├── pom.xml
└── src/main/java/com/cyril/llm/mcp/client/
    └── McpClientApplication.java    ← 标准 @SpringBootApplication
```

4. **新建 `src/main/resources/application.yml`**，内容先留空

### 验收标准

```bash
cd "/Users/yangxu/idea Projects/llm"
./mvnw -pl llm-mcp-client compile
```

输出 `BUILD SUCCESS` ✅

---

## 任务 2：手动连接 Stdio Server（理解协议本质）

### 目标

不依赖框架自动配置，自己写代码手动连接你的 MCP Server（Stdio 模式），走一遍完整的 JSON-RPC 生命周期。

### 前置条件

确保你的 `llm-mcp-server` 已经打包好：

```bash
./mvnw -pl llm-mcp-server package -DskipTests
```

jar 路径：`llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar`

### 需求

在 `llm-mcp-client` 模块中新建类：`com.cyril.llm.mcp.client.ManualStdioClient`

```java
package com.cyril.llm.mcp.client;

import org.springframework.stereotype.Component;
// 其他 import 你自己加

@Component
public class ManualStdioClient {

    public void callWeatherTool() throws Exception {
        // ─────────────────────────────────────
        // 步骤 1：配置要启动的 MCP Server 进程
        // ─────────────────────────────────────
        // 相当于在终端执行：
        //   java -jar /路径/llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
        //
        // 用 ServerParameters.builder("java") 来构造
        // 参数是 -jar 和 jar 包的绝对路径，再加 --spring.profiles.active=stdio
        // TODO

        // ─────────────────────────────────────
        // 步骤 2：构建 StdioClientTransport
        // ─────────────────────────────────────
        // new StdioClientTransport(parameters, McpJsonDefaults.getMapper())
        // TODO

        // ─────────────────────────────────────
        // 步骤 3：创建 McpSyncClient
        // ─────────────────────────────────────
        // McpClient.sync(transport)
        //     .clientInfo(new McpSchema.Implementation("my-client", "1.0"))
        //     .requestTimeout(Duration.ofSeconds(10))
        //     .build();
        // TODO

        // ─────────────────────────────────────
        // 步骤 4：握手初始化
        // ─────────────────────────────────────
        // client.initialize()
        // TODO

        // ─────────────────────────────────────
        // 步骤 5：查询工具列表
        // ─────────────────────────────────────
        // client.listTools() 或 client.sendToolListRequest()
        // 把结果打印到控制台
        // TODO

        // ─────────────────────────────────────
        // 步骤 6：调用 getWeather 工具
        // ─────────────────────────────────────
        // 构建 CallToolRequest:
        //   McpSchema.CallToolRequest.builder()
        //       .name("getWeather")
        //       .arguments(Map.of("city", "北京"))
        //       .build();
        //
        // client.callTool(request)
        // 打印返回结果
        // TODO

        // ─────────────────────────────────────
        // 步骤 7：关闭连接
        // ─────────────────────────────────────
        // client.closeGracefully()
        // TODO
    }
}
```

### 验收标准

```bash
# 不需要提前启动 MCP Server！
# MCP Client 会自动启动一个 Java 进程运行 Server
./mvnw -pl llm-mcp-client compile
```

写完跑起来后，控制台会打印出两样东西：
1. 工具列表（里面应该有 getWeather 和 query_weather_by_city_date）
2. `getWeather("北京")` 的返回结果：`"北京: 晴, 25°C"`

### 理解要点

- `StdioClientTransport` 会在后台 **启动一个新的 Java 进程** 运行你的 MCP Server
- Client 和 Server 通过 **该进程的 stdin/stdout** 通信
- 不需要提前手动启动 Server，Client 自己会启动

---

## 任务 3：通过配置文件自动注入 MCP Client

### 目标

用 `application.yml` 配置 Stdio Server 连接信息，让 Spring AI 自动注入 `List<McpSyncClient>`。

### 需求

1. **修改 `src/main/resources/application.yml`**：

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: my-mcp-client
        version: 1.0.0
        request-timeout: 60s
        type: SYNC
        stdio:
          connections:
            weather-stdio:
              command: java
              args:
                - -jar
                - "/你的绝对路径/llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar"
                - "--spring.profiles.active=stdio"
```

> ⚠️ **注意**：args 中 jar 的路径要写**绝对路径**，可以用 Finder 定位 jar 文件后拖入终端获得完整路径。

2. **新建 `com.cyril.llm.mcp.client.runner.AutoMcpClientRunner`**：

实现 `CommandLineRunner`，在 Spring 启动后自动执行：

```java
@Component
public class AutoMcpClientRunner implements CommandLineRunner {

    // Spring AI 自动配置会把所有配置的 MCP Server 注入到这个 List
    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Override
    public void run(String... args) throws Exception {
        // TODO：
        // 1. 遍历 mcpSyncClients
        // 2. 打印每个 client 的 getClientInfo() 和 getServerInfo()
        // 3. 调用 tools/list 并打印结果
        // 4. 调用 getWeather(city="上海") 并打印结果
        //    （提示：用 McpSchema.CallToolRequest.builder()）
    }
}
```

### 验收标准

启动应用后控制台打印：

```
clientInfo: {"name":"my-mcp-client","version":"1.0.0"}
serverInfo: {"name":"mcp-server-stdio","version":"1.0.0"}
工具列表: [...]
调用结果: 上海: 多云, 22°C
```

---

## 任务 4：MCP 工具接入 ChatClient

### 目标

把 MCP Server 的 @Tool 挂载到 ChatClient 上，让大模型自己决定什么时候调工具。

### 前置条件

需要引入 OpenAI 的 ChatModel 依赖（在 `llm-mcp-client/pom.xml` 中加）：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

并在 `application.yml` 配置 OpenAI API Key（你的 key 填你自己有的）：

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-key-here
      chat:
        options:
          model: gpt-4o
```

### 需求

1. **新建 `com.cyril.llm.mcp.client.config.ChatClientConfig`**：

```java
@Configuration
public class ChatClientConfig {

    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    @Autowired
    private ChatModel chatModel;

    @Bean
    public ChatClient chatClient() {
        // TODO:
        // 1. 从 toolCallbackProvider.getToolCallbacks() 拿到所有工具回调
        // 2. 构建 ChatClient: ChatClient.builder(chatModel)
        //       .defaultToolCallbacks(toolCallbacks)
        //       .build()
    }
}
```

2. **新建 `com.cyril.llm.mcp.client.controller.ChatController`**：

```java
@RestController
public class ChatController {

    @Autowired
    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        // TODO: 调用 chatClient.prompt().user(message).call().content()
        // 返回大模型的回答
    }
}
```

### 验收标准

先确保 MCP Server 的 jar 包是最新的：

```bash
./mvnw -pl llm-mcp-server package -DskipTests
```

启动 `llm-mcp-client` 后访问：

```bash
curl "http://localhost:8080/chat?message=北京天气怎么样？"
```

返回结果应该包含 `"北京: 晴, 25°C"` 或类似的天气信息。

---

## 任务 5：手动连接 SSE Server

### 目标

手动构建一个连接 SSE 模式的 MCP Client。

### 前置条件

需要引入 WebFlux 依赖（SSE 底层依赖反应式 HTTP 客户端）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 需求

**新建 `com.cyril.llm.mcp.client.ManualSseClient`**：

```java
@Component
public class ManualSseClient {

    public void callViaSse() throws Exception {
        // TODO:
        // 1. 构建 HttpClientSseClientTransport:
        //    HttpClientSseClientTransport.builder("http://localhost:8003")
        //        .sseEndpoint("/sse")
        //        .build()
        //
        // 2. 创建 McpSyncClient（同任务 2）
        //
        // 3. 初始化
        //
        // 4. 调 tools/list
        //
        // 5. 调 getWeather(city="深圳")
        //
        // 6. 关闭
    }
}
```

### 如何测试

需要**两个终端**：

```
终端 1（MCP Server，SSE 模式）：
  java -jar /路径/llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse

终端 2（运行 Client）：
  启动 llm-mcp-client，调用 ManualSseClient
```

### 验收标准

终端 2 控制台打印天气结果，终端 1 能看到 HTTP 请求日志。

---

## 任务 6：手动连接 Streamable HTTP Server

### 目标

手动构建连接 Streamable HTTP 模式的 MCP Client。

### 前置条件

WebFlux 依赖已在任务 5 中加入。

### 需求

**新建 `com.cyril.llm.mcp.client.ManualStreamableClient`**：

```java
@Component
public class ManualStreamableClient {

    public void callViaStreamable() throws Exception {
        // TODO:
        // 1. 构建 HttpClientStreamableHttpTransport：
        //    注意：baseUri 和 endpoint 要分开写！
        //    HttpClientStreamableHttpTransport.builder("http://localhost:8004")
        //        .endpoint("/api/mcp")
        //        .build()
        //
        // 2. 创建 McpSyncClient（同任务 2）
        //
        // 3. 初始化
        //
        // 4. 调 tools/list
        //
        // 5. 调 getWeather(city="广州")
        //
        // 6. 关闭
    }
}
```

> ⚠️ 注意 endpoint 和 baseUri **一定要分开写**：
> - 正确：`builder("http://localhost:8004").endpoint("/api/mcp")`
> - 错误：`builder("http://localhost:8004/api/mcp")` 或 `builder("http://localhost:8004").endpoint("api/mcp")`（缺少 `/`）

### 如何测试

需要**两个终端**：

```
终端 1（MCP Server，Streamable HTTP 模式）：
  java -jar /路径/llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=streamable

终端 2（运行 Client）：
  启动 llm-mcp-client
```

### 验收标准

控制台打印天气结果。

---

## 📖 提示

### 你可能会用到的 import

```java
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
```

### 如果你卡住了

- 每个任务都有 TODO 标记，先自己尝试填
- **写不出来不要硬撑**，随时问我，我给你提示而不是直接给答案
- 想跳过某个任务也可以，跟我说一声就行