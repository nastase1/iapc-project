package com.edupulse.gateway.ingestion.controller;

import com.edupulse.gateway.ingestion.model.IngestionResult;
import com.edupulse.gateway.ingestion.service.IngestionService;
import com.edupulse.gateway.security.SecurityConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "version", defaultValue = "1.0") String version,
            @RequestParam(value = "accessLevel", defaultValue = "PUBLIC") String accessLevel,
            @AuthenticationPrincipal Jwt jwt) throws IOException {

        String tenantId = (jwt != null)
                ? Objects.requireNonNullElse(jwt.getClaimAsString(SecurityConfig.TENANT_CLAIM), "default-org")
                : "default-org";
        String orgUnit = (jwt != null)
                ? Objects.requireNonNullElse(jwt.getClaimAsString(SecurityConfig.ORG_UNIT_CLAIM), "default")
                : "default";

        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "document");
        byte[] bytes = file.getBytes();
        String contentType = file.getContentType();

        if (ingestionService.shouldProcessAsync(bytes.length)) {
            ingestionService.ingestAsync(filename, bytes, contentType, docId, version, tenantId, orgUnit, accessLevel);
            return ResponseEntity.accepted().body(Map.of(
                    "status", "ACCEPTED",
                    "fileName", filename,
                    "tenantId", tenantId,
                    "orgUnit", orgUnit,
                    "accessLevel", accessLevel,
                    "message", "Large document (" + bytes.length + " bytes) scheduled for asynchronous hierarchical ingestion"
            ));
        }

        IngestionResult result = ingestionService.ingest(
                filename, bytes, contentType, docId, version, tenantId, orgUnit, accessLevel
        );

        if ("FAILED".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "status", result.status(),
                    "docId", result.docId(),
                    "fileName", filename,
                    "message", result.message()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", result.status(),
                "docId", result.docId(),
                "fileName", filename,
                "version", result.version(),
                "totalChunks", result.totalChunks(),
                "checksum", Objects.requireNonNullElse(result.checksum(), ""),
                "message", result.message()
        ));
    }
}
