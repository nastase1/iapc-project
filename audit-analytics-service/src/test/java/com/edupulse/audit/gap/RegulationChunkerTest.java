package com.edupulse.audit.gap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegulationChunkerTest {

    @Test
    void splitsOnHeadingsAndKeepsSectionName() {
        String text = """
                Article 1 Data retention
                Records must be kept for seven years.

                Article 2 Access control
                Access must be logged.
                """;
        List<RegulationChunker.Chunk> chunks = RegulationChunker.chunk(text, 500);
        assertEquals(2, chunks.size());
        assertEquals("Article 1 Data retention", chunks.get(0).section());
        assertTrue(chunks.get(1).text().contains("Access must be logged"));
        assertEquals(1, chunks.get(1).index());
    }

    @Test
    void respectsMaxChars() {
        String text = "word ".repeat(400);
        List<RegulationChunker.Chunk> chunks = RegulationChunker.chunk(text, 200);
        assertTrue(chunks.size() > 1);
        chunks.forEach(c -> assertTrue(c.text().length() <= 200));
    }

    @Test
    void hardSplitsVeryLongLine() {
        List<RegulationChunker.Chunk> chunks = RegulationChunker.chunk("x".repeat(1000), 300);
        assertEquals(4, chunks.size());
    }

    @Test
    void blankAndNullInputGiveNoChunks() {
        assertTrue(RegulationChunker.chunk("  \n ", 500).isEmpty());
        assertTrue(RegulationChunker.chunk(null, 500).isEmpty());
    }

    @Test
    void numberedHeadingIsRecognised() {
        List<RegulationChunker.Chunk> chunks = RegulationChunker.chunk("Intro text here.\n3.2 Encryption\nUse AES.", 500);
        assertEquals("Preamble", chunks.get(0).section());
        assertEquals("3.2 Encryption", chunks.get(1).section());
    }

    @Test
    void adversarialInputIsFast() {
        String evil = "1" + ".1".repeat(50_000) + " ";
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> RegulationChunker.chunk(evil, 500));
    }

    @Test
    void tooSmallLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RegulationChunker.chunk("text", 10));
    }
}
