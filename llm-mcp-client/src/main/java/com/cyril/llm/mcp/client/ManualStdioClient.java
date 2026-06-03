package com.cyril.llm.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class ManualStdioClient {

    public void callWeatherTool() throws Exception {
        ServerParameters parameters = ServerParameters.builder(System.getProperty("java.home") + "/bin/java")
                .args(
                        "-jar",
                        "/Users/yangxu/ideaProjects/llm/llm-mcp-server/target/llm-mcp-server-0.0.1-SNAPSHOT.jar",
                        "--spring.profiles.active=stdio"
                )
                .build();

        // 构建stdioClientTransport
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
        transport.setStdErrorHandler(line -> log.error("MCP Server STDERR: {}", line));

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("my-client", "1.0")) // 和 MCP 握手
                .requestTimeout(Duration.ofSeconds(10))
                .build();

       log.info("初始化: {}",client.initialize());
       log.info("查询工具列表: {}",client.listTools());

        McpSchema.CallToolRequest build = McpSchema.CallToolRequest.builder()
                .name("getWeather")
                .arguments(Map.of("city", "北京"))
                .build();

        McpSchema.CallToolResult callToolResult = client.callTool(build);

        System.out.println("天气 ：" + callToolResult);

        client.closeGracefully();



    }
}
