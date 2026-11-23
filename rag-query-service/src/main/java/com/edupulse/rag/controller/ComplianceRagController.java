package com.edupulse.rag.controller;

import com.edupulse.rag.preretrieval.QueryNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/rag")
public class ComplianceRagController {
    private static final Logger log = LoggerFactory.getLogger(ComplianceRagController.class);
    private static final Pattern LOG_CONTROLS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private final QueryNormalizer queryNormalizer;

    public ComplianceRagController(QueryNormalizer queryNormalizer) {
        this.queryNormalizer = queryNormalizer;
    }


    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryCompliance(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default-org") String tenantId) {

        String originalQuery = request.get("query");
        if (log.isTraceEnabled() && originalQuery != null) {
            String escapedQuery = LOG_CONTROLS.matcher(originalQuery).replaceAll(match ->
                    Matcher.quoteReplacement("\\u{%X}".formatted(match.group().codePointAt(0))));
            log.trace("Original RAG query: {}", escapedQuery);
        }
        String question = queryNormalizer.normalize(originalQuery);

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
