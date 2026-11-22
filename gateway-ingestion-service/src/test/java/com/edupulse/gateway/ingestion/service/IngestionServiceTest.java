package com.edupulse.gateway.ingestion.service;

import com.edupulse.gateway.ingestion.config.IngestionProperties;
import com.edupulse.gateway.ingestion.model.IngestionResult;
import com.edupulse.gateway.ingestion.security.ContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private VectorStore vectorStore;
    private JdbcTemplate jdbcTemplate;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        ContentSanitizer sanitizer = new ContentSanitizer();
        DocumentParserService parserService = new DocumentParserService(sanitizer);
        DocumentChunkerService chunkerService = new DocumentChunkerService(new IngestionProperties(200, 20, 1024L * 1024));

        ingestionService = new IngestionService(parserService, chunkerService, vectorStore, jdbcTemplate,
                new IngestionProperties(200, 20, 1024L * 1024));
    }

    @Test
    void ingestsNewDocumentSuccessfully() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        byte[] content = "Regulament intern: Prelucrarea datelor este strict reglementata.".getBytes(StandardCharsets.UTF_8);
        IngestionResult result = ingestionService.ingest(
                "policy.txt",
                content,
                "text/plain",
                "policy-001",
                "1.0",
                "tenant-1",
                "legal",
                "PUBLIC"
        );

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.docId()).isEqualTo("policy-001");
        assertThat(result.totalChunks()).isGreaterThan(0);

        verify(jdbcTemplate).update(anyString(), eq("policy-001"), eq("1.0"), eq("tenant-1"));
        verify(vectorStore).accept(anyList());
    }

    @Test
    void skipsIngestionWhenChecksumIsIdentical() {
        byte[] content = "Continut identic".getBytes(StandardCharsets.UTF_8);
        String checksum = DocumentParserService.computeSha256(content);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of(checksum));

        IngestionResult result = ingestionService.ingest(
                "policy.txt",
                content,
                "text/plain",
                "policy-001",
                "1.0",
                "tenant-1",
                "legal",
                "PUBLIC"
        );

        assertThat(result.status()).isEqualTo("ALREADY_UP_TO_DATE");
        assertThat(result.totalChunks()).isEqualTo(0);
        verify(vectorStore, never()).accept(anyList());
    }
}
