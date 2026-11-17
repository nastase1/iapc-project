package com.edupulse.gateway.ingestion.controller;

import com.edupulse.gateway.security.SecurityConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "accessLevel", defaultValue = "PUBLIC") String accessLevel,
            @AuthenticationPrincipal Jwt jwt) {

        // Metadatele multi-tenant provin din token-ul JWT validat, nu din parametrii clientului
        String tenantId = Objects.requireNonNullElse(jwt.getClaimAsString(SecurityConfig.TENANT_CLAIM), "default-org");
        String orgUnit = Objects.requireNonNullElse(jwt.getClaimAsString(SecurityConfig.ORG_UNIT_CLAIM), "default");

        // TODO (Membru 1): Implementare validare format (PDF, DOCX, TXT)
        // TODO (Membru 1): Trimitere către worker asincron dacă dimensiunea > 10MB
        // TODO (Membru 1): Procesare Hierarchical Chunking (parent 1000 tokens, child 200 tokens)
        // TODO (Membru 1): Salvare embeddings in pgvector cu metadate tenant_id / org_unit / access_level

        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "fileName", Objects.requireNonNullElse(file.getOriginalFilename(), "unknown"),
                "tenantId", tenantId,
                "orgUnit", orgUnit,
                "accessLevel", accessLevel,
                "message", "Document received for asynchronous hierarchical ingestion"
        ));
    }
}
