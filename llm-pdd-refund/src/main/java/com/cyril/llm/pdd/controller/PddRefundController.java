package com.cyril.llm.pdd.controller;

import com.cyril.llm.pdd.model.ChatStatus;
import com.cyril.llm.pdd.model.OrderChat;
import com.cyril.llm.pdd.tools.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
public class PddRefundController {

    private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final String RETRIEVE_SIZE_KEY = "chat_memory_retrieve_size";

    private final ChatClient chatClient;
    private final OrderTools orderTools;

    public PddRefundController(ChatClient chatClient, OrderTools orderTools) {
        this.chatClient = chatClient;
        this.orderTools = orderTools;
    }

    /**
     * 初始化新对话，返回结构化数据给前端
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

    /**
     * 流式对话接口
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
