package com.cyril.llm.springai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentCleaningServiceTest {

    private final DocumentCleaningService service = new DocumentCleaningService();

    @Test
    void nullInputReturnsEmpty() {
        assertThat(service.clean(null)).isEmpty();
    }

    @Test
    void emptyListReturnsEmpty() {
        assertThat(service.clean(List.of())).isEmpty();
    }

    @Test
    void nullDocumentIsSkipped() {
        Document valid = new Document("hello");
        List<Document> input = new ArrayList<>();
        input.add(null);
        input.add(valid);
        List<Document> result = service.clean(input);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getText()).isEqualTo("hello");
    }

    @Test
    void emptyTextStaysEmpty() {
        // 当前清洗实现不会过滤空 Document，只会把空 Document 的文本清洗后仍为空
        Document empty = new Document("");
        List<Document> result = service.clean(List.of(empty));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getText()).isEmpty();
    }

    @Test
    void consecutiveWhitespaceIsCollapsed() {
        List<Document> result = service.clean(List.of(
                new Document("hello    world")
        ));
        assertThat(result.getFirst().getText()).isEqualTo("hello world");
    }

    @Test
    void emptyLinesAreRemoved() {
        Document doc = new Document("line1\n\n \nline2");
        List<Document> result = service.clean(List.of(doc));
        assertThat(result.getFirst().getText()).isEqualTo("line1\nline2");
    }

    @Test
    void duplicateLinesAreDeduplicatedKeepingFirstOccurrenceOrder() {
        Document doc = new Document("a\nb\na\nc\nb");
        List<Document> result = service.clean(List.of(doc));
        assertThat(result.getFirst().getText()).isEqualTo("a\nb\nc");
    }

    @Test
    void specialControlCharactersAreRemoved() {
        Document doc = new Document("hello\0world");
        List<Document> result = service.clean(List.of(doc));
        String text = result.getFirst().getText();
        // should not contain raw control chars
        assertThat(text).doesNotContain("\0").doesNotContain("");
        assertThat(text.replace(" ", "")).isEqualTo("helloworld");
    }

    @Test
    void originalMetadataIsPreserved() {
        Map<String, Object> meta = Map.of("filename", "test.txt");
        Document doc = new Document("content", meta);
        List<Document> result = service.clean(List.of(doc));
        assertThat(result.getFirst().getMetadata()).containsEntry("filename", "test.txt");
    }
}
