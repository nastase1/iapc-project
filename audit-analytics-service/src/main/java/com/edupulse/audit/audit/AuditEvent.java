package com.edupulse.audit.audit;

import java.time.Instant;
import java.util.UUID;

/** O intrare din audit trail: interogarea, contextul efectiv folosit de LLM si raspunsul. */
public record AuditEvent(UUID id, String tenantId, String userId, String eventType,
                         String query, String context, String answer,
                         Long latencyMs, Instant createdAt) {
}
