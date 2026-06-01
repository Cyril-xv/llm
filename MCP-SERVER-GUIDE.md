# 🧭 MCP Server 实战教程

> 看不懂的术语不硬记，先看它在终端里长什么样

---

## 📖 你能从这个教程学到什么？

```
写完代码 + 跑完这 3 种模式 = 你就能说清楚：
  ❓ MCP 是什么
  ❓ Stdio / SSE / Streamable HTTP 有什么区别
  ❓ 为什么同一个 jar 可以换 3 种方式启动
  ❓ 你刚才在终端看到的那段 JSON 是什么意思
```

---

## ⚡ 准备工作：先跑起来再说

### 第 0 课：你的代码在哪里？

```
llm-mcp-server/
├── pom.xml                                  ← 只依赖一个 spring-ai-starter-mcp-server-webmvc
├── src/main/java/com/cyril/llm/mcp/
│   ├── McpServerApplication.java            ← @SpringBootApplication 启动类（最简单的那种）
│   ├── config/McpConfig.java                ← 把 @Tool 注册到 MCP 协议
│   ├── service/WeatherService.java          ← 你写的 2 个 @Tool 方法
│   └── model/
│       ├── WeatherRequest.java              ← POJO 入参（city, date, i, s）
│       └── WeatherResponse.java             ← POJO 出参（city, date, weather, temperature）
└── src/main/resources/
    ├── application.yml                      ← 公共配置（application.name 等）
    ├── application-stdio.yml                ← Stdio 模式配置
    ├── application-sse.yml                  ← SSE 模式配置
    └── application-streamable.yml           ← Streamable HTTP 模式配置
```

### 第 1 课：怎么切换模式？

**答案**：同一个 jar，启动时加 `--spring.profiles.active=` 参数切换。

```bash
# 三种模式就这一行不同：
java -jar llm-mcp-server.jar --spring.profiles.active=stdio
java -jar llm-mcp-server.jar --spring.profiles.active=sse
java -jar llm-mcp-server.jar --spring.profiles.active=streamable
```

---

## 👀 第 2 课：你在终端到底看到了什么？

回忆一下，你跑完这条命令：

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}' \
  | java -jar xxx.jar --spring.profiles.active=stdio
```

终端打印了一段 JSON：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {"listChanged": true},
      "prompts": {"listChanged": true},
      "resources": {...}
    },
    "serverInfo": {
      "name": "mcp-server-stdio",
      "version": "1.0.0"
    }
  }
}
```

**这不是乱码，这是 MCP 协议在工作！** 我们来逐层拆解。

### 2.1 你做了什么事？

```
你（电脑前）                    MCP Server（Java 进程）
  │                                   │
  │—— echo '{"jsonrpc":"2.0",...}' ──→│  你往它的"耳朵"（stdin）说了一句话
  │                                   │
  │←—— {"result":{"serverInfo":...}} —│  它往你的"眼睛"（stdout）回了一句话
  │                                   │
```

> **Stdio = Standard I/O（标准输入输出）**
> 就像两个人面对面说话，你说一句它回一句。
> 不需要网络，不需要浏览器，不需要端口号。

### 2.2 那句 JSON 是什么意思？

你发出去的（请求）：

| 字段 | 值 | 意思 |
|------|----|------|
| `jsonrpc` | `"2.0"` | "我用的是 JSON-RPC 2.0 协议" |
| `id` | `1` | "这是编号 1 的请求" |
| `method` | `"initialize"` | "初次见面，握个手" |
| `params` | `{...}` | "这是我的能力信息" |

服务端返回的（响应）：

| 字段 | 值 | 意思 |
|------|----|------|
| `id` | `1` | "这是对编号 1 请求的回复" |
| `result.serverInfo.name` | `"mcp-server-stdio"` | "我叫 mcp-server-stdio" |
| `result.serverInfo.version` | `"1.0.0"` | "我的版本是 1.0.0" |
| `result.capabilities.tools` | `{...}` | "我支持工具调用" |

> **合起来就是：**
> 你："你好，我叫 test-client，版本 1.0，我用 JSON-RPC 2.0 协议"
> 服务端："你好，我叫 mcp-server-stdio 版本 1.0.0，我会查天气工具"

---

## 🤔 第 3 课：为什么我们要搞 3 种模式？

本质是解决同一个问题：**AI 客户端（Cline、Chatbox）怎么调用你的 @Tool 方法？**

但是通信方式不同：

### 模式 1：Stdio —— 面对面说话

