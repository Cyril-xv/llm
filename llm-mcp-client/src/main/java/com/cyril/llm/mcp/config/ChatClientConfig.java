package com.cyril.llm.mcp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    @Autowired
    private ChatModel chatModel;

    @Bean
    public ChatClient chatClient(){
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        return ChatClient.builder(chatModel).defaultToolCallbacks(toolCallbacks).build();
    }
}
