package com.cyril.llm.mcp;

// TODO 步骤1：Spring Boot 启动类（1 分钟）
// 导入：org.springframework.boot.SpringApplication
//       org.springframework.boot.autoconfigure.SpringBootApplication
//
// 一行注释就够了：
//   MCP Server 支持三种模式：Stdio / SSE / Streamable HTTP
//   通过 spring.profiles.active 切换：stdio / sse / streamable
//   见 src/main/resources/application-{profile}.yml
//
// 提示：所有 MCP 相关的配置都在 application.yml 和 profile 文件中，
//       启动类不需要额外代码

// TODO: 写 @SpringBootApplication 启动类

// 启动方式（三种模式）：
//   Stdio:         java -jar llm-mcp-server.jar --spring.profiles.active=stdio
//   SSE:           java -jar llm-mcp-server.jar --spring.profiles.active=sse
//   Streamable:    java -jar llm-mcp-server.jar --spring.profiles.active=streamable
