package com.edupulse.gateway.ingestion.model;

public record IngestionResult(
        String docId,
        String source,
        String version,
        String status,
        int totalChunks,
        String checksum,
        String message
) {
    public static IngestionResult success(String docId, String source, String version, int totalChunks, String checksum) {
        return new IngestionResult(docId, source, version, "SUCCESS", totalChunks, checksum, "Ingested successfully");
    }

    public static IngestionResult updated(String docId, String source, String version, int totalChunks, String checksum) {
        return new IngestionResult(docId, source, version, "UPDATED", totalChunks, checksum, "Document updated atomically");
    }

    public static IngestionResult upToDate(String docId, String source, String version, String checksum) {
        return new IngestionResult(docId, source, version, "ALREADY_UP_TO_DATE", 0, checksum, "Document content unchanged");
    }

    public static IngestionResult failed(String docId, String source, String version, String message) {
        return new IngestionResult(docId, source, version, "FAILED", 0, null, message);
    }
}
