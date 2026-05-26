package com.cyril.llm.pdd.controller;

// TODO 步骤5：实现两个 REST 接口（★★★ 核心知识点 ★★★）
// 需要导入：
//   com.cyril.llm.pdd.model.ChatStatus, com.cyril.llm.pdd.model.OrderChat
//   com.cyril.llm.pdd.tools.OrderTools
//   org.springframework.ai.chat.client.ChatClient
//   org.springframework.http.MediaType
//   org.springframework.web.bind.annotation.*
//   reactor.core.publisher.Flux
//   java.util.UUID
//
// 注入依赖（构造函数注入）：
//   private final ChatClient chatClient;     // ChatConfig 中定义的 Bean
//   private final OrderTools orderTools;     // Function Call 工具
//
// 两个 advisory param 的 key：
//   private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";
//   private static final String RETRIEVE_SIZE_KEY = "chat_memory_retrieve_size";

@RestController
public class PddRefundController {

    // ┌──────────────────────────────────────────────────────────┐
    // │ 接口1: GET /newChat?userId=xxx&orderId=xxx               │
    // │                                                          │
    // │ 知识点：结构化输出 (Structured Output)                     │
    // │                                                          │
    // │ 流程：                                                    │
    // │ 1. 用 UUID.randomUUID() 生成 chatId                      │
    // │ 2. 组装 user message，把 userId、orderId、chatId 告诉 LLM │
    // │    格式："我要咨询订单相关的售后问题，我的用户id是%s..."    │
    // │ 3. 通过 advisor param 传入 chatId，建立记忆隔离           │
    // │ 4. .call().entity(OrderChat.class) 让 LLM 返回结构化 JSON │
    // │                                                          │
    // │ @GetMapping("/newChat")                                  │
    // │ public OrderChat newChat(                                │
    // │     @RequestParam String userId,                         │
    // │     @RequestParam String orderId) { ... }                │
    // └──────────────────────────────────────────────────────────┘

    // ┌──────────────────────────────────────────────────────────┐
    // │ 接口2: GET /ask?question=xxx&chatId=xxx                  │
    // │                                                          │
    // │ 知识点：流式输出 + Function Calling + 对话记忆             │
    // │                                                          │
    // │ 流程：                                                    │
    // │ 1. chatClient.prompt().user(question)                    │
    // │ 2. .tools(orderTools) ← 注册工具，LLM 可以调用            │
    // │ 3. .advisors(spec -> spec                               │
    // │       .param(CONVERSATION_ID_KEY, chatId)  ← 记忆隔离    │
    // │       .param(RETRIEVE_SIZE_KEY, 100))      ← 拉取最近消息 │
    // │ 4. .stream().content() ← 流式返回 Flux<String>           │
    // │                                                          │
    // │ 注意：produces = MediaType.TEXT_EVENT_STREAM_VALUE       │
    // │   这是 SSE（Server-Sent Events），前端才能流式读取         │
    // │                                                          │
    // │ @GetMapping(value = "/ask",                              │
    // │     produces = MediaType.TEXT_EVENT_STREAM_VALUE)        │
    // │ public Flux<String> ask(                                 │
    // │     @RequestParam String question,                       │
    // │     @RequestParam String chatId) { ... }                 │
    // └──────────────────────────────────────────────────────────┘
}
