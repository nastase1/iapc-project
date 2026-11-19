package com.edupulse.audit.controller;

import com.edupulse.audit.gap.AnalysisQueueFullException;
import com.edupulse.audit.gap.GapAnalysisService;
import com.edupulse.audit.gap.GapReport;
import com.edupulse.audit.gap.GapReportRepository;
import com.edupulse.audit.report.GapReportExporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/gap-analysis")
public class GapAnalysisController {

    private static final Set<String> FINISHED = Set.of("COMPLETED", "PARTIAL");

    public record RunRequest(@NotBlank String targetFramework, @NotBlank String regulationText) {
    }

    private final GapAnalysisService service;
    private final GapReportRepository repository;

    public GapAnalysisController(GapAnalysisService service, GapReportRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    /**
     * Porneste analiza in fundal si raspunde imediat 202 cu raportul PENDING.
     * Progresul se citeste cu GET /reports/{id} (status PENDING, RUNNING, COMPLETED, PARTIAL, FAILED).
     */
    @PostMapping("/run")
    public ResponseEntity<GapReport> run(@Valid @RequestBody RunRequest request,
                                         @RequestHeader(value = "X-Tenant-Id", defaultValue = "default-org") String tenantId,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            GapReport pending = service.submit(tenantId, userId, request.targetFramework(), request.regulationText());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(HttpHeaders.LOCATION, "/api/gap-analysis/reports/" + pending.id())
                    .body(pending);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
        } catch (AnalysisQueueFullException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
    }

    @GetMapping("/reports")
    public List<GapReport> reports(@RequestHeader(value = "X-Tenant-Id", defaultValue = "default-org") String tenantId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return repository.list(tenantId, Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<GapReport> report(@PathVariable UUID id,
                                            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default-org") String tenantId) {
        return repository.findById(tenantId, id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Export al unui raport terminat: format = json | csv | pdf. */
    @GetMapping("/reports/{id}/export")
    public ResponseEntity<?> export(@PathVariable UUID id,
                                    @RequestParam(defaultValue = "json") String format,
                                    @RequestHeader(value = "X-Tenant-Id", defaultValue = "default-org") String tenantId) {
        GapReport report = repository.findById(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (!FINISHED.contains(report.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Report is " + report.status() + ", export is available when it is COMPLETED or PARTIAL");
        }
        String base = "gap-report-" + report.id();
        return switch (format.toLowerCase()) {
            case "json" -> download(MediaType.APPLICATION_JSON, base + ".json", report);
            case "csv" -> download(MediaType.parseMediaType("text/csv"), base + ".csv", GapReportExporter.toCsv(report));
            case "pdf" -> download(MediaType.APPLICATION_PDF, base + ".pdf", GapReportExporter.toPdf(report));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be json, csv or pdf");
        };
    }

    private static ResponseEntity<Object> download(MediaType type, String filename, Object body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
