# PDD Auto Refund 实战开发指南

> 基于 Spring AI Alibaba + DashScope，实现仿 PDD 自动帮买家申请退款的智能客服

---

## 1. 技术栈总览

| 技术 | 说明 |
|------|------|
| **提示词工程** | 角色定义 + Few-Shot + CoT（思维链）|
| **Function Calling** | `@Tool` 注解定义退款工具，大模型自动调用 |
| **Spring AI** | ChatClient 统一入口、流式输出、结构化输出 |
| **对话记忆** | MessageChatMemoryAdvisor + MessageWindowChatMemory |
| **流式输出** | Flux<String> + SSE（Server-Sent Events）|
| **结构化输出** | `call().entity(OrderChat.class)` 自动映射 |
| **前端** | 原生 HTML/CSS/JS，EventSource 接收流式响应 |

---

## 2. 项目结构

```
llm-pdd-refund/
├── pom.xml
└── src/main/
    ├── java/com/cyril/llm/pdd/
    │   ├── PddRefundApplication.java          # 启动类
    │   ├── config/
    │   │   └── ChatConfig.java                # ChatClient + ChatMemory 配置
    │   ├── controller/
    │   │   └── PddRefundController.java       # /newChat 和 /ask 接口
    │   ├── model/
    │   │   ├── OrderChat.java                 # 结构化输出 record
    │   │   └── ChatStatus.java                # 对话状态枚举
    │   ├── service/
    │   │   └── OrderManageService.java        # 模拟订单退款服务
    │   └── tools/
    │       └── OrderTools.java                # Function Call 工具定义
    └── resources/
        ├── application.yml                     # DashScope 配置
        ├── prompts/
        │   └── pdd-refund-system.st           # 系统提示词模板
        └── static/
            └── index.html                      # 前端聊天界面
```

---

## 3. 核心知识点拆解

### 3.1 提示词工程（pdd-refund-system.st）

提示词模板包含三个关键要素：

**① 角色定义**
```
你是一名专业的电商平台客户体验专家...
首要任务是敏锐识别用户对商品质量的严重不满
```

**② Few-Shot（少样本示例）**
```
"根本没法用"、"是坏的"、"有瑕疵"、"质量太差了"
```
这些例子帮模型理解什么是"质量不满"的边界。

**③ CoT 思维链（Chain of Thought）**
```
第一步：主动识别与确认 → 共情 + 封闭式问题确认
第二步：判断与执行退款 → 满足条件则调用 apply_refund 工具
第三步：后续安抚与闭环 → 道歉 + 确定性 + 不索要额外信息
```

CoT 的关键是告诉模型 **每一步该做什么**，而不是笼统地说"处理退款"。

**④ Limit 约束**
```
仅处理质量问题，不喜欢/尺寸不合适/物流慢 → 按常规客诉处理
不索要额外信息 → 默认已有订单信息
```

### 3.2 对话记忆（ChatConfig.java）

```java
@Bean
public ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder().build();  // 1.1.6 API
}
```

通过 `MessageChatMemoryAdvisor` 将记忆注入 ChatClient：

```java
ChatClient.builder(chatModel)
    .defaultSystem(systemText)
    .defaultAdvisors(
        new SimpleLoggerAdvisor(),                         // 日志 Advisor
        MessageChatMemoryAdvisor.builder(chatMemory).build() // 记忆 Advisor
    )
    .build();
```

**对话隔离**：每次 `newChat` 时生成新的 `chatId`，通过 advisor param 指定：

```java
.advisors(spec -> spec
    .param("chat_memory_conversation_id", chatId)   // 用 chatId 隔离对话
    .param("chat_memory_retrieve_size", 100))        // 拉取最近 100 条消息
```

核心原理：`MessageChatMemoryAdvisor` 在每次请求前从 `ChatMemory` 中拉取 `chatId` 对应的历史消息，注入到 prompt 的 messages 列表中，实现"记忆"效果。

### 3.3 结构化输出（OrderChat.java + /newChat）

```java
public record OrderChat(
    @JsonPropertyDescription("订单号") String orderId,
    @JsonPropertyDescription("用户Id") String userId,
    @JsonPropertyDescription("对话Id") String chatId,
    @JsonPropertyDescription("对话状态") ChatStatus status
) {}
```

`@JsonPropertyDescription` 告诉大模型每个字段的含义，让它按这个格式输出。

使用时只需 `.call().entity(OrderChat.class)`：

```java
return chatClient.prompt()
    .user("...")
    .advisors(spec -> spec.param("chat_memory_conversation_id", chatId))
    .call()
    .entity(OrderChat.class);  // 自动将 LLM 输出映射为 OrderChat
```

Spring AI 会在 prompt 中追加格式指令，强制模型按 JSON Schema 输出，然后自动反序列化。

### 3.4 Function Calling（OrderTools.java）

