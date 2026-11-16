package com.edupulse.ingestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "organizationId", defaultValue = "default-org") String organizationId,
            @RequestParam(value = "accessLevel", defaultValue = "PUBLIC") String accessLevel) {

        // TODO (Membru 1): Implementare validare format (PDF, DOCX, TXT)
        // TODO (Membru 1): Trimitere către worker asincron dacă dimensiunea > 10MB
        // TODO (Membru 1): Procesare Hierarchical Chunking (parent 1000 tokens, child 200 tokens)
        
        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "fileName", file.getOriginalFilename(),
                "organizationId", organizationId,
                "message", "Document received for asynchronous hierarchical ingestion"
        ));
    }
}
