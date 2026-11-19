package com.edupulse.audit.report;

import com.edupulse.audit.audit.AuditEvent;
import com.edupulse.audit.audit.AuditService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Export audit trail: JSON si CSV (PDF ramane de facut). */
@RestController
@RequestMapping("/api/audit/export")
public class ReportController {

    private final AuditService service;

    public ReportController(AuditService service) {
        this.service = service;
    }

    @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AuditEvent> json(@RequestHeader("X-Tenant-Id") String tenantId) {
        return service.list(tenantId, 1000);
    }

    @GetMapping(value = "/csv", produces = "text/csv")
    public ResponseEntity<String> csv(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=audit-trail.csv")
                .body(CsvWriter.toCsv(service.list(tenantId, 1000)));
    }
}
