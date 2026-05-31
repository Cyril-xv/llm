package com.cyril.llm.mcp.config;

// TODO 步骤4：将 MCP 工具注册到 ToolCallbackProvider（★★★ 核心知识点 ★★★）
// 导入：
//   com.cyril.llm.mcp.service.WeatherService
//   org.springframework.ai.tool.ToolCallbackProvider
//   org.springframework.ai.tool.method.MethodToolCallbackProvider
//   org.springframework.context.annotation.Bean
//   org.springframework.context.annotation.Configuration
//
// ═══════════════════════════════════════════════════════
// 知识点：ToolCallbackProvider 是 MCP Server 的工具注册桥梁
// ═══════════════════════════════════════════════════════
//
// Spring AI MCP Server 启动时，会扫描所有 ToolCallbackProvider Bean，
// 把里面的工具注册到 MCP 协议中，这样客户端（如 Cline）才能发现和调用。
//
// MethodToolCallbackProvider 会自动扫描传入的对象中所有 @Tool 方法，
// 不需要手动一个个注册。
//
// @Bean
// public ToolCallbackProvider weatherTools(WeatherService weatherService) {
//     return MethodToolCallbackProvider.builder()
//             .toolObjects(weatherService)
//             .build();
// }
//
// 对比 PDD 模块：
//   在 PDD 中，工具通过 .tools(orderTools) 手动注册到 ChatClient
//   在 MCP 中，工具通过 ToolCallbackProvider Bean 自动注册到 MCP 协议
//   前者是"按需注册到某个 ChatClient"，后者是"注册到全局 MCP 服务"

// TODO: 写 @Configuration 类 + @Bean 方法

import com.cyril.llm.mcp.service.WeatherService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService){
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }
}
