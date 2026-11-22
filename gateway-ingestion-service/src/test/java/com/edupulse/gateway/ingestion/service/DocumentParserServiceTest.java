package com.edupulse.gateway.ingestion.service;

import com.edupulse.gateway.ingestion.model.ParsedDocument;
import com.edupulse.gateway.ingestion.security.ContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserServiceTest {

    private DocumentParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new DocumentParserService(new ContentSanitizer());
    }

    @Test
    void parsesPlainTextDocument() {
        String content = "Articolul 1. Datele cu caracter personal trebuie prelucrate în mod legal și corect.";
        ParsedDocument doc = parserService.parse("regulations.txt", content.getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(doc.source()).isEqualTo("regulations.txt");
        assertThat(doc.checksum()).isNotEmpty();
        assertThat(doc.docId()).startsWith("regulations.txt-");
        assertThat(doc.pages()).hasSize(1);
        assertThat(doc.pages().getFirst().text()).contains("Articolul 1");
    }

    @Test
    void parsesHtmlDocumentAndStripsDangerousTags() {
        String html = "<html><body><h1>Norme GDPR</h1><script>alert('xss')</script><p>Text curat de conformitate.</p></body></html>";
        ParsedDocument doc = parserService.parse("gdpr.html", html.getBytes(StandardCharsets.UTF_8), "text/html");

        assertThat(doc.pages()).hasSize(1);
        String text = doc.pages().getFirst().text();
        assertThat(text).contains("Norme GDPR");
        assertThat(text).contains("Text curat de conformitate");
        assertThat(text).doesNotContain("alert");
        assertThat(text).doesNotContain("<script>");
    }

    @Test
    void computesDeterministicChecksum() {
        byte[] data1 = "Same text content".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "Same text content".getBytes(StandardCharsets.UTF_8);
        byte[] data3 = "Different text content".getBytes(StandardCharsets.UTF_8);

        String checksum1 = DocumentParserService.computeSha256(data1);
        String checksum2 = DocumentParserService.computeSha256(data2);
        String checksum3 = DocumentParserService.computeSha256(data3);

        assertThat(checksum1).isEqualTo(checksum2);
        assertThat(checksum1).isNotEqualTo(checksum3);
    }
}
