package com.cyril.llm.mcp;

import com.cyril.llm.mcp.client.ManualStdioClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


public class ManualStdioClientRunner implements CommandLineRunner {

    private final ManualStdioClient manualStdioClient;

    public ManualStdioClientRunner(ManualStdioClient manualStdioClient) {
        this.manualStdioClient = manualStdioClient;
    }

    @Override
    public void run(String... args) throws Exception {
        manualStdioClient.callWeatherTool();
    }
}
