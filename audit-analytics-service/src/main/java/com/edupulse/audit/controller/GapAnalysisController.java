package com.edupulse.audit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gap-analysis")
public class GapAnalysisController {

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runGapAnalysis(
            @RequestParam("policyDocumentId") String policyDocumentId,
            @RequestParam("targetFramework") String targetFramework) {

        // TODO (Membru 3): Agentic Workflow -> Spargere regulament chunk cu chunk
        // TODO (Membru 3): Interogare vector store politici existente
        // TODO (Membru 3): Generare raport de neconformitate (Gaps)
        // TODO (Membru 3): Jurnalizare in audit log

        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "policyDocumentId", policyDocumentId,
                "targetFramework", targetFramework,
                "gapsFound", List.of(
                        Map.of(
                                "section", "Data Retention Policy",
                                "severity", "HIGH",
                                "finding", "Existing internal policy lacks explicit 7-year audit retention rules required by framework."
                        )
                )
        ));
    }
}
