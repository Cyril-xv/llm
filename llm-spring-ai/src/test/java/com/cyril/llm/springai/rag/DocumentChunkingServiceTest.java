package com.cyril.llm.springai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

class DocumentChunkingServiceTest {

    private final DocumentChunkingService service = new DocumentChunkingService();

    @Test
    void nullInputReturnsEmpty() {
        assertThat(service.split(null, 100)).isEmpty();
    }

    @Test
    void emptyListReturnsEmpty() {
        assertThat(service.split(List.of(), 100)).isEmpty();
    }

    @Test
    void zeroChunkSizeThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.split(List.of(new Document("x")), 0));
    }

    @Test
    void negativeChunkSizeThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.split(List.of(new Document("x")), -1));
    }

    @Test
    void emptyTextProducesNoChunks() {
        Document empty = new Document("");
        List<Document> result = service.split(List.of(empty), 100);
        // empty text should not produce a chunk
        assertThat(result).allMatch(d -> d.getText() != null && !d.getText().isEmpty());
    }

    @Test
    void longTextWithSmallBudgetProducesMultipleChunks() {
        String text = "Spring AI is a framework for building AI applications. "
                + "It provides an abstraction layer on top of different AI providers. "
                + "The TokenTextSplitter uses a local tokenizer to split documents into chunks. "
                + "Each chunk respects a token budget that you configure via the builder API. "
                + "This makes it easy to prepare documents for embedding and retrieval. "
                + "The splitter can also handle punctuation marks when deciding boundaries.";
        Document doc = new Document(text);
        List<Document> result = service.split(List.of(doc), 50);
        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void largerChunkSizeProducesFewerOrEqualChunks() {
        String text = "A ".repeat(500); // lots of tokens for a single "A" token
        Document doc = new Document(text);
        int countLarge = service.split(List.of(doc), 200).size();
        int countSmall = service.split(List.of(doc), 50).size();
        assertThat(countSmall).isGreaterThanOrEqualTo(countLarge);
    }

    @Test
    void eachChunkTextIsNonEmpty() {
        Document doc = new Document("hello world this is a test document for chunking");
        List<Document> result = service.split(List.of(doc), 10);
        for (Document chunk : result) {
            assertThat(chunk.getText()).isNotNull();
            assertThat(chunk.getText().trim()).isNotEmpty();
        }
    }

    @Test
    void originalMetadataIsCopied() {
        Map<String, Object> meta = Map.of("author", "cyril");
        Document doc = new Document("hello world test content", meta);
        List<Document> result = service.split(List.of(doc), 10);
        for (Document chunk : result) {
            assertThat(chunk.getMetadata()).containsEntry("author", "cyril");
        }
    }

    @Test
    void splitterMetadataIsPresent() {
        Document doc = new Document("some content for testing the splitter metadata injection");
        List<Document> result = service.split(List.of(doc), 10);
        assertThat(result).isNotEmpty();
        for (Document chunk : result) {
            Map<String, Object> meta = chunk.getMetadata();
            assertThat(meta).containsKeys("parent_document_id", "chunk_index", "total_chunks");
        }
    }

    @Test
    void chunkIndicesAreConsecutiveWithinParent() {
        Document doc = new Document("A ".repeat(200));
        List<Document> result = service.split(List.of(doc), 50);
        assertThat(result.size()).isGreaterThanOrEqualTo(2);
        for (int i = 0; i < result.size(); i++) {
            int idx = (int) result.get(i).getMetadata().get("chunk_index");
            assertThat(idx).isEqualTo(i);
        }
    }

    @Test
    void totalChunksIsConsistentWithinParent() {
        Document doc = new Document("A ".repeat(200));
        List<Document> result = service.split(List.of(doc), 50);
        assertThat(result).isNotEmpty();
        int expected = result.size();
        for (Document chunk : result) {
            assertThat((int) chunk.getMetadata().get("total_chunks")).isEqualTo(expected);
        }
    }

    @Test
    void multipleSourceDocumentsKeepOrder() {
        Document doc1 = new Document("AAAA ".repeat(100));
        Document doc2 = new Document("BBBB ".repeat(100));
        List<Document> result = service.split(List.of(doc1, doc2), 80);
        assertThat(result.size()).isGreaterThanOrEqualTo(4);

        // All doc1 chunks (containing "AAAA") must appear before all doc2 chunks (containing "BBBB")
        boolean seenB = false;
        for (Document chunk : result) {
            String text = chunk.getText();
            if (text.contains("BBBB")) {
                seenB = true;
            } else {
                assertThat(seenB).as("chunks should not interleave: found AAAA after BBBB in text: " + text).isFalse();
            }
        }
        assertThat(seenB).isTrue();
    }

    @Test
    void inputDocumentsAreNotModified() {
        String origText = "original content A ".repeat(50);
        Map<String, Object> origMeta = Map.of("key", "val");
        Document doc = new Document(origText, origMeta);
        String origId = doc.getId();

        service.split(List.of(doc), 30);

        assertThat(doc.getId()).isEqualTo(origId);
        assertThat(doc.getText()).isEqualTo(origText);
        assertThat(doc.getMetadata()).containsEntry("key", "val");
    }
}
