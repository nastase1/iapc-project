package com.edupulse.gateway.ingestion.service;

import com.edupulse.gateway.ingestion.config.IngestionProperties;
import com.edupulse.gateway.ingestion.model.IngestionMetadata;
import com.edupulse.gateway.ingestion.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerServiceTest {

    private DocumentChunkerService chunkerService;

    @BeforeEach
    void setUp() {
        chunkerService = new DocumentChunkerService(new IngestionProperties(50, 10, null));
    }

    @Test
    void chunksDocumentAndPreservesAllMetadataFields() {
        ParsedDocument parsedDoc = new ParsedDocument(
                "doc-123",
                "policy.pdf",
                "chk-abc",
                List.of(
                        new ParsedDocument.ParsedPage(1, "Page 1 content text for compliance rules."),
                        new ParsedDocument.ParsedPage(2, "Page 2 content with secondary rules.")
                )
        );

        IngestionMetadata meta = new IngestionMetadata(
                "doc-123",
                "2.0",
                "policy.pdf",
                "chk-abc",
                true,
                "2026-11-22T12:00:00Z",
                "tenant-alpha",
                "legal",
                "INTERNAL"
        );

        List<Document> chunks = chunkerService.chunk(parsedDoc, meta);

        assertThat(chunks).isNotEmpty();
        int expectedIndex = 0;
        for (Document chunk : chunks) {
            assertThat(chunk.getMetadata()).containsEntry("doc_id", "doc-123");
            assertThat(chunk.getMetadata()).containsEntry("version", "2.0");
            assertThat(chunk.getMetadata()).containsEntry("source", "policy.pdf");
            assertThat(chunk.getMetadata()).containsEntry("checksum", "chk-abc");
            assertThat(chunk.getMetadata()).containsEntry("is_latest", true);
            assertThat(chunk.getMetadata()).containsEntry("valid_from", "2026-11-22T12:00:00Z");
            assertThat(chunk.getMetadata()).containsEntry("tenant_id", "tenant-alpha");
            assertThat(chunk.getMetadata()).containsEntry("org_unit", "legal");
            assertThat(chunk.getMetadata()).containsEntry("access_level", "INTERNAL");
            assertThat(chunk.getMetadata()).containsEntry("chunk_index", expectedIndex);
            assertThat(chunk.getMetadata()).containsKey("page");
            expectedIndex++;
        }
    }

    @Test
    void chunksLongTextWithConfiguredOverlap() {
        String longText = "1234567890".repeat(10); // 100 characters
        ParsedDocument parsedDoc = new ParsedDocument(
                "doc-long",
                "long.txt",
                "chk-long",
                List.of(new ParsedDocument.ParsedPage(1, longText))
        );

        IngestionMetadata meta = new IngestionMetadata(
                "doc-long",
                "1.0",
                "long.txt",
                "chk-long",
                true,
                null,
                "default-org",
                "compliance",
                "PUBLIC"
        );

        List<Document> chunks = chunkerService.chunk(parsedDoc, meta, 30, 5);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        assertThat(chunks.get(0).getId()).isEqualTo("doc-long:1.0:0");
        assertThat(chunks.get(1).getId()).isEqualTo("doc-long:1.0:1");
    }
}
