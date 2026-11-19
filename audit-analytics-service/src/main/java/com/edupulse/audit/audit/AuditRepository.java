package com.edupulse.audit.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(String tenantId, AuditEventRequest r) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO audit_event (id, tenant_id, user_id, event_type, query, context, answer, latency_ms)
                VALUES (:id, :tenant, :user, :type, :query, :context, :answer, :latency)
                """)
                .param("id", id).param("tenant", tenantId).param("user", r.userId())
                .param("type", r.eventType()).param("query", r.query())
                .param("context", r.context()).param("answer", r.answer())
                .param("latency", r.latencyMs())
                .update();
        return id;
    }

    /** Toate interogarile sunt filtrate pe tenant: un tenant nu vede niciodata auditul altuia. */
    public List<AuditEvent> findByTenant(String tenantId, int limit) {
        return jdbc.sql("SELECT * FROM audit_event WHERE tenant_id = :t ORDER BY created_at DESC LIMIT :l")
                .param("t", tenantId).param("l", limit)
                .query((rs, n) -> new AuditEvent(
                        rs.getObject("id", UUID.class), rs.getString("tenant_id"), rs.getString("user_id"),
                        rs.getString("event_type"), rs.getString("query"), rs.getString("context"),
                        rs.getString("answer"), (Long) rs.getObject("latency_ms"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    public List<Long> latencies(String tenantId) {
        return jdbc.sql("SELECT latency_ms FROM audit_event WHERE tenant_id = :t AND latency_ms IS NOT NULL")
                .param("t", tenantId).query(Long.class).list();
    }
}
