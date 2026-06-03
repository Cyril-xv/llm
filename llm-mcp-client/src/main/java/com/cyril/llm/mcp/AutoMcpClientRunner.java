package com.cyril.llm.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class AutoMcpClientRunner implements CommandLineRunner {

    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Override
    public void run(String... args) throws Exception {
        for (McpSyncClient client : mcpSyncClients){
            log.info("每个 client 的 getClientInfo() : {}",client.getClientInfo());
            log.info("每个 client 的 getServerInfo() : {}",client.getServerInfo());
            log.info("工具列表：{}",client.listTools());
            Map<String, Object> map = new HashMap<>();
            map.put("city","北京");
            McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder().name("getWeather").arguments(map).build();
            McpSchema.CallToolResult callToolResult = client.callTool(request);
            System.out.println("调用结果: "+ callToolResult);
        }
    }
}
