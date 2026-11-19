package com.edupulse.audit.audit;

import java.util.Collections;
import java.util.List;

public record LatencyStats(int count, long p50, long p95, long max, long thresholdMs, boolean nfrMet) {

    public static LatencyStats of(List<Long> values, long thresholdMs) {
        if (values.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, thresholdMs, true);
        }
        List<Long> sorted = values.stream().sorted().toList();
        long p95 = percentile(sorted, 95);
        return new LatencyStats(sorted.size(), percentile(sorted, 50), p95,
                Collections.max(sorted), thresholdMs, p95 <= thresholdMs);
    }

    static long percentile(List<Long> sorted, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(idx, 0));
    }
}