```
AI 客户端（Cline）               MCP Server（Java 进程）
     │                                │
     │—— 启动 java 进程 ————————————→│  Cline 帮你把程序跑起来
     │                                │
     │—— stdin: "查北京天气" ————————→│  Cline 通过键盘（stdin）问你
     │                                │
     │←—— stdout: "北京: 晴, 25°C" ——│  你通过屏幕（stdout）回答
     │                                │
     │—— ... 持续对话 ... ———————————→│  进程不退出，一直聊
     │                                │
     │—— 关闭进程 ————————————————————→│  聊完了关掉
```

**特点：**
- ✅ 不依赖网络
- ✅ 不需要 Web 服务器
- ❌ 必须在同一台机器上

**配置文件 `application-stdio.yml` 为什么这样写？**

```yaml
spring:
  main:
    web-application-type: none   # "我不需要 Tomcat Web 服务器"—— 因为我是直接 stdin/stdout
    banner-mode: off             # "不要打印 Spring 的大 Logo" —— 会污染 JSON 输出

  ai:
    mcp:
      server:
        stdio: true              # "我用 Stdio 模式"
        type: SYNC               # "一问一答，同步处理"

logging:
  level:
    root: OFF                    # "所有日志都关了" —— 多于一个字符就坏了
```

> ⚠️ **为什么日志和 banner 必须关？**
>
> 假设你给 Cline 配了 Stdio，Cline 在后台启动了这个 jar。
> Cline 一直在读这个程序的 stdout，等待 JSON 格式的回复。
> 如果程序打印了 `[INFO] 2026-06-01 10:00:00 Started Application`，
> Cline 解析这个非 JSON 内容 —— **报错！**
>
> 所以 Stdio 模式下，程序 stdout 只能输出 JSON，不能有别的。

---

### 模式 2：SSE —— 微信消息 + 收音机

首先你要理解什么是 SSE：

> **SSE = Server-Sent Events（服务端推送）**
> 普通 HTTP：  你问一句，它答一句（一问一答）
> SSE：        你打开一个连接，不用问，服务端自己会不断发消息过来

SSE 模式用**两个端点**：

```
                              MCP Server（Web 服务）
                                  │
    ASK 端点（写信）              │    PUSH 端点（听广播）
  POST /mcp/messages              │    GET /sse
      │                           │        │
      │                           │←———————│ 客户端打开收音机
      │                           │        │  服务端说："你的 sessionId 是 xyz"
      │                           │        │
      │————————→                  │        │  客户端写信问"有哪些工具？"
      │  "帮我查北京天气"          │        │
      │                           │————————→│  回复走广播通道推回去
      │                           │  "北京: 晴, 25°C"
      │                           │        │
```

**为什么 SSE 需要两个端点？**

因为 HTTP 本身是"你问我答"的。服务端想主动发消息给客户端，普通的 HTTP 做不到。
所以 SSE 模式拆成：
1. **一个 GET /sse 连接** —— 一直开着，服务端有消息就往上推（单向广播）
2. **一个 POST /mcp/messages** —— 客户端发请求到这里（单向投信）

两个合起来就是双向通信。

**配置文件的关键配置：**

```yaml
server:
  port: 8003                               # 启动 Web 服务，端口 8003

spring:
  ai:
    mcp:
      server:
        sse-message-endpoint: /mcp/messages  # "写信的地址"
        sse-endpoint: /sse                   # "听广播的地址"
```

**测试方法（需要 2 个终端）：**

```bash
# 终端 1：启动服务
java -jar llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse

# 终端 2：开广播（打开一个长连接，保持不关）
curl -N http://localhost:8003/sse
# 输出: data:/mcp/messages?sessionId=xxxx-xxxx

# 终端 3：发消息
curl -X POST "http://localhost:8003/mcp/messages?sessionId=xxxx-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
# 注意！这条命令的响应不会出现在终端 3，而是出现在终端 2 的 SSE 流里！
```

---

### 模式 3：Streamable HTTP —— 一个视频电话搞定

**SSE 的问题**：两个端点，还要维护一个长连接，麻烦。

**Streamable HTTP 的改进**：一个端点就够了。

```
                              MCP Server（Web 服务）
                                  │
                        POST /api/mcp
                              │
    ｜——————————→              │
    ｜ "初始化+查工具"          │
    ｜                         │
    ｜←——————————              │
    ｜  一次 HTTP 响应返回全部结果
    ｜  可能包含多个事件
```

**和 SSE 的核心区别：**

