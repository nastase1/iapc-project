package com.edupulse.audit.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression check against a committed baseline report. A pipeline change must not lower recall@k or MRR
 * by more than {@code maxDrop} (absolute), otherwise the run fails and the change must not be merged.
 */
public final class RegressionGate {

    private RegressionGate() {
    }

    /** @return human-readable violations; empty means no regression */
    public static List<String> check(EvalReport baseline, EvalReport current, double maxDrop) {
        List<String> violations = new ArrayList<>();
        if (baseline.k() != current.k()) {
            violations.add("k differs from the baseline (" + baseline.k() + " vs " + current.k() + "); not comparable");
            return violations;
        }
        if (current.meanRecallAtK() < baseline.meanRecallAtK() - maxDrop) {
            violations.add(String.format("recall@%d dropped from %.3f to %.3f (allowed drop %.3f)",
                    current.k(), baseline.meanRecallAtK(), current.meanRecallAtK(), maxDrop));
        }
        if (current.mrr() < baseline.mrr() - maxDrop) {
            violations.add(String.format("MRR dropped from %.3f to %.3f (allowed drop %.3f)",
                    baseline.mrr(), current.mrr(), maxDrop));
        }
        if (current.failedCount() > baseline.failedCount()) {
            violations.add("more retrieval failures than the baseline (" + current.failedCount()
                    + " vs " + baseline.failedCount() + ")");
        }
        return violations;
    }
}