**定义工具**：
```java
@Component
public class OrderTools {
    @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
    public String refund(
        @ToolParam(description = "订单编号，为数字类型") String orderId,
        @ToolParam(description = "商品名称") String name,
        @ToolParam(description = "退款原因") String reason) {
        // 调用真实退款服务
        orderManageService.refund(orderId, reason);
        return "已退款...";
    }
}
```

**在对话中注册工具**：
```java
chatClient.prompt()
    .user(question)
    .tools(orderTools)  // 让模型知道这个工具可用
    ...
```

**工作流程**：
1. 用户说"买的衣服袖口开线了，质量太差了"
2. 模型根据 system prompt 判断这是质量问题 → 决定调用 `apply_refund`
3. Spring AI 自动执行 `OrderTools.refund()`，拿到结果
4. 模型把退款结果包装成自然语言返回给用户

### 3.5 流式输出（/ask）

```java
@GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> ask(@RequestParam String question, @RequestParam String chatId) {
    return chatClient.prompt()
            .user(question)
            .tools(orderTools)
            .advisors(...)
            .stream()       // 流式调用
            .content();     // 返回 Flux<String>
}
```

前端通过 SSE 逐 token 接收，实现打字机效果。

### 3.6 前端流式接收（index.html）

```javascript
const resp = await fetch(url, { headers: { Accept: 'text/event-stream' } });
const reader = resp.body.getReader();
const decoder = new TextDecoder();

while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    // 逐 chunk 追加到聊天泡泡
    aiContent.textContent += decoder.decode(value, { stream: true });
}
```

---

## 4. 交互流程

```
┌─────────────────────────────────────────────────────┐
│ 1. 用户打开页面                                        │
│    → 填写 userId + orderId                            │
│    → 点击"开始对话"                                     │
│    → GET /newChat?userId=xxx&orderId=xxx              │
│    → 返回 OrderChat 结构化数据                          │
│    → 前端展示订单信息，切换为聊天模式                      │
├─────────────────────────────────────────────────────┤
│ 2. 用户发起咨询                                        │
│    → 输入问题，点击发送                                  │
│    → GET /ask?question=xxx&chatId=xxx                 │
│    → 流式接收 SSE 响应，逐字显示                         │
├─────────────────────────────────────────────────────┤
│ 3. 模型判断质量问题                                     │
│    → 共情 + 封闭式问题确认                               │
│    → 用户确认后，模型调用 apply_refund 工具               │
│    → 退款成功，返回确认信息                               │
└─────────────────────────────────────────────────────┘
```

---

## 5. 运行方式

### 5.1 配置 DashScope API Key

```bash
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

或在 `application.yml` 中直接填写。

### 5.2 启动服务

```bash
cd llm-pdd-refund
../mvnw spring-boot:run
```

访问 http://localhost:8084

### 5.3 测试对话

**初始化对话**：
```bash
curl "http://localhost:8084/newChat?userId=user_001&orderId=20240520001"
```

**发送消息（流式）**：
```bash
curl -N "http://localhost:8084/ask?question=我买的衣服袖口开线了&chatId=<上面返回的chatId>"
```

---

## 6. Spring AI 1.1.x 版本差异说明

本文档使用的 Spring AI Alibaba 1.0.0.3 传递依赖了 Spring AI 1.1.6，API 与 1.0.x 有以下差异：

| 1.0.x API | 1.1.x API |
|-----------|-----------|
| `new InMemoryChatMemory()` | `MessageWindowChatMemory.builder().build()` |
| `MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY` | 字符串 `"chat_memory_conversation_id"` |
| `MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY` | 字符串 `"chat_memory_retrieve_size"` |

---

## 7. 常见问题

### 7.1 Function Call 在流式模式下失败

**现象**：模型调用了工具但参数为空，或者有参数但没工具名。

**原因**：流式输出时，function call 的 chunk 是分段的。Spring AI 1.0.x 在某些模型上检测到 `tool_call` 就立即执行，而不是等参数拼接完整。

**解决方案**：
1. 换用 DashScope（本文档方案）— DashScopeChatModel 处理得更好
2. 或在 `application.yml` 中禁用自动工具执行，手动拼接参数后再调用

### 7.2 对话记忆混淆

确保每次 `newChat` 生成唯一的 `chatId`，并且前端一直使用同一个 `chatId` 调用 `/ask`。

### 7.3 模型不调用退款工具

检查 system prompt 中的 `apply_refund` 工具调用指令是否足够明确。system prompt 最后一行 `退款时必须调用 apply_refund 工具` 就是为了强化这一点。

---

## 8. 扩展练习

掌握了基础流程后，可以尝试：

1. **持久化记忆**：把 `MessageWindowChatMemory` 换为 Redis/JDBC 实现
2. **多工具编排**：添加"查询物流"、"申请换货"等更多工具
3. **RAG 增强**：接入商品知识库，让客服能回答商品详情
4. **人工审核**：退款金额超过阈值时，先挂起等待人工审核
5. **结构化日志**：用 `SimpleLoggerAdvisor` 的日志接入 ELK 做分析
