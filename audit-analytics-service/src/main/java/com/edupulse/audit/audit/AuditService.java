package com.edupulse.audit.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public UUID record(String tenantId, AuditEventRequest request) {
        UUID id = repository.insert(tenantId, request);
        log.info("audit event stored id={} tenant={} type={}", id, tenantId, request.eventType());
        return id;
    }

    public List<AuditEvent> list(String tenantId, int limit) {
        return repository.findByTenant(tenantId, Math.min(Math.max(limit, 1), 1000));
    }

    /** p50/p95 pe latenta interogarilor; NFR: sub 2000 ms. */
    public LatencyStats latencyStats(String tenantId) {
        return LatencyStats.of(repository.latencies(tenantId), 2000);
    }
}
