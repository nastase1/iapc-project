package com.edupulse.audit.audit;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tenantul vine din headerul X-Tenant-Id, setat de gateway din JWT. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, UUID>> record(@RequestHeader("X-Tenant-Id") String tenantId,
                                                    @Valid @RequestBody AuditEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", service.record(tenantId, request)));
    }

    @GetMapping("/events")
    public List<AuditEvent> list(@RequestHeader("X-Tenant-Id") String tenantId,
                                 @RequestParam(defaultValue = "100") int limit) {
        return service.list(tenantId, limit);
    }

    @GetMapping("/stats/latency")
    public LatencyStats latency(@RequestHeader("X-Tenant-Id") String tenantId) {
        return service.latencyStats(tenantId);
    }
}