| | SSE | Streamable HTTP |
|--|-----|-----------------|
| 端点数量 | 2 个 | 1 个 |
| 是否需要长连接 | 是的，/sse 一直开着 | 不需要，请求-响应模式 |
| 断线重连 | 需自己实现 | 内置支持 |
| 状态管理 | 只有有状态 | 有状态/无状态可选 |

**配置文件的关键配置：**

```yaml
server:
  port: 8004

spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE           # STREAMABLE（有状态）或 STATELESS（无状态）
        streamable-http:
          mcp-endpoint: /api/mcp        # "唯一的通信地址"
          keep-alive-interval: 30s      # "30 秒发一次心跳"
```

**protocol 的两个值：**

| protocol | 需要 Mcp-Session-Id？ | 含义 |
|----------|----------------------|------|
| `STREAMABLE` | 需要 | 服务端记得你是谁（有状态） |
| `STATELESS` | 不需要 | 每次请求都是新人（无状态） |

---

## 🆚 第 4 课：三张图对比三种模式

### 一句话总结

```
Stdio：         "面对面说话"     —— 本地，不要网络，不要端口
SSE：           "写信+收音机"    —— 远程，两个地址（一个写一个听）
Streamable HTTP："打视频电话"     —— 远程，一个地址搞定
```

### 一张表对比

| | Stdio | SSE | Streamable HTTP |
|--|-------|-----|----------------|
| **通信方式** | stdin/stdout（管道） | HTTP 双端点 | HTTP 单端点 |
| **需要网络？** | ❌ 不需要 | ✅ 需要 | ✅ 需要 |
| **需要 Web 服务？** | ❌ 不需要 | ✅ 需要 Tomcat | ✅ 需要 Tomcat |
| **端点/地址数** | 0 个 | 2 个（/sse + /mcp/messages） | 1 个（/api/mcp）|
| **适合谁用？** | Cline、Chatbox 等桌面工具 | 远程服务器上的服务 | 推荐替代 SSE |
| **你配了什么端口？** | 无 | 8003 | 8004 |

### 你代码中的三个 yml 文件对照

**application.yml（公共配置——三种模式都要的）**
```yaml
spring:
  application:
    name: llm-mcp-server
```

**application-stdio.yml（Stdio 特有）**
```yaml
web-application-type: none    ← 不开 Web 服务
stdio: true                   ← 用 Stdio 模式
logging.level.root: OFF       ← 静默输出
```

**application-sse.yml（SSE 特有）**
```yaml
server.port: 8003              ← 开 Web 服务
sse-endpoint: /sse             ← 广播地址
sse-message-endpoint: /mcp/messages   ← 写信地址
```

**application-streamable.yml（Streamable HTTP 特有）**
```yaml
server.port: 8004              ← 开 Web 服务
protocol: STREAMABLE           ← 有状态模式
mcp-endpoint: /api/mcp         ← 唯一地址
keep-alive-interval: 30s       ← 心跳
```

---

## 🔄 第 5 课：MCP 协议的三次握手（生命周期）

不管是哪种模式，MCP 协议的对话流程都一样，分为 3 个阶段：

```
阶段 1：握手（initialize）
  客户端 → {"method":"initialize"}     → 服务端
  客户端 ← {"result":{"serverInfo":...}} ← 服务端
  （双方确认身份和能力）

阶段 2：发现（notifications/initialized + tools/list）
  客户端 → {"method":"notifications/initialized"} → 服务端
  （客户端说"我准备好了"）
  客户端 → {"method":"tools/list"}               → 服务端
  客户端 ← {"result":{"tools":["getWeather",...]}} ← 服务端
  （客户端问"你会啥？"，服务端回答工具列表）

阶段 3：调用（tools/call）
  客户端 → {"method":"tools/call","params":{"name":"getWeather","arguments":{"city":"北京"}}}
  客户端 ← {"result":{"content":"北京: 晴, 25°C"}}
  （客户端说"查北京天气"，服务端执行并返回）
```

> **你现在懂了为什么刚才 tools/list 没返回了吧？**
>
> 你用管道 `|` 一次性发送了 `initialize`，成功了。
> 但服务端回复后还想读下一段 stdin，结果 stdin 已经关闭了（管道只送了一次数据）。
>
> 在真实的 Cline 里，Cline 会保持进程一直运行，持续读写 stdin/stdout。

---

## 🛠️ 第 6 课：你的 @Tool 方法是怎么被发现的？

**两个关键问题：**

### Q1：为什么 @Tool 方法能被 MCP 客户端发现？

**答案**：`McpConfig.java` 中的 `ToolCallbackProvider` Bean。

```java
@Configuration
public class McpConfig {
    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService) // ← 扫描 WeatherService 的所有 @Tool 方法
                .build();
    }
}
```

