# 手把手：Spring AI 智能客服实战教程

> 从零开始，用 6 个步骤构建一个 PDD 自动退款智能客服，掌握提示词工程、Function Calling、对话记忆、流式输出、结构化输出五大核心技能。

---

## 目录

- [0. 准备篇：先理解整体架构](#0-准备篇先理解整体架构)
- [1. 第一步：数据模型（2 分钟）](#1-第一步数据模型-2-分钟)
- [2. 第二步：模拟退款服务（2 分钟）](#2-第二步模拟退款服务-2-分钟)
- [3. 第三步：Function Calling 工具（10 分钟，核心）](#3-第三步function-calling-工具-10-分钟核心)
- [4. 第四步：ChatConfig 配置（15 分钟，核心）](#4-第四步chatconfig-配置-15-分钟核心)
- [5. 第五步：Controller 接口（20 分钟，核心）](#5-第五步controller-接口-20-分钟核心)
- [6. 第六步：启动类（1 分钟）](#6-第六步启动类-1-分钟)
- [7. 运行验证](#7-运行验证)
- [8. 深度理解：每个知识点发生了什么](#8-深度理解每个知识点发生了什么)
- [9. 调试技巧](#9-调试技巧)
- [10. 常见错误与解决](#10-常见错误与解决)

---

## 0. 准备篇：先理解整体架构

### 0.1 这个项目做什么？

模拟 PDD（拼多多）的智能客服：用户反馈商品质量问题 → AI 自动识别 → 自动帮用户发起退款。

```
用户: "我买的衣服袖口开线了，质量太差了"
  ↓
AI 客服: "非常抱歉！您是说袖口已经完全开线了，对吗？"    ← 第一步：共情+确认
  ↓
用户: "对的"
  ↓
AI 客服: "我完全理解。我将立即为您发起退款申请..."        ← 第二步：执行退款(调用工具)
  ↓
AI 客服: "退款流程已启动，您无需再进行其他操作..."        ← 第三步：安抚+闭环
```

### 0.2 数据流全景图

```
                    ┌─────────┐
                    │  前端    │  index.html (聊天界面)
                    └────┬────┘
            GET /newChat  │  GET /ask?question=...&chatId=...
                         │
              ┌──────────┴──────────┐
              │  PddRefundController │
              │                      │
              │  /newChat → 非流式   │
              │  /ask     → 流式 SSE │
              └──────────┬──────────┘
                         │
              ┌──────────┴──────────┐
              │     ChatClient      │  ← ChatConfig 中配置
              │                      │
              │  .defaultSystem()  ← 提示词模板
              │  .defaultAdvisors() ← MessageChatMemoryAdvisor
              │  .tools()          ← OrderTools
              │  .stream().content()← Flux<String>
              └──────────┬──────────┘
                         │
              ┌──────────┴──────────┐
              │   DashScope LLM     │  通义千问
              │   (qwen-plus)       │
              └─────────────────────┘
```

### 0.3 六个文件，六个知识点

| 文件 | 知识点 | 难度 |
|------|--------|------|
| `ChatStatus.java` | 枚举基础 | ⭐ |
| `OrderChat.java` | **结构化输出** | ⭐⭐ |
| `OrderManageService.java` | 业务服务模拟 | ⭐ |
| `OrderTools.java` | **Function Calling** | ⭐⭐⭐ |
| `ChatConfig.java` | **对话记忆 + 提示词加载** | ⭐⭐⭐ |
| `PddRefundController.java` | **流式输出 + 记忆隔离** | ⭐⭐⭐ |
| `PddRefundApplication.java` | Spring Boot 启动 | ⭐ |

### 0.4 推荐的学习顺序

**按依赖关系从上往下写，写完一个编译一次**：

```
Model (ChatStatus → OrderChat)
  → Service (OrderManageService)
    → Tools (OrderTools，依赖 Service)
      → Config (ChatConfig，依赖 ChatMemory)
        → Controller (依赖 ChatClient + OrderTools)
          → Application (启动类)
```

每个步骤写完都跑 `mvn -pl llm-pdd-refund compile` 验证。

### 0.5 项目已有文件（不用你写）

| 文件 | 内容 |
|------|------|
| `pom.xml` | 依赖已配好（Spring Web + DashScope starter） |
| `application.yml` | DashScope 配置，填你的 API Key |
| `prompts/pdd-refund-system.st` | 系统提示词模板 |
| `static/index.html` | 前端聊天页面（CSS + JS 流式接收） |

---

## 1. 第一步：数据模型（2 分钟）

> 知识点：**枚举 + Java Record + @JsonPropertyDescription 结构化输出注解**

### 1.1 ChatStatus.java

打开 `model/ChatStatus.java`，这是一个普通 Java 枚举，不依赖任何框架：

```java
package com.cyril.llm.pdd.model;

public enum ChatStatus {
    CHAT_START,   // 对话刚创建
    CHATTING,     // 对话进行中
    CHAT_END      // 对话已结束
}
```

**为什么用枚举？** 三个状态是固定的、有限的，用枚举比字符串更安全（编译期检查、IDE 自动补全）。

编译验证：
```bash
mvn -pl llm-pdd-refund compile
```

### 1.2 OrderChat.java

打开 `model/OrderChat.java`，这是一个 Java `record`（Java 16+ 特性，比 class 更简洁）：

```java
package com.cyril.llm.pdd.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record OrderChat(
        @JsonPropertyDescription("订单号") String orderId,
        @JsonPropertyDescription("用户Id") String userId,
        @JsonPropertyDescription("对话Id") String chatId,
        @JsonPropertyDescription("对话状态") ChatStatus status
) {}
```

**关键概念：@JsonPropertyDescription**

这个注解来自 Jackson（`com.fasterxml.jackson.annotation`），**不是** Spring AI 的东西。它的作用是：

1. 当你调用 `.call().entity(OrderChat.class)` 时，Spring AI 读取这些描述
2. 自动生成一个 JSON Schema 追加到 prompt 中，告诉 LLM：「请按这个格式输出」
3. LLM 返回的 JSON 自动反序列化为 OrderChat 对象

生成的 JSON Schema 类似：
```json
{
  "type": "object",
  "properties": {
    "orderId": { "type": "string", "description": "订单号" },
    "userId": { "type": "string", "description": "用户Id" },
    "chatId": { "type": "string", "description": "对话Id" },
    "status": { "type": "string", "description": "对话状态" }
  }
}
```

**为什么用 record 而不是 class？**
- record 自动生成构造器、getter、equals、hashCode、toString
- `@JsonPropertyDescription` 可以放在 record 的 compact constructor 参数上
- 适合 DTO（数据载体），不需要 setter

编译验证：
```bash
mvn -pl llm-pdd-refund compile
```

---

## 2. 第二步：模拟退款服务（2 分钟）

> 知识点：**业务层抽象**

### 2.1 OrderManageService.java

打开 `service/OrderManageService.java`：

```java
package com.cyril.llm.pdd.service;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OrderManageService {

    // 模拟查询订单——真实项目中会查数据库
    public String getOrderById(String orderId) {
        return "订单号：" + orderId;
    }

    // 模拟退款——真实项目中会调支付网关
    public String refund(String orderId, String reason) {
        System.out.println("退款成功，订单号: " + orderId + "，原因: " + reason);
        return UUID.randomUUID().toString(); // 返回退款单号
    }
}
```

**设计要点**：
- 这个 Service 是最普通的 Spring Bean，不依赖任何 AI 框架
- 它代表你项目中「已有的业务代码」
- 第四步的 OrderTools 会调用它，这就是 Function Calling 的价值——**让 AI 调用你已有的代码**

编译验证：
```bash
mvn -pl llm-pdd-refund compile
```

---

## 3. 第三步：Function Calling 工具（10 分钟，核心）

> 知识点：**Function Calling —— 让 LLM 调用你的 Java 方法**

### 3.1 什么是 Function Calling？

传统对话：LLM 只能「说话」，不能「做事」。
Function Calling：LLM 可以「决定」调用某个函数，Spring AI 替你执行它，然后把结果交还给 LLM 继续对话。

```
用户: "衣服坏了，我要退款"
  ↓
LLM: 「这属于质量问题，我应该调用 apply_refund 工具」  ← LLM 的决策
  ↓
Spring AI: 执行 OrderTools.refund("20240520001", "衣服", "质量问题")
  ↓
LLM: 「已为您发起退款，退款单号 xxx，请查收」           ← LLM 包装成自然语言
```

### 3.2 OrderTools.java

打开 `tools/OrderTools.java`：

```java
package com.cyril.llm.pdd.tools;

import com.cyril.llm.pdd.service.OrderManageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component  // ⬅ 必须！让 Spring 管理这个 Bean
public class OrderTools {

    // 构造函数注入（推荐方式，不用 @Autowired）
    private final OrderManageService orderManageService;

    public OrderTools(OrderManageService orderManageService) {
        this.orderManageService = orderManageService;
    }

    /*
     * @Tool 注解的三个关键要素：
     *   1. name: LLM 看到的函数名
     *   2. description: LLM 判断「什么时候应该调用这个函数」的依据
     *      写得好坏直接影响 Function Calling 的准确率！
     *   3. 方法签名: 参数和返回值会被 Spring AI 自动转换为 JSON Schema
     */
    @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
    public String refund(
            /*
             * @ToolParam 的 description 极其重要！
             * LLM 靠这个来判断每个参数应该填什么值。
             * 例如 LLM 会从对话记忆中提取 orderId，填入这个参数。
             */
            @ToolParam(description = "订单编号，为数字类型") String orderId,
            @ToolParam(description = "商品名称") String name,
            @ToolParam(description = "退款原因") String reason) {

        System.out.println("已为商品: " + name + "，订单号: " + orderId
                + " 申请退款，退款原因: " + reason);

        // 调用真实业务服务
        orderManageService.refund(orderId, reason);

        // 返回值的文字会被 LLM 接收，LLM 会把它包装成自然语言告诉用户
        return "已为商品：" + name + "，订单号：" + orderId
                + " 申请退款，退款原因: " + reason;
    }
}
```

### 3.3 @Tool 注解深度解析

`@Tool` 不是普通的注解——它来自 `org.springframework.ai.tool.annotation`，Spring AI 会在启动时扫描所有 `@Component` 中的 `@Tool` 方法，自动注册为 LLM 可调用的工具。

**LLM 视角下，它看到的工具定义是这样的**（Spring AI 自动生成的 JSON Schema）：

```json
{
  "type": "function",
  "function": {
    "name": "apply_refund",
    "description": "根据用户传入的订单信息发起退款",
    "parameters": {
      "type": "object",
      "properties": {
        "orderId": {
          "type": "string",
          "description": "订单编号，为数字类型"
        },
        "name": {
          "type": "string",
          "description": "商品名称"
        },
        "reason": {
          "type": "string",
          "description": "退款原因"
        }
      },
      "required": ["orderId", "name", "reason"]
    }
  }
}
```

**这就是 Function Calling 的本质**：把 Java 方法的签名和注解翻译成 LLM 能理解的 JSON Schema，让 LLM 自己决定什么时候调用、传什么参数。

### 3.4 description 为什么这么重要？

对比两个版本：

| 版本 | LLM 表现 |
|------|----------|
| `@Tool(name="f1", description="退款")` | LLM 不知道何时该调用，可能乱调 |
| `@Tool(name="apply_refund", description="根据用户传入的订单信息发起退款")` | LLM 知道这是退款功能，只在用户需要退款时调用 |

**写上 "根据用户传入的……" 这个前缀很关键**——它告诉 LLM 参数应该从对话中提取，而不是胡编乱造。

编译验证：
```bash
mvn -pl llm-pdd-refund compile
```

---

## 4. 第四步：ChatConfig 配置（15 分钟，核心）

> 知识点：**对话记忆（ChatMemory）+ Advisor 链 + 提示词加载**

### 4.1 什么是 Advisor？

Advisor（顾问）是 Spring AI 中的拦截器/中间件概念。

```
用户消息 → [LoggerAdvisor] → [ChatMemoryAdvisor] → LLM → 响应
              ↑                      ↑
           打印日志            注入历史消息到 prompt
```

类比 Spring MVC 的 Interceptor 或 Servlet 的 Filter。

### 4.2 什么是 ChatMemory？

ChatMemory 是一个 key-value 存储：

```
chatId_1 → [message1, message2, message3, ...]
chatId_2 → [message1, message2, ...]
```

每次对话时，MessageChatMemoryAdvisor 从 ChatMemory 中按 `chatId` 取出历史消息，拼接到本次请求的 messages 列表中，LLM 就能「记住」之前说过什么。

### 4.3 ChatConfig.java

打开 `config/ChatConfig.java`：

```java
package com.cyril.llm.pdd.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration  // ⬅ Spring 配置类，必须加
public class ChatConfig {

    /*
     * Bean 1: ChatMemory —— 对话记忆的存储
     *
     * MessageWindowChatMemory 是 Spring AI 1.1.x 提供的内存实现。
     * 特点：
     *   - 用 ConcurrentHashMap 存储，重启即丢失
     *   - 自动限制窗口大小（默认保留最近的消息）
     *   - 适合开发和测试，生产环境换 Redis/JDBC 实现
     *
     * 旧版 API（Spring AI 1.0.x）：new InMemoryChatMemory()
     * 新版 API（Spring AI 1.1.x）：MessageWindowChatMemory.builder().build()
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().build();
    }

    /*
     * Bean 2: ChatClient —— Spring AI 的统一对话入口
     *
     * ChatClient 是 Spring AI 最重要的类，封装了：
     *   - prompt 构建（system message + user message + history）
     *   - LLM 调用（call / stream）
     *   - 工具注册（tools）
     *   - Advisor 链（advisor）
     *   - 结构化输出（entity）
     *
     * 参数说明：
     *   ChatModel chatModel:
     *     LLM 实现，由 application.yml 中的 spring.ai.dashscope 自动配置
     *     不需要手动创建，Spring Boot 自动注入 DashScopeChatModel
     *
     *   ChatMemory chatMemory:
     *     上面定义的 Bean，这里注入给 Advisor 使用
     *
     *   @Value("classpath:prompts/pdd-refund-system.st") Resource systemPrompt:
     *     从 classpath 读取提示词模板文件
     *     .st 后缀表示 StringTemplate 格式，但这里只是纯文本
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
                                  @Value("classpath:prompts/pdd-refund-system.st")
                                  Resource systemPrompt) throws IOException {

        // 读取提示词文件内容为字符串
        String systemText = systemPrompt.getContentAsString(StandardCharsets.UTF_8);

        // 构建 ChatClient
        return ChatClient.builder(chatModel)

                // .defaultSystem(): 设置系统提示词
                //   每次对话都会带上这段 system message，
                //   它定义了 LLM 的角色、行为规则和工具使用指引
                .defaultSystem(systemText)

                // .defaultAdvisors(): 设置默认的 Advisor 链
                //   Advisor 在每次请求前后执行，可以做日志、记忆管理、安全检查等
                .defaultAdvisors(
                        /*
                         * Advisor 1: SimpleLoggerAdvisor
                         * 作用：在控制台打印每次请求的 prompt 和响应
                         * 极其有用！开发调试时你可以看到 LLM 收到了什么、返回了什么
                         *
                         * 日志格式示例：
                         * [SimpleLoggerAdvisor.before] request: {messages: [...], ...}
                         * [SimpleLoggerAdvisor.after]  response: {content: "..."}
                         */
                        new SimpleLoggerAdvisor(),

                        /*
                         * Advisor 2: MessageChatMemoryAdvisor
                         * 作用：在请求前把历史消息注入 prompt，在响应后把新消息存入记忆
                         *
                         * 工作原理（每次 /ask 调用时）：
                         *   before: 从 ChatMemory 中读取 chatId 对应的历史消息
                         *           → 注入到 prompt 的 messages 列表中
                         *           → LLM 看到完整对话历史
                         *   after:  把本次的 user message 和 assistant response
                         *           → 存入 ChatMemory 中 chatId 对应的列表
                         */
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
```

### 4.4 记忆工作原理图解

```
第一次对话 (chatId=abc-123)：

  before:  ChatMemory["abc-123"] → [] (空)
           prompt = [system, user:"我要咨询..."]
  call:    LLM 响应
  after:   ChatMemory["abc-123"] ← [user:"我要咨询...", assistant:"好的..."]

第二次对话 (同一个 chatId)：

  before:  ChatMemory["abc-123"] → [user:"...", assistant:"..."]
           prompt = [system, user:"...", assistant:"...", user:"新消息"]
                                      ↑ 历史消息被注入进来 ↑
  call:    LLM 看到完整历史，能理解上下文
  after:   ChatMemory["abc-123"] ← 追加新消息
```

**对话隔离**：不同 `chatId` 的对话完全隔离，互不影响。

### 4.5 提示词模板加载

注意这行代码：
```java
@Value("classpath:prompts/pdd-refund-system.st") Resource systemPrompt
```

`classpath:` 前缀让 Spring 从 `src/main/resources/` 下加载文件。`.st` 是 StringTemplate 后缀，但你可以在 `prompts/` 目录下打开查看——它目前是纯文本，包含了角色定义、CoT 步骤、Few-Shot 示例和约束规则。

编译验证：
```bash
mvn -pl llm-pdd-refund compile
```

---

## 5. 第五步：Controller 接口（20 分钟，核心）

> 知识点：**结构化输出 + 流式输出 + 记忆隔离 + Function Calling 注册**

### 5.1 PddRefundController.java

这是整个项目的核心——两个接口实现了全部四个知识点。

打开 `controller/PddRefundController.java`：

```java
package com.cyril.llm.pdd.controller;

import com.cyril.llm.pdd.model.ChatStatus;
import com.cyril.llm.pdd.model.OrderChat;
import com.cyril.llm.pdd.tools.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController  // ⬅ 每个方法返回值直接写入 HTTP 响应体
public class PddRefundController {

    /*
     * 两个 advisor param 的 key 值。
     *
     * chat_memory_conversation_id:
     *   告诉 MessageChatMemoryAdvisor 用哪个 key 来隔离对话。
     *   同一个 chatId 共享记忆，不同 chatId 完全隔离。
     *
     * chat_memory_retrieve_size:
     *   每次从记忆库中拉取最近多少条消息。
     *   100 条足够覆盖大部分对话场景。
     *
     * 注意：Spring AI 1.1.x 中这些常量不在 MessageChatMemoryAdvisor 上，
     *   需要直接写字符串。1.0.x 中有 CHAT_MEMORY_CONVERSATION_ID_KEY 等常量。
     */
    private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final String RETRIEVE_SIZE_KEY = "chat_memory_retrieve_size";

    /*
     * 构造函数注入——Spring 推荐的注入方式
     *   chatClient: 由 ChatConfig 中定义的 Bean 自动注入
     *   orderTools: 由 @Component 扫描到的 Bean 自动注入
     */
    private final ChatClient chatClient;
    private final OrderTools orderTools;

    public PddRefundController(ChatClient chatClient, OrderTools orderTools) {
        this.chatClient = chatClient;
        this.orderTools = orderTools;
    }

    // ================================================================
    // 接口 1: /newChat —— 初始化对话 + 结构化输出
    // ================================================================

    /*
     * 知识点：结构化输出（Structured Output）
     *
     * 调用方式：GET /newChat?userId=user_001&orderId=20240520001
     * 返回值：  JSON 格式的 OrderChat 对象
     *
     * 示例返回：
     *   {
     *     "orderId": "20240520001",
     *     "userId": "user_001",
     *     "chatId": "a1b2c3d4-...",
     *     "status": "CHAT_START"
     *   }
     */
    @GetMapping("/newChat")
    public OrderChat newChat(@RequestParam String userId,
                             @RequestParam String orderId) {

        // 1. 生成唯一的对话 ID
        String chatId = UUID.randomUUID().toString();

        // 2. 构建 prompt，把 userId、orderId、chatId 告知 LLM
        //    LLM 会把这些信息存入「记忆」，后续对话中自动引用
        //    注意：这里明确告诉 LLM "当前状态是 CHAT_START"
        return chatClient.prompt()
                .user(String.format(
                        "我要咨询订单相关的售后问题，我的用户id是%s，我的订单号是: %s，"
                                + "本地的对话Id是 %s，当前状态是 %s",
                        userId, orderId, chatId, ChatStatus.CHAT_START.name()))

                // 3. 通过 advisor params 传入 chatId，建立记忆隔离
                .advisors(spec -> spec
                        .param(CONVERSATION_ID_KEY, chatId)
                        .param(RETRIEVE_SIZE_KEY, 100))

                // 4. 非流式调用（call 不是 stream）
                .call()

                // 5. 结构化输出：自动把 LLM 的 JSON 响应映射为 OrderChat 对象
                //    Spring AI 会在 prompt 中追加格式约束，让 LLM 按 OrderChat 的 schema 输出
                .entity(OrderChat.class);
    }

    // ================================================================
    // 接口 2: /ask —— 流式对话 + Function Calling
    // ================================================================

    /*
     * 知识点 1：流式输出（Streaming Output）
     * 知识点 2：Function Calling（工具注册）
     * 知识点 3：对话记忆（Advisor param 传入 chatId）
     *
     * 调用方式：GET /ask?question=衣服坏了怎么办&chatId=a1b2c3d4-...
     * 返回值：  SSE（Server-Sent Events）流式文本
     *
     * 关键注解：
     *   produces = MediaType.TEXT_EVENT_STREAM_VALUE
     *     → 告诉浏览器这是 SSE 事件流，不是普通 HTTP 响应
     *     → 前端可以用 EventSource 或 fetch ReadableStream 逐块读取
     *
     * 返回类型 Flux<String>：
     *   Flux 是 Reactor 的响应式类型，表示「0 到 N 个元素的异步序列」
     *   每个 String 是 LLM 生成的一个 token（或几个 token）
     *   前端逐 token 接收，实现打字机效果
     */
    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@RequestParam String question,
                            @RequestParam String chatId) {

        return chatClient.prompt()
                // 用户问题
                .user(question)

                /*
                 * .tools(orderTools): 注册工具
                 *
                 * 这行代码让 LLM 知道它可以调用 apply_refund 方法。
                 * 当 LLM 判断用户的反馈是质量问题时，
                 * 它会「决定」调用 apply_refund，Spring AI 自动执行。
                 *
                 * 如果要添加更多工具，只需要在这里追加：
                 *   .tools(orderTools, logisticsTools, couponTools)
                 */
                .tools(orderTools)

                /*
                 * .advisors(): 覆盖默认 advisor 的参数
                 *
                 * 这里的 advisors() 不是替换 defaultAdvisors，
                 * 而是给已有的 advisor 传入运行时参数。
                 *
                 * CONVERSATION_ID_KEY = chatId:
                 *   → 告诉 MessageChatMemoryAdvisor 用这个 chatId 读/写记忆
                 *   → 不同 chatId 的对话完全隔离
                 *
                 * RETRIEVE_SIZE_KEY = 100:
                 *   → 每次请求拉取最近 100 条历史消息
                 */
                .advisors(spec -> spec
                        .param(CONVERSATION_ID_KEY, chatId)
                        .param(RETRIEVE_SIZE_KEY, 100))

                /*
                 * .stream().content():
                 *
                 * .stream() → 切换到流式模式，LLM 边生成边返回
                 * .content() → 只取响应中的文本内容（不含 metadata）
                 * 返回类型：Flux<String>
                 */
                .stream()
                .content();
    }
}
```

### 5.2 两个接口的对比

| 特性 | /newChat | /ask |
|------|----------|------|
| 调用方式 | 非流式 `.call()` | 流式 `.stream()` |
| 返回类型 | `OrderChat`（JSON） | `Flux<String>`（SSE） |
| 工具注册 | 无 | `.tools(orderTools)` |
| 结构化输出 | `.entity(OrderChat.class)` | 无（自由文本） |
| 记忆隔离 | 有（传入 chatId） | 有（传入同一 chatId） |
| HTTP 响应 | 一次性返回 JSON | 逐 token 推送 |

### 5.3 为什么 /newChat 不用 stream()？

`/newChat` 返回的是结构化 JSON，只有几十个字符，不需要流式输出。而且 `.entity()` 要求拿到完整响应才能反序列化，和流式不兼容。

`.call()` = 等 LLM 完整生成后一次性返回。
`.stream()` = LLM 边生成边返回，用户看到打字机效果。

编译验证：
```bash
mvn -pl llm-pdd-refund compile
```

---

## 6. 第六步：启动类（1 分钟）

> 知识点：**Spring Boot 自动配置**

### 6.1 PddRefundApplication.java

打开 `PddRefundApplication.java`：

```java
package com.cyril.llm.pdd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PddRefundApplication {

    public static void main(String[] args) {
        SpringApplication.run(PddRefundApplication.class, args);
    }
}
```

**为什么不用显式配置 DashScope？**

`@SpringBootApplication` 包含 `@EnableAutoConfiguration`，Spring AI Alibaba 的 starter 自动配置了：
- `DashScopeChatModel`（ChatModel 的实现类）
- `ChatClient.Builder`（ChatClient 的构建器）
- 所有从 `application.yml` 读取配置的 Bean

你只需要在 `application.yml` 中写好 API Key，其他全部自动搞定。

---

## 7. 运行验证

### 7.1 全量编译

```bash
# 在项目根目录
mvn compile
```

看到 `BUILD SUCCESS` 就是六个文件都写对了。

### 7.2 配置 API Key

编辑 `src/main/resources/application.yml`，把 `your-dashscope-api-key` 替换为你的真实 Key：

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-xxxxxxxxxxxxxxxx  # 改这里
```

获取 Key：https://dashscope.aliyun.com/ （阿里云灵积平台）

### 7.3 启动

```bash
mvn -pl llm-pdd-refund spring-boot:run
```

看到 `Started PddRefundApplication` 就是启动成功了。

### 7.4 打开前端测试

浏览器打开 http://localhost:8084

1. 填写用户 ID（如 `user_001`）和订单号（如 `20240520001`）
2. 点击"开始对话"
3. 看到结构化输出：对话 ID、订单号、状态
4. 输入问题：`我买的衣服袖口开线了，根本没法穿`
5. 观察 AI 流式回复，看它如何共情、确认、并最终调用退款工具

### 7.5 命令行测试

```bash
# 1. 初始化对话
curl "http://localhost:8084/newChat?userId=user_001&orderId=20240520001"
# 返回：{"orderId":"20240520001","userId":"user_001","chatId":"xxx","status":"CHAT_START"}

# 2. 用返回的 chatId 发消息（-N 是 curl 的流式模式）
curl -N "http://localhost:8084/ask?question=我买的衣服质量太差了&chatId=xxx"
```

### 7.6 观察控制台日志

启动后，每次对话都会看到 `SimpleLoggerAdvisor` 输出的日志：

```
[SimpleLoggerAdvisor.before] request: {
  messages: [
    {role: system, content: "你是一名专业的电商平台客户体验专家..."},
    {role: user, content: "我要咨询订单相关的售后问题..."},
    {role: assistant, content: "好的，我了解了..."},
    {role: user, content: "我买的衣服质量太差了"}
  ]
}
```

**注意看 `messages` 的变化**：
- 第一次请求：只有 system + 1 条 user
- 第二次请求：system + 历史消息 + 新的 user
- 这就是对话记忆在发挥作用！

当 LLM 决定调用工具时，控制台会打印：
```
已为商品: 衣服，订单号: 20240520001 申请退款，退款原因: 质量问题
```

---

## 8. 深度理解：每个知识点发生了什么

### 8.1 提示词工程 —— 为什么 CoT 有效？

普通 prompt：
> "你是客服，处理退款"

CoT prompt：
> "第一步：识别问题 → 第二步：确认问题 → 第三步：执行退款 → 第四步：安抚"

**区别**：CoT 把复杂任务分解为多个简单步骤，LLM 每一步只需关注一件事，减少了跳步和遗漏。这和人写 checklist 的道理一样——把大象放进冰箱也要分三步。

### 8.2 对话记忆 —— 实际上 prompt 里有什么？

当你用 `chatId=abc123` 进行第三轮对话时，实际发送给 LLM 的 prompt 长这样：

```json
{
  "messages": [
    {"role": "system", "content": "你是一名专业的电商平台客户体验专家...（长篇提示词）"},
    {"role": "user", "content": "我要咨询订单相关的售后问题，我的用户id是user_001，我的订单号是: 20240520001，本地的对话Id是 abc123，当前状态是 CHAT_START"},
    {"role": "assistant", "content": "好的，我了解了您的订单信息。请问有什么可以帮您的？"},
    {"role": "user", "content": "我买的衣服袖口开线了"},
    {"role": "assistant", "content": "非常抱歉给您带来了不好的体验。您是说刚收到的这件衣服袖口已经完全开线了，对吗？"},
    {"role": "user", "content": "对的，就是开线了"}   ← 当前消息
  ]
}
```

**关键**：LLM 本身是无状态的（每次请求都是独立的），「记忆」是通过把历史消息拼到 prompt 里实现的。这就是 `MessageChatMemoryAdvisor` 的 `before()` 方法做的事。

### 8.3 Function Calling —— LLM 怎么决定调用哪个函数？

LLM 的输出不是「调用函数」，而是「生成一个特殊格式的 JSON」：

```json
{
  "tool_calls": [{
    "id": "call_xxxxx",
    "type": "function",
    "function": {
      "name": "apply_refund",
      "arguments": "{\"orderId\":\"20240520001\",\"name\":\"衣服\",\"reason\":\"质量问题\"}"
    }
  }]
}
```

Spring AI 拦截到这个 JSON → 调用 `OrderTools.refund("20240520001", "衣服", "质量问题")` → 拿到返回值 → 把返回值发回给 LLM → LLM 生成自然语言回复。

**所以 Function Calling 的本质是**：LLM 生成一个结构化的「调用请求」，由框架执行，执行结果再交给 LLM 继续对话。

### 8.4 流式输出 —— SSE 是什么？

普通的 HTTP 请求：客户端发请求 → 服务端处理完 → 一次性返回全部数据。

SSE（Server-Sent Events）：客户端发请求 → 服务端保持连接 → 有数据就推送 → 再推送 → ... → 直到 `done`。

```
HTTP 响应头：
  Content-Type: text/event-stream
  Transfer-Encoding: chunked

响应体：
  data: 非常
  data: 抱歉
  data: 给您
  data: 带来了
  ...
  data: [DONE]
```

前端用 `fetch` 的 `ReadableStream` 逐 chunk 读取，追加到 DOM，就是打字机效果。

`Flux<String>` 是 Reactor 的响应式类型，每个 `String` 自动变成一个 SSE `data:` 事件。

### 8.5 结构化输出 —— .entity() 背后做了什么？

1. Spring AI 扫描 `OrderChat` 的字段和 `@JsonPropertyDescription` 注解
2. 生成 JSON Schema
3. 在 prompt 中追加：`Please respond in the following JSON format: { ...schema... }`
4. LLM 按格式返回 JSON
5. Spring AI 用 Jackson 反序列化为 `OrderChat` 对象
6. Controller 返回给前端

**这就是为什么 @JsonPropertyDescription 这么重要**——它同时影响了 JSON Schema 的生成和 Jackson 的反序列化。

---

## 9. 调试技巧

### 9.1 查看 LLM 收到的完整 prompt

SimpleLoggerAdvisor 会打印，但你也可以手动查看。在 `ChatConfig` 中加一行：

```java
System.out.println("System prompt length: " + systemText.length());
```

### 9.2 查看工具是否正确注册

启动后在日志中搜索 `apply_refund`。如果看到相关日志，说明工具注册成功。

### 9.3 测试 Function Calling 触发

在 `/ask` 中发一个明显的质量问题，观察控制台：

```
# 如果看到这行，说明 Function Calling 成功触发：
已为商品: xxx，订单号: xxx 申请退款，退款原因: xxx
```

### 9.4 增大日志级别

在 `application.yml` 中临时添加：

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

这会打印 Spring AI 内部的工具解析、记忆读写等详细日志。

---

## 10. 常见错误与解决

### 10.1 编译错误：找不到 InMemoryChatMemory

**现象**：`error: cannot find symbol InMemoryChatMemory`

**原因**：Spring AI 1.1.x 把它改名为 `MessageWindowChatMemory`。

**解决**：用 `MessageWindowChatMemory.builder().build()`。

### 10.2 编译错误：找不到 CHAT_MEMORY_CONVERSATION_ID_KEY

**现象**：`error: cannot find symbol CHAT_MEMORY_CONVERSATION_ID_KEY`

**原因**：Spring AI 1.1.x 把常量移走了。

**解决**：直接用字符串 `"chat_memory_conversation_id"`。

### 10.3 启动后访问 /newChat 报错

**现象**：`401 Unauthorized` 或 `Authentication failed`

**原因**：DashScope API Key 没配或配错了。

**解决**：检查 `application.yml` 中的 `spring.ai.dashscope.api-key`。

### 10.4 LLM 不调用退款工具

**现象**：LLM 只是说话安慰，不调用 `apply_refund`。

**原因**：system prompt 中的工具调用指令不够强。

**解决**：
1. 确保 system prompt 最后有 `退款时必须调用 apply_refund 工具`
2. 确保 `/ask` 中调用了 `.tools(orderTools)`
3. 尝试更明确地描述问题，如"质量太差了，根本没法用"

### 10.5 前端流式显示乱码或不分段

**现象**：中文显示为乱码，或所有文字一次性弹出。

**原因**：字符编码问题，或 SSE 没有正确配置。

**解决**：
1. 确保 Controller 方法上有 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
2. 确保 `application.yml` 中有 `server.servlet.encoding.charset: UTF-8`（Spring Boot 默认已配置）
3. 前端检查 `Accept: text/event-stream` header

### 10.6 Bean 注入失败

**现象**：`Field chatClient in ... required a bean of type 'ChatClient' that could not be found`

**原因**：ChatConfig 中的 @Bean 方法没有被执行。

**解决**：
1. 确保 `ChatConfig` 上有 `@Configuration`
2. 确保 `PddRefundApplication` 的包路径能扫描到 `config/` 目录
3. 确保 `@Bean` 返回了正确的类型

---

## 附录 A：完整文件清单

```
llm-pdd-refund/src/main/java/com/cyril/llm/pdd/
├── PddRefundApplication.java      ← 步骤6（最后写）
├── config/
│   └── ChatConfig.java            ← 步骤4
├── controller/
│   └── PddRefundController.java   ← 步骤5（最复杂）
├── model/
│   ├── ChatStatus.java            ← 步骤1.1（最先写，最简单）
│   └── OrderChat.java             ← 步骤1.2
├── service/
│   └── OrderManageService.java    ← 步骤2
└── tools/
    └── OrderTools.java            ← 步骤3
```

## 附录 B：关键技术词汇表

| 术语 | 解释 |
|------|------|
| CoT (Chain of Thought) | 思维链，把复杂任务分解为逐步推理 |
| Few-Shot | 在 prompt 中给出少量示例，引导 LLM 行为 |
| Function Calling | LLM 决定调用外部函数，由框架执行 |
| ChatMemory | 对话记忆的存储接口 |
| Advisor | Spring AI 的拦截器/中间件概念 |
| Flux | Reactor 的响应式流类型 |
| SSE | Server-Sent Events，服务端推送技术 |
| Structured Output | LLM 按固定 JSON Schema 输出 |
| Record | Java 16+ 的不可变数据载体 |
| @JsonPropertyDescription | Jackson 注解，描述 JSON 字段含义 |
