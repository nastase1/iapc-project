package com.edupulse.gateway.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edupulse.ingestion")
public record IngestionProperties(
        Integer chunkSize,
        Integer chunkOverlap,
        Long asyncThresholdBytes
) {
    public IngestionProperties {
        chunkSize = (chunkSize == null || chunkSize <= 0) ? 800 : chunkSize;
        chunkOverlap = (chunkOverlap == null || chunkOverlap < 0) ? 100 : chunkOverlap;
        asyncThresholdBytes = (asyncThresholdBytes == null || asyncThresholdBytes <= 0)
                ? 10L * 1024 * 1024
                : asyncThresholdBytes;
    }
}
