package com.cyril.llm.pdd.config;

// TODO 步骤4：配置 ChatClient 和 ChatMemory（★★★ 核心知识点 ★★★）
// 需要导入：
//   org.springframework.ai.chat.client.ChatClient
//   org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
//   org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
//   org.springframework.ai.chat.memory.ChatMemory
//   org.springframework.ai.chat.memory.MessageWindowChatMemory
//   org.springframework.ai.chat.model.ChatModel
//   org.springframework.beans.factory.annotation.Value
//   org.springframework.context.annotation.Bean
//   org.springframework.context.annotation.Configuration
//   org.springframework.core.io.Resource
//   java.io.IOException, java.nio.charset.StandardCharsets
//
// 这个配置类做了两件关键的事：

// ┌── Bean 1: ChatMemory ──────────────────────────────┐
// │ 对话记忆的核心接口。                                  │
// │                                                     │
// │ Spring AI 1.1.x 中：                                │
// │   用 MessageWindowChatMemory.builder().build()      │
// │   创建内存实现（重启丢失，生产环境用 Redis/JDBC）      │
// │                                                     │
// │ 原理：每个 chatId 对应一个消息列表，                    │
// │   MessageChatMemoryAdvisor 负责读写这个列表            │
// └─────────────────────────────────────────────────────┘

// ┌── Bean 2: ChatClient ──────────────────────────────┐
// │ Spring AI 的统一对话入口。                            │
// │                                                     │
// │ 配置项：                                             │
// │   1. ChatModel — LLM 实现（DashScope 自动注入）      │
// │   2. defaultSystem — 系统提示词，从 prompts/xxx.st   │
// │      用 @Value("classpath:...") Resource 加载        │
// │   3. defaultAdvisors — 拦截器链：                    │
// │      ├── SimpleLoggerAdvisor()  打印请求/响应日志     │
// │      └── MessageChatMemoryAdvisor  注入历史消息       │
// │                                                     │
// │ 读取 .st 文件的内容：                                 │
// │   String systemText = systemPrompt                  │
// │       .getContentAsString(StandardCharsets.UTF_8);  │
// └─────────────────────────────────────────────────────┘

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

@Configuration
public class ChatConfig {

    // TODO 4-1: 创建 ChatMemory Bean
    // 方法签名: public ChatMemory chatMemory()
    // 返回: MessageWindowChatMemory.builder().build()
    // 注解: @Bean
    @Bean
    public ChatMemory chatMemory(){
        return MessageWindowChatMemory.builder().build();
    }

    // TODO 4-2: 创建 ChatClient Bean
    // 方法签名: public ChatClient chatClient(
    //              ChatModel chatModel,           // 自动注入
    //              ChatMemory chatMemory,         // 注入上面的 Bean
    //              @Value("classpath:prompts/pdd-refund-system.st")
    //              Resource systemPrompt          // 从 classpath 加载
    //          ) throws IOException
    // 实现步骤：
    //   1. String systemText = systemPrompt.getContentAsString(StandardCharsets.UTF_8)
    //   2. return ChatClient.builder(chatModel)
    //          .defaultSystem(systemText)                    // 设置系统提示词
    //          .defaultAdvisors(                              // 注册 Advisor 链
    //              new SimpleLoggerAdvisor(),                 //   日志
    //              MessageChatMemoryAdvisor                   //   记忆
    //                  .builder(chatMemory).build()
    //          )
    //          .build();
    @Bean
    public ChatClient chatClient(
            ChatModel chatModel,
            ChatMemory chatMemory,
            @Value("classpath:prompts/pdd-refund-system.st")
            Resource systemPrompt
    ) throws IOException {
        systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(), //日志
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                ).build();
    }
}
