package com.edupulse.audit.gap;

import com.edupulse.audit.audit.AuditEventRequest;
import com.edupulse.audit.audit.AuditService;
import com.edupulse.audit.client.RagQueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Workflow agentic: reglementare -> bucati -> (RAG politici existente -> evaluare LLM) per bucata -> raport.
 * <p>
 * Analiza ruleaza asincron: {@link #submit} creeaza raportul in stare PENDING si il intoarce imediat,
 * iar progresul (PENDING -> RUNNING -> COMPLETED/PARTIAL/FAILED) se citeste prin GET /reports/{id}.
 * Un chunk care esueaza nu opreste analiza: devine NOT_ASSESSED (nu il declaram nici conform, nici gap).
 */
@Service
public class GapAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GapAnalysisService.class);
    private static final int MAX_QUERY_CHARS = 1000;

    private final RagQueryClient ragClient;
    private final GapAssessor assessor;
    private final AuditService auditService;
    private final GapReportRepository repository;
    private final GapAnalysisProperties props;
    private final Executor executor;

    public GapAnalysisService(RagQueryClient ragClient, GapAssessor assessor, AuditService auditService,
                              GapReportRepository repository, GapAnalysisProperties props,
                              @Qualifier("gapAnalysisExecutor") Executor executor) {
        this.ragClient = ragClient;
        this.assessor = assessor;
        this.auditService = auditService;
        this.repository = repository;
        this.props = props;
        this.executor = executor;
    }

    /** Joburile ramase neterminate la o oprire anterioara nu mai pot continua: le marcam FAILED. */
    @EventListener(ApplicationReadyEvent.class)
    void failStaleJobs() {
        int n = repository.failStale();
        if (n > 0) log.warn("marked {} unfinished gap analyses from a previous run as FAILED", n);
    }

    /**
     * @throws IllegalArgumentException    daca textul depaseste limita de dimensiune
     * @throws AnalysisQueueFullException  daca pool-ul si coada sunt pline
     */
    public GapReport submit(String tenantId, String userId, String framework, String regulationText) {
        if (regulationText.length() > props.maxInputChars()) {
            throw new IllegalArgumentException("regulationText exceeds " + props.maxInputChars() + " characters");
        }
        UUID id = UUID.randomUUID();
        repository.createPending(id, tenantId, framework);
        try {
            executor.execute(() -> process(id, tenantId, userId, framework, regulationText));
        } catch (RejectedExecutionException e) {
            repository.delete(id);
            log.warn("gap analysis rejected, queue full tenant={}", tenantId);
            throw new AnalysisQueueFullException();
        }
        log.info("gap analysis queued report={} tenant={} framework={}", id, tenantId, framework);
        return new GapReport(id, tenantId, framework, "PENDING", 0, 0, 0, Instant.now(), List.of());
    }

    /** Executata pe thread-ul de fundal; nu arunca niciodata, ci marcheaza raportul FAILED. */
    void process(UUID id, String tenantId, String userId, String framework, String regulationText) {
        try {
            GapReport report = analyze(id, tenantId, userId, framework, regulationText);
            repository.complete(report);
            audit(tenantId, userId, "GAP_ANALYSIS_REPORT", framework, null,
                    "report=" + id + " status=" + report.status() + " findings=" + report.findings().size(), null);
            log.info("gap analysis done report={} status={} findings={} failed={}",
                    id, report.status(), report.findings().size(), report.failedChunks());
        } catch (RuntimeException e) {
            log.error("gap analysis crashed report={}", id, e);
            try {
                repository.markFailed(id);
            } catch (RuntimeException inner) {
                log.error("could not mark report {} as FAILED", id, inner);
            }
            audit(tenantId, userId, "GAP_ANALYSIS_ERROR", framework, null, "report=" + id + " crashed", null);
        }
    }

    private GapReport analyze(UUID id, String tenantId, String userId, String framework, String regulationText) {
        List<RegulationChunker.Chunk> all = RegulationChunker.chunk(regulationText, props.chunkSize());
        List<RegulationChunker.Chunk> toAnalyze = all.subList(0, Math.min(all.size(), props.maxChunks()));
        repository.markRunning(id, all.size());
        log.info("gap analysis start report={} chunks={} analyzing={}", id, all.size(), toAnalyze.size());

        List<GapFinding> findings = new ArrayList<>();
        int failed = 0;
        int done = 0;
        for (RegulationChunker.Chunk chunk : toAnalyze) {
            GapFinding finding = analyzeChunk(tenantId, userId, chunk);
            if (finding != null) {
                findings.add(finding);
                if (GapFinding.NOT_ASSESSED.equals(finding.status())) failed++;
            }
            done++;
            updateProgress(id, done, failed);
        }

        boolean complete = failed == 0 && toAnalyze.size() == all.size();
        return new GapReport(id, tenantId, framework, complete ? "COMPLETED" : "PARTIAL",
                all.size(), toAnalyze.size(), failed, Instant.now(), findings);
    }

    private void updateProgress(UUID id, int done, int failed) {
        try {
            repository.updateProgress(id, done, failed);
        } catch (RuntimeException e) {
            log.warn("progress update failed report={}", id, e);
        }
    }

    /** @return null daca cerinta este conforma (nu apare in raport) */
    private GapFinding analyzeChunk(String tenantId, String userId, RegulationChunker.Chunk chunk) {
        String query = chunk.text().length() > MAX_QUERY_CHARS
                ? chunk.text().substring(0, MAX_QUERY_CHARS) : chunk.text();

        String context;
        Long ragLatency;
        long start = System.nanoTime();
        try {
            context = flatten(ragClient.query(query, tenantId));
            ragLatency = (System.nanoTime() - start) / 1_000_000;
        } catch (RuntimeException e) {
            log.warn("rag query failed for chunk {}: {}", chunk.index(), e.toString());
            audit(tenantId, userId, "GAP_ANALYSIS_ERROR", chunk.text(), null, "rag query failed", null);
            return notAssessed(chunk, "Existing policies could not be queried: " + e.getClass().getSimpleName());
        }

        try {
            Assessment a = assessor.assess(chunk.text(), context);
            audit(tenantId, userId, "GAP_ANALYSIS_CHUNK", chunk.text(), context,
                    (a.compliant() ? "COMPLIANT: " : "GAP: ") + a.finding(), ragLatency);
            return a.compliant() ? null
                    : new GapFinding(chunk.section(), chunk.text(), GapFinding.GAP, a.severity(),
                    a.finding(), a.evidence());
        } catch (RuntimeException e) {
            log.warn("assessment failed for chunk {}: {}", chunk.index(), e.toString());
            audit(tenantId, userId, "GAP_ANALYSIS_ERROR", chunk.text(), context, "assessment failed", ragLatency);
            return notAssessed(chunk, "Automatic assessment failed: " + e.getClass().getSimpleName());
        }
    }

    private static GapFinding notAssessed(RegulationChunker.Chunk chunk, String reason) {
        return new GapFinding(chunk.section(), chunk.text(), GapFinding.NOT_ASSESSED, null, reason, null);
    }

    /** Transforma raspunsul RAG (answer + citations) in text de context pentru LLM. */
    static String flatten(Map<String, Object> ragResponse) {
        if (ragResponse == null) return "";
        StringBuilder sb = new StringBuilder();
        Object answer = ragResponse.get("answer");
        if (answer != null) sb.append(answer).append('\n');
        if (ragResponse.get("citations") instanceof List<?> citations) {
            for (Object c : citations) {
                if (c instanceof Map<?, ?> m) {
                    sb.append("[").append(m.get("source")).append(", p.").append(m.get("page")).append("] ")
                            .append(m.get("snippet")).append('\n');
                }
            }
        }
        return sb.toString().strip();
    }

    /** O eroare de audit nu trebuie sa opreasca analiza; este logata cu severitate ERROR. */
    private void audit(String tenantId, String userId, String type, String query, String context,
                       String answer, Long latency) {
        try {
            auditService.record(tenantId, new AuditEventRequest(type, userId, query, context, answer, latency));
        } catch (RuntimeException e) {
            log.error("audit write failed type={} tenant={}", type, tenantId, e);
        }
    }
}
