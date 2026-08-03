package com.cyril.llm.springai.controller;

import com.cyril.llm.springai.RagExceptionHandler;
import com.cyril.llm.springai.rag.DocumentChunkingService;
import com.cyril.llm.springai.rag.DocumentCleaningService;
import com.cyril.llm.springai.rag.DocumentReaderStrategySelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagControllerTest {

    private MockMvc mvc;
    private DocumentReaderStrategySelector selector;
    private DocumentCleaningService cleaningService;
    private DocumentChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        selector = Mockito.mock(DocumentReaderStrategySelector.class);
        cleaningService = new DocumentCleaningService();
        chunkingService = new DocumentChunkingService();

        RagController controller = new RagController(selector, cleaningService, chunkingService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RagExceptionHandler())
                .build();
    }

    // ---- /rag/read ----

    @Test
    void readDelegatesToSelectorAndCleaning(@TempDir Path tmpDir) throws Exception {
        Path file = tmpDir.resolve("test.txt");
        Files.writeString(file, "line1\n\nline1"); // dup line + empty line

        when(selector.read(any())).thenReturn(List.of(
                new Document(Files.readString(file))
        ));

        mvc.perform(get("/rag/read").param("path", file.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("line1");
                    assertThat(body).doesNotContain("\n\n");
                });

        Mockito.verify(selector).read(any());
    }

    @Test
    void readNonExistentPathReturns400() throws Exception {
        mvc.perform(get("/rag/read").param("path", "/nonexistent/file.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void readIOExceptionReturns500(@TempDir Path tmpDir) throws Exception {
        Path file = tmpDir.resolve("bad.txt");
        Files.writeString(file, "x");

        when(selector.read(any())).thenThrow(new IOException("boom"));

        mvc.perform(get("/rag/read").param("path", file.toString()))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error").value("文档读取失败: " + file));
    }

    // ---- /rag/split ----

    @Test
    void splitReturnsStatsAndChunks(@TempDir Path tmpDir) throws Exception {
        Path file = tmpDir.resolve("split-test.txt");
        // repeat enough content to produce multiple chunks at small chunkSize
        String text = "Spring AI provides a TokenTextSplitter for document chunking. "
                + "This splitter uses a local tokenizer to estimate token counts. "
                + "The chunkSize parameter controls the token budget per chunk. "
                + "Metadata like parent_document_id and chunk_index are added automatically. "
                + "Chunking is a pure local operation without any model or database calls.";
        Files.writeString(file, text);

        when(selector.read(any())).thenReturn(List.of(new Document(text)));

        mvc.perform(get("/rag/split")
                        .param("path", file.toString())
                        .param("chunkSize", "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.splitter").value("TokenTextSplitter"))
                .andExpect(jsonPath("$.requestedChunkSizeTokens").value(20))
                .andExpect(jsonPath("$.sourceDocumentCount").value(1))
                .andExpect(jsonPath("$.chunkCount").isNumber())
                .andExpect(jsonPath("$.chunks").isArray())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("\"order\"");
                    assertThat(body).contains("\"characterCount\"");
                    assertThat(body).contains("\"metadata\"");
                    assertThat(body).contains("\"text\"");
                });
    }

    @Test
    void splitWithEmptyContentReturnsZeroChunks(@TempDir Path tmpDir) throws Exception {
        Path file = tmpDir.resolve("empty.txt");
        Files.writeString(file, "");

        when(selector.read(any())).thenReturn(List.of(new Document("")));

        mvc.perform(get("/rag/split")
                        .param("path", file.toString())
                        .param("chunkSize", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks").isArray())
                .andExpect(jsonPath("$.chunks").isEmpty());
    }

    @Test
    void splitNonExistentPathReturns400() throws Exception {
        mvc.perform(get("/rag/split")
                        .param("path", "/nonexistent/file.txt")
                        .param("chunkSize", "80"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
