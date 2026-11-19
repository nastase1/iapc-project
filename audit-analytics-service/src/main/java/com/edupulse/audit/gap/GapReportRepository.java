package com.edupulse.audit.gap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GapReportRepository {

    private final JdbcClient jdbc;

    public GapReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void createPending(UUID id, String tenantId, String framework) {
        jdbc.sql("""
                INSERT INTO gap_report (id, tenant_id, framework, status, total_chunks, analyzed_chunks, failed_chunks)
                VALUES (:id, :tenant, :fw, 'PENDING', 0, 0, 0)
                """)
                .param("id", id).param("tenant", tenantId).param("fw", framework)
                .update();
    }

    public void markRunning(UUID id, int totalChunks) {
        jdbc.sql("UPDATE gap_report SET status = 'RUNNING', total_chunks = :total WHERE id = :id")
                .param("id", id).param("total", totalChunks).update();
    }

    public void updateProgress(UUID id, int analyzed, int failed) {
        jdbc.sql("UPDATE gap_report SET analyzed_chunks = :a, failed_chunks = :f WHERE id = :id")
                .param("id", id).param("a", analyzed).param("f", failed).update();
    }

    /** Scrie rezultatul final si constatarile intr-o singura tranzactie. */
    @Transactional
    public void complete(GapReport r) {
        jdbc.sql("""
                UPDATE gap_report SET status = :status, total_chunks = :total,
                    analyzed_chunks = :analyzed, failed_chunks = :failed
                WHERE id = :id
                """)
                .param("id", r.id()).param("status", r.status()).param("total", r.totalChunks())
                .param("analyzed", r.analyzedChunks()).param("failed", r.failedChunks())
                .update();
        int position = 0;
        for (GapFinding f : r.findings()) {
            jdbc.sql("""
                    INSERT INTO gap_finding (id, report_id, position, section, requirement, status, severity, finding, evidence)
                    VALUES (:id, :report, :pos, :section, :req, :status, :sev, :finding, :evidence)
                    """)
                    .param("id", UUID.randomUUID()).param("report", r.id()).param("pos", position++)
                    .param("section", truncate(f.section(), 300)).param("req", f.requirement())
                    .param("status", f.status()).param("sev", f.severity())
                    .param("finding", f.finding()).param("evidence", f.evidence())
                    .update();
        }
    }

    public void markFailed(UUID id) {
        jdbc.sql("UPDATE gap_report SET status = 'FAILED' WHERE id = :id").param("id", id).update();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM gap_report WHERE id = :id").param("id", id).update();
    }

    /** La pornire: joburile ramase PENDING/RUNNING dintr-o rulare anterioara nu mai pot continua. */
    public int failStale() {
        return jdbc.sql("UPDATE gap_report SET status = 'FAILED' WHERE status IN ('PENDING', 'RUNNING')").update();
    }

    /** Filtrat pe tenant: un raport al altui tenant este tratat ca inexistent. */
    public Optional<GapReport> findById(String tenantId, UUID id) {
        return jdbc.sql("SELECT * FROM gap_report WHERE id = :id AND tenant_id = :t")
                .param("id", id).param("t", tenantId)
                .query((rs, n) -> map(rs, findings(id)))
                .optional();
    }

    public List<GapReport> list(String tenantId, int limit) {
        return jdbc.sql("SELECT * FROM gap_report WHERE tenant_id = :t ORDER BY created_at DESC LIMIT :l")
                .param("t", tenantId).param("l", limit)
                .query((rs, n) -> map(rs, List.of()))
                .list();
    }

    private List<GapFinding> findings(UUID reportId) {
        return jdbc.sql("SELECT * FROM gap_finding WHERE report_id = :r ORDER BY position")
                .param("r", reportId)
                .query((rs, n) -> new GapFinding(rs.getString("section"), rs.getString("requirement"),
                        rs.getString("status"), rs.getString("severity"),
                        rs.getString("finding"), rs.getString("evidence")))
                .list();
    }

    private static GapReport map(ResultSet rs, List<GapFinding> findings) throws SQLException {
        return new GapReport(rs.getObject("id", UUID.class), rs.getString("tenant_id"), rs.getString("framework"),
                rs.getString("status"), rs.getInt("total_chunks"), rs.getInt("analyzed_chunks"),
                rs.getInt("failed_chunks"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), findings);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
