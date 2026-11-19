package com.edupulse.audit.gap;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** status: COMPLETED sau PARTIAL (chunk-uri esuate sau peste limita de cost). */
public record GapReport(UUID id, String tenantId, String framework, String status,
                        int totalChunks, int analyzedChunks, int failedChunks,
                        Instant createdAt, List<GapFinding> findings) {
}
