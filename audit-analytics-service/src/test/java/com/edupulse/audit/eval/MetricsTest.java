package com.edupulse.audit.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsTest {

    @Test
    void recallCountsExpectedSourcesFoundInTopK() {
        List<String> expected = List.of("a.pdf", "b.pdf");
        assertEquals(0.5, Metrics.recallAtK(expected, List.of("x.pdf", "a.pdf", "y.pdf"), 3), 1e-9);
        assertEquals(1.0, Metrics.recallAtK(expected, List.of("b.pdf", "a.pdf"), 2), 1e-9);
    }

    @Test
    void recallIgnoresResultsBeyondK() {
        assertEquals(0.0, Metrics.recallAtK(List.of("a.pdf"), List.of("x.pdf", "y.pdf", "a.pdf"), 2), 1e-9);
    }

    @Test
    void duplicateSourcesCountOnceAndDoNotPushRanks() {
        // a chunk-level retriever returns the same document several times
        assertEquals(0.5, Metrics.reciprocalRank(List.of("a.pdf"), List.of("x.pdf", "x.pdf", "x.pdf", "a.pdf"), 5), 1e-9);
        assertEquals(0.5, Metrics.recallAtK(List.of("a.pdf", "b.pdf"), List.of("a.pdf", "a.pdf", "a.pdf"), 3), 1e-9);
    }

    @Test
    void reciprocalRankIsOneOverRankOfFirstHit() {
        assertEquals(1.0, Metrics.reciprocalRank(List.of("a.pdf"), List.of("a.pdf", "b.pdf"), 5), 1e-9);
        assertEquals(1.0 / 3, Metrics.reciprocalRank(List.of("a.pdf"), List.of("x", "y", "a.pdf"), 5), 1e-9);
    }

    @Test
    void reciprocalRankIsZeroWhenNothingFoundOrOutsideK() {
        assertEquals(0.0, Metrics.reciprocalRank(List.of("a.pdf"), List.of("x", "y"), 5), 1e-9);
        assertEquals(0.0, Metrics.reciprocalRank(List.of("a.pdf"), List.of("x", "y", "a.pdf"), 2), 1e-9);
    }

    @Test
    void emptyResultsScoreZero() {
        assertEquals(0.0, Metrics.recallAtK(List.of("a.pdf"), List.of(), 5), 1e-9);
        assertEquals(0.0, Metrics.reciprocalRank(List.of("a.pdf"), List.of(), 5), 1e-9);
        assertEquals(0.0, Metrics.recallAtK(List.of(), List.of("a.pdf"), 5), 1e-9);
    }
}
