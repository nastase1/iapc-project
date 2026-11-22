package com.edupulse.gateway.ingestion.service;

import com.edupulse.gateway.ingestion.config.IngestionProperties;
import com.edupulse.gateway.ingestion.model.IngestionMetadata;
import com.edupulse.gateway.ingestion.model.IngestionResult;
import com.edupulse.gateway.ingestion.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentParserService parserService;
    private final DocumentChunkerService chunkerService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final IngestionProperties properties;

    public IngestionService(DocumentParserService parserService,
                            DocumentChunkerService chunkerService,
                            VectorStore vectorStore,
                            JdbcTemplate jdbcTemplate,
                            IngestionProperties properties) {
        this.parserService = parserService;
        this.chunkerService = chunkerService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public IngestionResult ingest(String sourceName,
                                  byte[] content,
                                  String contentType,
                                  String customDocId,
                                  String version,
                                  String tenantId,
                                  String orgUnit,
                                  String accessLevel) {
        try {
            ParsedDocument parsedDoc = parserService.parse(sourceName, content, contentType);
            String docId = (customDocId != null && !customDocId.isBlank()) ? customDocId : parsedDoc.docId();

            // Deduplication / Checksum check
            if (isUnchanged(docId, version, tenantId, parsedDoc.checksum())) {
                log.info("Document '{}' (docId: {}, version: {}) is unchanged (checksum: {}), skipping re-indexing",
                        sourceName, docId, version, parsedDoc.checksum());
                return IngestionResult.upToDate(docId, sourceName, version, parsedDoc.checksum());
            }

            IngestionMetadata metadata = new IngestionMetadata(
                    docId,
                    version,
                    sourceName,
                    parsedDoc.checksum(),
                    true,
                    null,
                    tenantId,
                    orgUnit,
                    accessLevel
            );

            List<Document> chunks = chunkerService.chunk(parsedDoc, metadata);
            if (chunks.isEmpty()) {
                log.warn("Document '{}' yielded 0 chunks", sourceName);
                return IngestionResult.failed(docId, sourceName, version, "No readable text content found");
            }

            // Remove previous chunks for this doc_id & version to avoid duplicates on re-run
            removeExistingChunks(docId, version, tenantId);

            // Save new embeddings into pgvector
            vectorStore.accept(chunks);

            log.info("Successfully ingested document '{}' (docId: {}, version: {}) -> {} chunks",
                    sourceName, docId, version, chunks.size());

            return IngestionResult.success(docId, sourceName, version, chunks.size(), parsedDoc.checksum());
        } catch (Exception e) {
            log.error("Failed to ingest document '{}': {}", sourceName, e.getMessage(), e);
            return IngestionResult.failed(customDocId != null ? customDocId : sourceName, sourceName, version, e.getMessage());
        }
    }

    @Async
    public CompletableFuture<IngestionResult> ingestAsync(String sourceName,
                                                          byte[] content,
                                                          String contentType,
                                                          String customDocId,
                                                          String version,
                                                          String tenantId,
                                                          String orgUnit,
                                                          String accessLevel) {
        return CompletableFuture.completedFuture(
                ingest(sourceName, content, contentType, customDocId, version, tenantId, orgUnit, accessLevel)
        );
    }

    private boolean isUnchanged(String docId, String version, String tenantId, String checksum) {
        try {
            List<String> existing = jdbcTemplate.query(
                    "SELECT metadata->>'checksum' FROM vector_store WHERE metadata->>'doc_id' = ? AND metadata->>'version' = ? AND metadata->>'tenant_id' = ? LIMIT 1",
                    (rs, rowNum) -> rs.getString(1),
                    docId, (version == null ? "1.0" : version), (tenantId == null ? "default-org" : tenantId)
            );
            return !existing.isEmpty() && checksum.equalsIgnoreCase(existing.getFirst());
        } catch (Exception e) {
            log.debug("Could not verify existing checksum (vector_store table might be uninitialized yet): {}", e.getMessage());
            return false;
        }
    }

    public void removeExistingChunks(String docId, String version, String tenantId) {
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'doc_id' = ? AND metadata->>'version' = ? AND metadata->>'tenant_id' = ?",
                    docId, (version == null ? "1.0" : version), (tenantId == null ? "default-org" : tenantId)
            );
            if (deleted > 0) {
                log.info("Removed {} existing chunks for docId: {}, version: {}, tenant: {}", deleted, docId, version, tenantId);
            }
        } catch (Exception e) {
            log.debug("Cleanup of previous chunks failed (safe to ignore if table does not exist): {}", e.getMessage());
        }
    }

    public boolean shouldProcessAsync(long byteSize) {
        return byteSize > properties.asyncThresholdBytes();
    }
}
