package com.edupulse.rag.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class ComplianceRagController {

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryCompliance(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default-org") String tenantId) {

        String question = request.getOrDefault("query", "");

        // TODO (Membru 2): Pre-Retrieval -> MultiQueryExpander (3-4 formulari)
        // TODO (Membru 2): Retrieval -> Hybrid Search (pgvector HNSW + tsvector)
        // TODO (Membru 2): Post-Retrieval -> Reranking (Cohere / Cross-Encoder) & Compression
        // TODO (Membru 2): Citare surse stricte (document, pagina, paragraf)

        return ResponseEntity.ok(Map.of(
                "query", question,
                "tenantId", tenantId,
                "answer", "Sample compliance response with citation.",
                "citations", List.of(
                        Map.of("source", "FERPA_Policy.pdf", "page", 4, "snippet", "Student record data privacy...")
                )
        ));
    }
}
