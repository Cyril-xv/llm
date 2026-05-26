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

@RestController
public class PddRefundController {

    // MessageChatMemoryAdvisor 通过这两个 key 从 advisor params 中取值
    private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final String RETRIEVE_SIZE_KEY = "chat_memory_retrieve_size";

    private final ChatClient chatClient;
    private final OrderTools orderTools;

    public PddRefundController(ChatClient chatClient, OrderTools orderTools) {
        this.chatClient = chatClient;
        this.orderTools = orderTools;
    }

    /*
     * 接口1: 初始化对话 + 结构化输出
     *
     * 知识点：
     *   1. 对话记忆隔离 — 每次 newChat 生成唯一 chatId
     *   2. 结构化输出 — .entity(OrderChat.class) 让 LLM 按固定 JSON 格式返回
     */
    @GetMapping("/newChat")
    public OrderChat newChat(@RequestParam String userId,
                             @RequestParam String orderId) {
        String chatId = UUID.randomUUID().toString();

        return chatClient.prompt()
                .user(String.format(
                        "我要咨询订单相关的售后问题，我的用户id是%s，我的订单号是: %s，"
                                + "本地的对话Id是 %s，当前状态是 %s",
                        userId, orderId, chatId, ChatStatus.CHAT_START.name()))
                .advisors(spec -> spec
                        .param(CONVERSATION_ID_KEY, chatId)
                        .param(RETRIEVE_SIZE_KEY, 100))
                .call()
                .entity(OrderChat.class);
    }

    /*
     * 接口2: 流式对话 + Function Calling + 对话记忆
     *
     * 知识点：
     *   1. 流式输出 — .stream().content() 返回 Flux<String>
     *   2. Function Calling — .tools(orderTools) 注册工具让 LLM 调用
     *   3. 对话记忆 — advisor param 传入同一个 chatId
     */
    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@RequestParam String question,
                            @RequestParam String chatId) {
        return chatClient.prompt()
                .user(question)
                .tools(orderTools)
                .advisors(spec -> spec
                        .param(CONVERSATION_ID_KEY, chatId)
                        .param(RETRIEVE_SIZE_KEY, 100))
                .stream()
                .content();
    }
}
