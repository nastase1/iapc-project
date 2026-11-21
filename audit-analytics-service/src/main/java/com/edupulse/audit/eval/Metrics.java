package com.edupulse.audit.eval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Retrieval metrics, computed on source names. */
public final class Metrics {

    private Metrics() {
    }

    /** Ranked list with duplicates removed (keeps the first occurrence), so a source counts once. */
    static List<String> distinct(List<String> retrieved) {
        return List.copyOf(new LinkedHashSet<>(retrieved));
    }

    /** Share of expected sources found in the top-k results; 0 when nothing is expected. */
    public static double recallAtK(List<String> expected, List<String> retrieved, int k) {
        if (expected.isEmpty()) {
            return 0.0;
        }
        List<String> ranked = distinct(retrieved);
        Set<String> topK = Set.copyOf(ranked.subList(0, Math.min(k, ranked.size())));
        long hits = expected.stream().distinct().filter(topK::contains).count();
        return (double) hits / expected.stream().distinct().count();
    }

    /** 1 / rank of the first expected source within the top-k; 0 when none is found. */
    public static double reciprocalRank(List<String> expected, List<String> retrieved, int k) {
        List<String> ranked = distinct(retrieved);
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (expected.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }
}
