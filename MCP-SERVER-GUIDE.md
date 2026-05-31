# 手把手：Spring AI MCP Server 实战教程

> 从零构建 MCP Server，掌握 Stdio / SSE / Streamable HTTP 三种传输模式

---

## 目录

- [0. 前置准备](#0-前置准备)
- [1. 第一步：启动类（1 分钟）](#1-第一步启动类-1-分钟)
- [2. 第二步：天气查询工具（10 分钟）](#2-第二步天气查询工具-10-分钟)
- [3. 第三步：POJO 入参出参（10 分钟）](#3-第三步pojo-入参出参-10-分钟)
- [4. 第四步：注册 MCP 工具（5 分钟）](#4-第四步注册-mcp-工具-5-分钟)
- [5. 第五步：配置三种模式（15 分钟）](#5-第五步配置三种模式-15-分钟)
- [6. 运行与测试](#6-运行与测试)
- [7. 深度理解：MCP 协议与 JSON-RPC 生命周期](#7-深度理解mcp-协议与-json-rpc-生命周期)
- [8. 接入 Cline 使用](#8-接入-cline-使用)
- [9. 常见错误与解决](#9-常见错误与解决)

---

## 0. 前置准备

### 0.1 什么是 MCP？

**MCP（Model Context Protocol）** 是 Anthropic 提出的开放协议，用于 AI 客户端（如 Cline、Claude Desktop）发现和调用外部工具。

类比理解：

```
传统 API:  前端 → 后端 API → 数据库
MCP:      AI 客户端 → MCP Server → 天气服务 / 数据库 / 文件系统
                       ↑
                  你写的 @Tool 方法
```

MCP 定义了三种传输方式：

| 模式 | 通信方式 | 适用场景 |
|------|----------|----------|
| **Stdio** | 标准输入输出 | 本地工具，无需网络 |
| **SSE** | HTTP 双端点 | 远程服务，需要推送 |
| **Streamable HTTP** | HTTP 单端点 | 远程服务，推荐替代 SSE |

### 0.2 模块文件清单

```
llm-mcp-server/
├── pom.xml                            # MCP Server 依赖
└── src/main/
    ├── java/com/cyril/llm/mcp/
    │   ├── McpServerApplication.java   # 启动类
    │   ├── config/
    │   │   └── McpConfig.java          # ToolCallbackProvider Bean
    │   ├── service/
    │   │   └── WeatherService.java     # @Tool 方法定义
    │   └── model/
    │       ├── WeatherRequest.java     # POJO 入参
    │       └── WeatherResponse.java    # POJO 出参
    └── resources/
        ├── application.yml             # 公共配置
        ├── application-stdio.yml       # Stdio 配置
        ├── application-sse.yml         # SSE 配置
        └── application-streamable.yml  # Streamable HTTP 配置
```

### 0.3 依赖说明

pom.xml 只引入了 `spring-ai-starter-mcp-server-webmvc`：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

这个 starter 包含了：
- Spring Boot Web（MVC）
- Spring AI MCP Server 核心
- MCP 的 SSE 和 Streamable HTTP 支持
- 自动配置（`@EnableAutoConfiguration`）

### 0.4 三个模式的切换方式

```bash
# 同一个 jar，用 spring.profiles.active 切换模式
java -jar llm-mcp-server.jar --spring.profiles.active=stdio
java -jar llm-mcp-server.jar --spring.profiles.active=sse
java -jar llm-mcp-server.jar --spring.profiles.active=streamable
```

---

## 1. 第一步：启动类（1 分钟）

打开 `McpServerApplication.java`，只需要最标准的 Spring Boot 启动类：

```java
package com.cyril.llm.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
```

**关键理解**：
- 不加任何额外配置！所有的 MCP 行为通过配置文件控制
- `@SpringBootApplication` 会自动扫描 `@Service`、`@Configuration` 等注解

---

## 2. 第二步：天气查询工具（10 分钟）

> 知识点：**@Tool 注解 —— 和你在 PDD 模块写的一模一样**

### 2.1 WeatherService.java

打开 `service/WeatherService.java`，实现一个简单的天气查询工具：

```java
package com.cyril.llm.mcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {

    @Tool(description = "根据城市名称查询天气信息")
    public String getWeather(String city) {
        if (city == null || city.isBlank()) {
            return "请提供城市名称";
        }
        return switch (city) {
            case "北京" -> "北京: 晴, 25°C";
            case "上海" -> "上海: 多云, 22°C";
            case "深圳" -> "深圳: 小雨, 28°C";
            default -> city + ": 下雪, -20°C";
        };
    }
}
```

### 2.2 和 PDD 的 OrderTools 对比

```
PDD 模块:
  @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
  → 通过 .tools(orderTools) 注册到 ChatClient
  → 在 AI 客服对话中由 LLM 决定调用

MCP 模块:
  @Tool(description = "根据城市名称查询天气信息")
  → 通过 ToolCallbackProvider Bean 注册到 MCP 协议
  → 由 Cline 等 MCP 客户端发现并调用
```

**工具定义是同一个 @Tool，但注册方式不同。** PDD 是 ChatClient 手动挂载，MCP 是全局自动注册。

---

## 3. 第三步：POJO 入参出参（10 分钟）

> 知识点：**@Tool 方法使用 POJO 作为入参和出参 ★★★**

### 3.1 什么时候需要用 POJO 入参？

当工具方法需要传入多个参数，参数之间有关联，或者参数有很多个时 ——

```java
// 不优雅：5 个基本类型参数，大模型很容易传错
@Tool(description = "查询天气")
public String queryWeather(String city, String date, String district,
                           String street, String unit) { ... }
```

```java
// 优雅：把参数封装成 POJO
@Tool(description = "根据城市和日期获取天气信息")
public WeatherResponse queryWeather(WeatherRequest request) { ... }
```

### 3.2 WeatherRequest.java

打开 `model/WeatherRequest.java`：

```java
package com.cyril.llm.mcp.model;

import org.springframework.ai.tool.annotation.ToolParam;

public class WeatherRequest {

    @ToolParam(description = "城市")
    private String city;

    @ToolParam(description = "日期")
    private String date;

    // ★ 故意用了含糊的字段名，看看 @ToolParam 的作用
    @ToolParam(description = "区县")
    private String i;

    @ToolParam(description = "街道")
    private String s;

    // getter 和 setter（必须！框架通过 setter 或构造器赋值）
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getI() { return i; }
    public void setI(String i) { this.i = i; }
    public String getS() { return s; }
    public void setS(String s) { this.s = s; }
}
```

### 3.3 @ToolParam 为什么重要？

对比两种写法，看大模型生成的 JSON 参数：

```
无 @ToolParam:
  → 大模型看到字段名为 "i" 和 "s"
  → 无法理解含义 → 可能会填错或留空

有 @ToolParam(description = "区县"):
  → 大模型看到 "区县" → 能正确提取用户提到的区县名
```

⚠️ **最佳实践**：**每个字段都要加 @ToolParam(description)**，不要相信大模型能猜对你的字段含义！

### 3.4 WeatherResponse.java

打开 `model/WeatherResponse.java`：

```java
package com.cyril.llm.mcp.model;

public class WeatherResponse {
    private String city;
    private String date;
    private String weather;
    private double temperature;

    // 必须有全参构造器（框架通过它或 setter 填充字段）
    public WeatherResponse(String city, String date, String weather, double temperature) {
        this.city = city;
        this.date = date;
        this.weather = weather;
        this.temperature = temperature;
    }

    // getter 和 setter
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}
```

### 3.5 回到 WeatherService，添加第二个 @Tool 方法

```java
@Service
public class WeatherService {

    // 第一个工具：简单入参
    @Tool(description = "根据城市名称查询天气信息")
    public String getWeather(String city) { ... }

    // 第二个工具：POJO 入参 ★
    @Tool(name = "query_weather_by_city_date",
          description = "根据城市和日期获取天气信息")
    public WeatherResponse queryWeather(WeatherRequest request) {
        try {
            Thread.sleep(5000); // 模拟调用外部 API 耗时
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        double temp = Math.random() * 15 + 10;
        return new WeatherResponse(
            request.getCity(),
            request.getDate(),
            "晴朗，有微风",
            temp
        );
    }
}
```

---

## 4. 第四步：注册 MCP 工具（5 分钟）

> 知识点：**ToolCallbackProvider —— MCP 的工具注册入口**

打开 `config/McpConfig.java`：

```java
package com.cyril.llm.mcp.config;

import com.cyril.llm.mcp.service.WeatherService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    /*
     * MethodToolCallbackProvider 会自动扫描 WeatherService 中
     * 所有带有 @Tool 注解的方法，注册到 MCP 协议中。
     *
     * MCP Server 启动时，Spring AI 的自动配置会读取这个 Bean，
     * 把工具列表通过 MCP 协议暴露给客户端（Cline 等）。
     *
     * 如果要注册多个 Service 中的工具，只需要：
     *   .toolObjects(weatherService, anotherService, ...)
     */
    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService)  // 扫描这个对象里的 @Tool 方法
                .build();
    }
}
```

**关键理解**：

```
PDD 的注册方式（手动绑定到某次对话）：
  chatClient.prompt().tools(orderTools).stream()

MCP 的注册方式（全局注册，协议发现）：
  @Bean ToolCallbackProvider → Spring AI 自动配置 → MCP 协议暴露
```

---

## 5. 第五步：配置三种模式（15 分钟）

> 知识点：**三种传输模式的 Spring 配置 + 各自的适用场景**

### 5.1 Stdio 模式（application-stdio.yml）

**原理**：程序通过标准输入接收 JSON-RPC 请求，通过标准输出返回结果。

**关键要求**：控制台输出**必须纯 JSON**，不能有任何多余字符！

```yaml
# 关闭 Web 服务
spring:
  main:
    web-application-type: none
    banner-mode: off

  ai:
    mcp:
      server:
        enabled: true
        name: mcp-server-stdio
        version: 1.0.0
        stdio: true       # 开启 Stdio 模式
        type: SYNC

# 关闭所有日志输出！
logging:
  level:
    root: OFF
```

**生命周期的每一帧 stdout 输出都是这种格式**：

```
← stdout 输出 →
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{...}}}
{"jsonrpc":"2.0","id":2,"result":{"tools":[...]}}
```

**有一条多余的 `System.out.println` 就会导致客户端解析失败！**

### 5.2 SSE 模式（application-sse.yml）

**原理**：双端点 HTTP 通信，一个端点接收请求，一个端点推送事件。

```
  客户端 ──────────────────────────── MCP Server
    │                                      │
    │──── GET /sse (订阅 SSE 流) ──────────→│  ← 听广播
    │←─── SSE: sessionId=xxx ──────────────│  ← 拿到的 sessionId
    │                                      │
    │──── POST /mcp/messages?sessionId=xx →│  ← 发消息
    │←─── SSE event (响应) ────────────────│  ← 响应通过 SSE 流推送
```

```yaml
server:
  port: 8003
  servlet:
    encoding:
      charset: UTF-8
      force: true
      enabled: true

spring:
  ai:
    mcp:
      server:
        enabled: true
        name: weather-sse-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages  # 客户端发送消息的 endpoint
        sse-endpoint: /sse                    # 客户端订阅 SSE 的 endpoint
```

### 5.3 Streamable HTTP 模式（application-streamable.yml）

**原理**：单端点 HTTP 通信，同时支持普通 JSON 响应和流式 SSE 响应。

**这是 MCP 官方推荐替代 SSE 的方案。** 优点：
- 单端点（不用维护两个 URL）
- 支持断线重连
- 支持未确认消息重发

```yaml
server:
  port: 8004
  servlet:
    encoding:
      charset: UTF-8
      force: true
      enabled: true

spring:
  ai:
    mcp:
      server:
        # STREAMABLE = 有状态模式（需要 Mcp-Session-Id）
        # STATELESS  = 无状态模式（每次请求独立）
        protocol: STREAMABLE
        name: streamable-mcp-server
        version: 1.0.0
        type: SYNC
        instructions: "这个服务是用来查询城市天气的。"
        streamable-http:
          mcp-endpoint: /api/mcp
          keep-alive-interval: 30s
```

**protocol 的两个值对比**：

| 值 | 状态 | Mcp-Session-Id | 适用场景 |
|----|------|---------------|----------|
| `STREAMABLE` | 有状态 | 需要 | 多轮交互、上下文依赖 |
| `STATELESS` | 无状态 | 不需要 | 单次调用、Serverless |

---

## 6. 运行与测试

### 6.1 编译

```bash
mvn -pl llm-mcp-server compile
```

### 6.2 Stdio 模式测试

```bash
# 启动（纯后台，没有 web 端口）
mvn -pl llm-mcp-server spring-boot:run \
  -Dspring-boot.run.profiles=stdio
```

Stdio 模式的测试需要通过标准输入发送 JSON-RPC 消息。可以用 Python 脚本或者 echo 管道：

```bash
# 简单的初始化请求
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}' | \
  java -jar target/llm-mcp-server-*.jar --spring.profiles.active=stdio
```

### 6.3 SSE 模式测试

```bash
# 启动
mvn -pl llm-mcp-server spring-boot:run \
  -Dspring-boot.run.profiles=sse
```

浏览器打开 `http://localhost:8003/sse`，会看到：

```
data:/mcp/messages?sessionId=xxxx-xxxx-xxxx-xxxx
```

用 Postman 或 curl 模拟 MCP Client 的生命周期：

**步骤 1：初始化**
```bash
curl -X POST "http://localhost:8003/mcp/messages?sessionId=xxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

**步骤 2：通知已初始化**
```bash
curl -X POST "http://localhost:8003/mcp/messages?sessionId=xxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"notifications/initialized"}'
```

**步骤 3：查询工具列表**
```bash
curl -X POST "http://localhost:8003/mcp/messages?sessionId=xxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

### 6.4 Streamable HTTP 模式测试

```bash
# 启动
mvn -pl llm-mcp-server spring-boot:run \
  -Dspring-boot.run.profiles=streamable
```

**请求头必须加 Accept: text/event-stream**（Streamable HTTP 可能返回 SSE 或普通 JSON）：

```bash
# 初始化
curl -X POST "http://localhost:8004/api/mcp" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

**注意响应头中的 `Mcp-Session-Id`，后续请求必须带上它：**

```bash
# 二次请求带 session
curl -X POST "http://localhost:8004/api/mcp" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "Mcp-Session-Id: xxxx-xxxx" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

---

## 7. 深度理解：MCP 协议与 JSON-RPC 生命周期

### 7.1 JSON-RPC 协议格式

MCP 使用 JSON-RPC 2.0 协议。每个消息有三个字段：

```json
{
  "jsonrpc": "2.0",          // 协议版本
  "id": 1,                   // 请求 ID（用于匹配请求和响应）
  "method": "tools/list",    // 方法名
  "params": {}               // 参数（可选）
}
```

### 7.2 MCP 生命周期三阶段

```
阶段 1：初始化
  客户端 → initialize → 服务端
  客户端 ← initialized result ← 服务端  （协商协议版本和能力）
  客户端 → notifications/initialized → 服务端  （确认准备就绪）

阶段 2：能力协商
  客户端 → tools/list → 服务端
  客户端 ← [{name:"getWeather", ...}] ← 服务端  （获取可用工具列表）
  客户端 → resources/list → 服务端
  客户端 → prompts/list → 服务端

阶段 3：工具调用
  客户端 → tools/call → 服务端
  客户端 ← {content: "北京: 晴, 25°C"} ← 服务端  （执行业务逻辑）
```

SSE 模式下，阶段 1 和 2 的响应通过 **SSE 事件流** 推送给客户端。Streamable HTTP 模式则直接在 HTTP 响应中返回。

### 7.3 Streamable HTTP 的特殊之处

Streamable HTTP 的请求头必须包含 `Accept: text/event-stream`：

```http
POST /api/mcp
Content-Type: application/json
Accept: text/event-stream   ← 必须声明
```

原因：服务端可能会返回两种格式 ——
- SSE 流（多事件）
- 普通 JSON（单次响应）
- 客户端必须提前声明自己支持这两种格式

### 7.4 无状态 (STATELESS) vs 有状态 (STREAMABLE)

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STATELESS   # 无状态
```

**无状态场景**：
- 客户端不需要 `Mcp-Session-Id`
- 每次请求完全独立
- 适合：查询天气、计算器、数据库查询等单次操作

**有状态场景**：
- 客户端需要 `Mcp-Session-Id`
- 服务端保存会话上下文
- 适合：多轮对话、文件编辑、git 操作等需要上下文的场景

---

## 8. 接入 Cline 使用

### 8.1 Stdio 模式接入

```json
{
  "mcpServers": {
    "weather-stdio": {
      "disabled": false,
      "timeout": 60,
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/path/to/llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar",
        "--spring.profiles.active=stdio"
      ]
    }
  }
}
```

### 8.2 SSE 模式接入

```json
{
  "mcpServers": {
    "weather-sse": {
      "type": "sse",
      "url": "http://127.0.0.1:8003/sse",
      "autoApprove": [],
      "timeout": 60,
      "disabled": false
    }
  }
}
```

### 8.3 Streamable HTTP 模式接入

```json
{
  "mcpServers": {
    "weather-streamable": {
      "url": "http://127.0.0.1:8004/api/mcp",
      "type": "streamableHttp",
      "timeout": 60,
      "disabled": false
    }
  }
}
```

---

## 9. 常见错误与解决

### 9.1 Stdio 启动报错

**现象**：启动后控制台输出非 JSON 内容，Cline 连接失败。

**原因**：控制台有 banner、日志或其他多余输出。

**解决**：检查 `application-stdio.yml` 中是否设置了：
```yaml
spring.main.banner-mode: off
logging.level.root: OFF
```
并且确认没有代码中调用 `System.out.println`（除了 @Tool 方法返回值）。

### 9.2 SSE 连接后收不到结果

**现象**：访问 `/sse` 拿到 sessionId，但发 POST 请求后没响应。

**原因**：postman/curl 只发送了 POST 请求，但响应是通过 SSE 推回来的。需要在同一个 session 的 SSE 连接上监听到结果。

**解决**：
1. 浏览器开两个 tab
2. Tab 1 打开 `http://localhost:8003/sse`，保持连接
3. 从 Tab 1 的页面内容中复制 sessionId
4. Tab 2 用 curl 发 POST 请求
5. 观察 Tab 1 的 SSE 流中是否出现响应

### 9.3 Streamable HTTP 报 400/415

**现象**：POST 请求返回 400 Bad Request。

**原因**：请求头缺少 `Accept: text/event-stream`。

**解决**：添加请求头：
```bash
curl -H "Accept: text/event-stream" ...
```

### 9.4 Mcp-Session-Id 错误

**现象**：Streamable HTTP 返回 session 相关的错误。

**原因**：有状态模式下没有携带正确的 `Mcp-Session-Id`。

**解决**：
1. 初始化请求中获取 `Mcp-Session-Id` 响应头
2. 后续所有请求在 Header 中带上：`Mcp-Session-Id: xxxx`

### 9.5 @Tool POJO 参数无法被识别

**现象**：大模型调用工具时把字段值传错了。

**原因**：POJO 字段没有 `@ToolParam(description)`，大模型无法理解字段含义。

**解决**：每个字段加上中文描述：
```java
@ToolParam(description = "区县")
private String i;
```

---

## 附录 A：三种模式对比总结

| 特性 | Stdio | SSE | Streamable HTTP |
|------|-------|-----|-----------------|
| 网络需求 | 不需要 | 需要 HTTP | 需要 HTTP |
| 端点数量 | 0（stdin/stdout） | 2 | 1 |
| 持续连接 | 持续 | 持续 | 支持断开重连 |
| 状态管理 | 无状态 | 有状态（session） | 有状态/无状态可选 |
| 适合场景 | 本地工具 | 远程服务 | 远程服务（推荐） |
| 启动方式 | `java -jar` | `java -jar` | `java -jar` |

## 附录 B：核心概念对照表

| 术语 | 解释 |
|------|------|
| MCP | Model Context Protocol，AI 客户端与工具服务的通信协议 |
| JSON-RPC 2.0 | MCP 使用的请求-响应协议格式 |
| Stdio | 通过 stdin/stdout 通信的 MCP 传输方式 |
| SSE | Server-Sent Events，服务端推送技术（MCP 的 HTTP 传输方式之一）|
| Streamable HTTP | MCP 最新传输标准，单端点支持流式响应 |
| ToolCallbackProvider | Spring AI 中把 @Tool 注册到 MCP 协议的桥梁 |
| MethodToolCallbackProvider | 自动扫描对象中 @Tool 方法的实现 |
| Mcp-Session-Id | Streamable HTTP 有状态模式中用于标识会话的 Header |
