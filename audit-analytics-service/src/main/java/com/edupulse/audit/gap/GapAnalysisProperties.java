package com.edupulse.audit.gap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param chunkSize     caractere maxime per bucata de reglementare
 * @param maxChunks     plafon de cost: cate bucati se analizeaza intr-o rulare (restul => PARTIAL)
 * @param maxInputChars dimensiunea maxima acceptata pentru textul reglementarii
 * @param concurrency   cate analize ruleaza simultan
 * @param queueCapacity cate analize pot astepta; peste aceasta valoare cererea primeste 429
 */
@ConfigurationProperties(prefix = "audit.gap-analysis")
public record GapAnalysisProperties(int chunkSize, int maxChunks, int maxInputChars,
                                    int concurrency, int queueCapacity) {

    public GapAnalysisProperties {
        if (chunkSize <= 0) chunkSize = 1500;
        if (maxChunks <= 0) maxChunks = 100;
        if (maxInputChars <= 0) maxInputChars = 500_000;
        if (concurrency <= 0) concurrency = 4;
        if (queueCapacity <= 0) queueCapacity = 20;
    }
}