```
Spring 启动时：
  1. 扫描到 McpConfig → 发现 @Bean weatherTools
  2. MethodToolCallbackProvider 自动扫描 WeatherService 里的 @Tool 方法
  3. Spring AI MCP 自动配置读取这个 Bean
  4. MCP 客户端调用 tools/list → 服务端返回所有 @Tool 方法列表
```

### Q2：和 PDD 模块的 @Tool 有什么不同？

```
PDD 模块的 @Tool：
  你写 @Tool → 通过 .tools(orderTools) 手动挂载到某一次对话
  → 只在这次对话中可用
  
MCP 模块的 @Tool：
  你写 @Tool → 通过 ToolCallbackProvider Bean 注册到 MCP 协议
  → 所有 MCP 客户端（Cline、Chatbox）都能发现和调用
```

**工具定义是一样的@Tool，但注册方式不同。**

---

## 🎮 第 7 课：动手测试

### 先把 jar 打好

```bash
cd "/Users/yangxu/idea Projects/llm"

# 打包
./mvnw -pl llm-mcp-server package -DskipTests
```

### 7.1 测试 Stdio 模式

```bash
# 初始化握手
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  | java -jar llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio

# 如果正常 → 你会看到一段 JSON 返回（里面有 serverInfo 和 capabilities）
```

### 7.2 测试 SSE 模式

需要打开**三个终端**：

```bash
# 终端 1：启动 SSE 服务
java -jar llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse

# 终端 2：订阅 SSE（打开广播，保持运行不要关）
curl -N http://localhost:8003/sse

# 从终端 2 的输出中复制 sessionId（类似 xxxx-xxxx-xxxx-xxxx）

# 终端 3：发送初始化请求
curl -X POST "http://localhost:8003/mcp/messages?sessionId=xxxx-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

> 观察终端 2 中是否出现了响应内容。

### 7.3 测试 Streamable HTTP 模式

```bash
# 终端 1：启动 Streamable HTTP 服务
java -jar llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=streamable

# 终端 2：发送初始化请求（一个端点就够了）
curl -X POST "http://localhost:8004/api/mcp" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

---

## 🚀 第 8 课：接入 Cline / Chatbox

### Stdio 模式接入（推荐本地用）

在 Cline 的 MCP 配置中：

```json
{
  "mcpServers": {
    "weather": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/Users/yangxu/idea Projects/llm/llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar",
        "--spring.profiles.active=stdio"
      ]
    }
  }
}
```

### SSE 模式接入（远程服务）

先把 jar 部署到服务器上启动（端口 8003），然后在 Cline 中配置：

```json
{
  "mcpServers": {
    "weather-sse": {
      "type": "sse",
      "url": "http://你的服务器IP:8003/sse"
    }
  }
}
```

### Streamable HTTP 模式接入

```json
{
  "mcpServers": {
    "weather-streamable": {
      "type": "streamableHttp",
      "url": "http://你的服务器IP:8004/api/mcp"
    }
  }
}
```

---

## ⚠️ 第 9 课：常见问题

### Q：Stdio 启动后看到 [INFO] 日志怎么办？

**原因**：Maven 启动方式（`mvn spring-boot:run`）会有 Maven 自己的日志输出，这些会污染 JSON 流。

**解决**：改成 `java -jar` 直接启动（如本教程所示）。不要用 `mvn` 启动 Stdio 模式。

### Q：SSE 模式发 POST 请求后没收到响应？

**原因**：SSE 的响应不是通过 POST 响应的，而是通过 SSE 广播推送给你的。

**检查**：看订阅了 `/sse` 的终端/浏览器是否收到了响应内容。

### Q：Streamable HTTP 返回 400？

**原因**：缺少请求头 `Accept: text/event-stream`。

**解决**：加请求头：
```bash
curl -H "Accept: text/event-stream" ...
```

### Q：@Tool 方法的 POJO 字段传错值？

**原因**：POJO 字段没有 `@ToolParam(description)`。

**解决**：每个字段加中文描述：
```java
@ToolParam(description = "区县")
private String i;  // 字段名是 i，但大模型看到的是"区县"
```

---

## 📋 总结：一句话记住 MCP

```
MCP 就是让 AI 客户端（Cline）能发现和调用你写的 @Tool 方法。

3 种模式只是通信方式不同：
  Stdio         → 面对面说话
  SSE           → 写信 + 收音机
  Streamable    → 视频电话

代码写法和 @Tool 注解都一样，仅配置 yml 不同。
```