package com.edupulse.audit.audit;

import jakarta.validation.constraints.NotBlank;

public record AuditEventRequest(@NotBlank String eventType, String userId, String query,
                                String context, String answer, Long latencyMs) {
}
