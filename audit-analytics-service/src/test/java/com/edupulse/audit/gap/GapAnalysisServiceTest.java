package com.edupulse.audit.gap;

import com.edupulse.audit.audit.AuditEventRequest;
import com.edupulse.audit.audit.AuditService;
import com.edupulse.audit.client.RagQueryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GapAnalysisServiceTest {

    private static final String REGULATION = """
            Article 1 Retention
            Audit records must be retained for seven years.

            Article 2 Encryption
            Personal data must be encrypted at rest.
            """;

    private RagQueryClient rag;
    private AuditService audit;
    private GapReportRepository repository;

    @BeforeEach
    void setUp() {
        rag = mock(RagQueryClient.class);
        audit = mock(AuditService.class);
        repository = mock(GapReportRepository.class);
        when(rag.query(anyString(), anyString())).thenReturn(Map.of(
                "answer", "We keep records.",
                "citations", List.of(Map.of("source", "policy.pdf", "page", 4, "snippet", "Records kept 1 year"))));
    }

    private GapAnalysisService service(GapAssessor assessor, int maxChunks, Executor executor) {
        return new GapAnalysisService(rag, assessor, audit, repository,
                new GapAnalysisProperties(1500, maxChunks, 10_000, 2, 5), executor);
    }

    /** Ruleaza analiza sincron si intoarce raportul scris in repository. */
    private GapReport analyze(GapAssessor assessor, int maxChunks) {
        service(assessor, maxChunks, Runnable::run).process(UUID.randomUUID(), "t1", "u1", "SOX", REGULATION);
        ArgumentCaptor<GapReport> captor = ArgumentCaptor.forClass(GapReport.class);
        verify(repository).complete(captor.capture());
        return captor.getValue();
    }

    @Test
    void reportsOnlyGapsAndPersistsReport() {
        GapAssessor assessor = (req, ctx) -> req.contains("seven years")
                ? new Assessment(false, "HIGH", "Retention is only 1 year", "Records kept 1 year")
                : new Assessment(true, "LOW", "Covered", "");

        GapReport report = analyze(assessor, 10);

        assertEquals("COMPLETED", report.status());
        assertEquals(2, report.totalChunks());
        assertEquals(1, report.findings().size());
        assertEquals("GAP", report.findings().get(0).status());
        assertEquals("HIGH", report.findings().get(0).severity());
        verify(repository).markRunning(any(), eq(2));
    }

    @Test
    void passesRagContextToAssessor() {
        GapAssessor assessor = mock(GapAssessor.class);
        when(assessor.assess(anyString(), anyString())).thenReturn(new Assessment(true, "LOW", "ok", ""));

        analyze(assessor, 10);

        verify(assessor, times(2)).assess(anyString(), argThat(ctx -> ctx.contains("policy.pdf") && ctx.contains("p.4")));
    }

    @Test
    void failedAssessmentBecomesNotAssessedNotGap() {
        GapAssessor assessor = (req, ctx) -> {
            throw new IllegalStateException("llm down");
        };

        GapReport report = analyze(assessor, 10);

        assertEquals("PARTIAL", report.status());
        assertEquals(2, report.failedChunks());
        assertTrue(report.findings().stream().allMatch(f -> f.status().equals("NOT_ASSESSED")));
    }

    @Test
    void ragFailureDoesNotStopTheBatch() {
        when(rag.query(anyString(), anyString())).thenThrow(new IllegalStateException("rag down"));
        GapAssessor assessor = mock(GapAssessor.class);

        GapReport report = analyze(assessor, 10);

        assertEquals(2, report.failedChunks());
        verifyNoInteractions(assessor);
    }

    @Test
    void maxChunksCapMakesReportPartial() {
        GapAssessor assessor = (req, ctx) -> new Assessment(true, "LOW", "ok", "");

        GapReport report = analyze(assessor, 1);

        assertEquals("PARTIAL", report.status());
        assertEquals(2, report.totalChunks());
        assertEquals(1, report.analyzedChunks());
        verify(rag, times(1)).query(anyString(), anyString());
    }

    @Test
    void progressIsReportedAfterEachChunk() {
        GapAssessor assessor = (req, ctx) -> new Assessment(true, "LOW", "ok", "");

        analyze(assessor, 10);

        verify(repository).updateProgress(any(), eq(1), eq(0));
        verify(repository).updateProgress(any(), eq(2), eq(0));
    }

    @Test
    void auditTrailRecordsEachChunkAndTheReport() {
        GapAssessor assessor = (req, ctx) -> new Assessment(true, "LOW", "ok", "");

        analyze(assessor, 10);

        ArgumentCaptor<AuditEventRequest> captor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(audit, times(3)).record(eq("t1"), captor.capture());
        assertEquals(2, captor.getAllValues().stream().filter(e -> e.eventType().equals("GAP_ANALYSIS_CHUNK")).count());
        assertEquals(1, captor.getAllValues().stream().filter(e -> e.eventType().equals("GAP_ANALYSIS_REPORT")).count());
    }

    @Test
    void auditFailureDoesNotBreakAnalysis() {
        when(audit.record(anyString(), any())).thenThrow(new IllegalStateException("db down"));
        GapAssessor assessor = (req, ctx) -> new Assessment(true, "LOW", "ok", "");

        assertEquals("COMPLETED", analyze(assessor, 10).status());
    }

    @Test
    void unexpectedCrashMarksReportFailed() {
        doThrow(new IllegalStateException("db down")).when(repository).complete(any());
        GapAssessor assessor = (req, ctx) -> new Assessment(true, "LOW", "ok", "");
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(() -> service(assessor, 10, Runnable::run).process(id, "t1", "u1", "SOX", REGULATION));

        verify(repository).markFailed(id);
    }

    @Test
    void submitReturnsPendingReportAndDispatchesJob() {
        Executor deferred = mock(Executor.class);
        GapAssessor assessor = mock(GapAssessor.class);

        GapReport report = service(assessor, 10, deferred).submit("t1", "u1", "SOX", REGULATION);

        assertEquals("PENDING", report.status());
        verify(repository).createPending(report.id(), "t1", "SOX");
        verify(deferred).execute(any(Runnable.class));
        verifyNoInteractions(assessor, rag);
    }

    @Test
    void submitRejectsWhenQueueIsFull() {
        Executor full = r -> {
            throw new RejectedExecutionException();
        };
        GapAssessor assessor = mock(GapAssessor.class);

        assertThrows(AnalysisQueueFullException.class,
                () -> service(assessor, 10, full).submit("t1", "u1", "SOX", REGULATION));

        verify(repository).delete(any());
    }

    @Test
    void oversizedInputIsRejectedBeforeAnythingIsStored() {
        GapAssessor assessor = mock(GapAssessor.class);

        assertThrows(IllegalArgumentException.class,
                () -> service(assessor, 10, Runnable::run).submit("t1", "u1", "SOX", "x".repeat(10_001)));

        verify(repository, never()).createPending(any(), anyString(), anyString());
    }

    @Test
    void staleJobsAreFailedOnStartup() {
        when(repository.failStale()).thenReturn(2);
        service(mock(GapAssessor.class), 10, Runnable::run).failStaleJobs();
        verify(repository).failStale();
    }

    @Test
    void flattenHandlesNullAndMissingFields() {
        assertEquals("", GapAnalysisService.flatten(null));
        assertEquals("hi", GapAnalysisService.flatten(Map.of("answer", "hi")));
    }
}
