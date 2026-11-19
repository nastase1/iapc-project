package com.edupulse.audit.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LatencyStatsTest {

    @Test
    void computesPercentilesAndNfr() {
        LatencyStats s = LatencyStats.of(List.of(100L, 200L, 300L, 400L, 500L), 2000);
        assertEquals(300, s.p50());
        assertEquals(500, s.p95());
        assertTrue(s.nfrMet());
    }

    @Test
    void flagsNfrViolation() {
        assertFalse(LatencyStats.of(List.of(100L, 3000L), 2000).nfrMet());
    }

    @Test
    void emptyInputIsSafe() {
        assertEquals(0, LatencyStats.of(List.of(), 2000).count());
    }
}
