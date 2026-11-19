package com.edupulse.audit.report;

import com.edupulse.audit.gap.GapFinding;
import com.edupulse.audit.gap.GapReport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GapReportExporterTest {

    private static GapReport report(List<GapFinding> findings) {
        return new GapReport(UUID.randomUUID(), "t1", "SOX", "COMPLETED", 2, 2, 0, Instant.now(), findings);
    }

    @Test
    void csvHasHeaderAndOneRowPerFinding() {
        String csv = GapReportExporter.toCsv(report(List.of(
                new GapFinding("Article 1", "Keep records", "GAP", "HIGH", "Only 1 year", "policy p.4"))));
        String[] lines = csv.split("\n");
        assertEquals("section,status,severity,finding,requirement,evidence", lines[0]);
        assertEquals("\"Article 1\",\"GAP\",\"HIGH\",\"Only 1 year\",\"Keep records\",\"policy p.4\"", lines[1]);
    }

    @Test
    void csvNeutralizesFormulaInjectionFromLlmOutput() {
        String csv = GapReportExporter.toCsv(report(List.of(
                new GapFinding("s", "r", "GAP", "LOW", "=HYPERLINK(\"http://evil\")", null))));
        assertTrue(csv.contains("\"'=HYPERLINK"));
    }

    @Test
    void pdfIsAValidPdfDocument() {
        byte[] pdf = GapReportExporter.toPdf(report(List.of(
                new GapFinding("Article 1", "Keep records", "GAP", "HIGH", "Only 1 year", "policy p.4"))));
        assertTrue(pdf.length > 500);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    void pdfWithNoFindingsAndNullFieldsDoesNotFail() {
        assertDoesNotThrow(() -> GapReportExporter.toPdf(report(List.of())));
        assertDoesNotThrow(() -> GapReportExporter.toPdf(report(List.of(
                new GapFinding(null, null, "NOT_ASSESSED", null, null, null)))));
    }
}
