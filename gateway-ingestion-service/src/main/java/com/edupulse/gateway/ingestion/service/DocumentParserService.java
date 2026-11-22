package com.edupulse.gateway.ingestion.service;

import com.edupulse.gateway.ingestion.model.ParsedDocument;
import com.edupulse.gateway.ingestion.model.ParsedDocument.ParsedPage;
import com.edupulse.gateway.ingestion.security.ContentSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    private final ContentSanitizer contentSanitizer;

    public DocumentParserService(ContentSanitizer contentSanitizer) {
        this.contentSanitizer = contentSanitizer;
    }

    public ParsedDocument parse(String filenameOrUrl, byte[] content, String contentType) {
        String checksum = computeSha256(content);
        String docId = generateDocId(filenameOrUrl, checksum);
        String normalizedType = normalizeContentType(filenameOrUrl, contentType);

        List<ParsedPage> pages;
        try {
            if ("application/pdf".equals(normalizedType)) {
                pages = parsePdf(content);
            } else if ("text/html".equals(normalizedType)) {
                pages = parseHtml(content);
            } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(normalizedType)
                    || "application/msword".equals(normalizedType)) {
                pages = parseTika(content);
            } else {
                pages = parsePlainText(content);
            }
        } catch (Exception e) {
            log.warn("Failed primary parser for '{}' ({}), attempting generic text fallback: {}",
                    filenameOrUrl, normalizedType, e.getMessage());
            pages = parsePlainText(content);
        }

        return new ParsedDocument(docId, filenameOrUrl, checksum, pages);
    }

    private List<ParsedPage> parsePdf(byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content);
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> docs = reader.read();

        List<ParsedPage> pages = new ArrayList<>();
        int pageNum = 1;
        for (Document doc : docs) {
            Object pageMeta = doc.getMetadata().get("page_number");
            int p = (pageMeta instanceof Number n) ? n.intValue() : pageNum;
            String text = doc.getText();
            if (text != null && !text.isBlank()) {
                pages.add(new ParsedPage(p, text.trim()));
            }
            pageNum++;
        }
        if (pages.isEmpty()) {
            pages.add(new ParsedPage(1, ""));
        }
        return pages;
    }

    private List<ParsedPage> parseHtml(byte[] content) {
        String rawHtml = new String(content, StandardCharsets.UTF_8);
        String cleanText = contentSanitizer.sanitizeToText(rawHtml);
        return List.of(new ParsedPage(1, cleanText));
    }

    private List<ParsedPage> parseTika(byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content);
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> docs = reader.read();
        List<ParsedPage> pages = new ArrayList<>();
        int idx = 1;
        for (Document doc : docs) {
            String text = doc.getText();
            if (text != null && !text.isBlank()) {
                pages.add(new ParsedPage(idx++, text.trim()));
            }
        }
        if (pages.isEmpty()) {
            pages.add(new ParsedPage(1, ""));
        }
        return pages;
    }

    private List<ParsedPage> parsePlainText(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return List.of(new ParsedPage(1, text.trim()));
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data == null ? new byte[0] : data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }

    private String generateDocId(String source, String checksum) {
        String cleanSource = source == null ? "doc" : source.replaceAll("[^a-zA-Z0-9.-]", "_");
        String shortChecksum = checksum.length() > 12 ? checksum.substring(0, 12) : checksum;
        return cleanSource + "-" + shortChecksum;
    }

    private String normalizeContentType(String source, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            String primary = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!primary.equals("application/octet-stream")) {
                return primary;
            }
        }
        String lower = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        return "text/plain";
    }
}
