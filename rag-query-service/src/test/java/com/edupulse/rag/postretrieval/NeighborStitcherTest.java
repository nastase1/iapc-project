package com.edupulse.rag.postretrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborStitcherTest {

    /** 1 token per character keeps the budget arithmetic obvious in the tests. */
    private static final java.util.function.ToIntFunction<String> CHARS = String::length;

    /** In-memory store; "overlap" chunks share a 6-char tail/head, like the real chunker's overlap. */
    private final Map<String, Chunk> store = new HashMap<>();

    private static String key(String doc, String version, int index) {
        return doc + "|" + version + "|" + index;
    }

    private void add(String doc, String version, int index, String text) {
        store.put(key(doc, version, index), new Chunk(doc, version, index, doc + ".pdf", index + 1, text));
    }

    private Chunk chunk(String doc, int index) {
        return store.get(key(doc, "v1", index));
    }

    private NeighborStitcher stitcher() {
        return new NeighborStitcher((doc, version, index) -> Optional.ofNullable(store.get(key(doc, version, index))), CHARS, 4);
    }

    /** Document with 5 chunks A..E, each overlapping the next by 6 characters ("..ov12" style). */
    private void fiveChunkDoc(String doc) {
        add(doc, "v1", 0, "alpha text ovr001");
        add(doc, "v1", 1, "ovr001 bravo text ovr002");
        add(doc, "v1", 2, "ovr002 charlie text ovr003");
        add(doc, "v1", 3, "ovr003 delta text ovr004");
        add(doc, "v1", 4, "ovr004 echo text");
    }

    // ---- success path ----

    @Test
    void mergesAdjacentChunksAndRemovesOverlap() {
        fiveChunkDoc("d1");
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 2)), 1, 1000);

        assertEquals(1, passages.size());
        Passage p = passages.get(0);
        assertEquals(1, p.firstChunkIndex());
        assertEquals(3, p.lastChunkIndex());
        assertEquals("ovr001 bravo text ovr002 charlie text ovr003 delta text ovr004", p.text());
        assertEquals(2, p.page()); // page of the first chunk
        assertEquals("d1.pdf", p.source());
    }

    @Test
    void overlappingHitsMergeIntoOnePassage() {
        fiveChunkDoc("d1");
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 1), chunk("d1", 3)), 1, 1000);

        assertEquals(1, passages.size());
        assertEquals(0, passages.get(0).firstChunkIndex());
        assertEquals(4, passages.get(0).lastChunkIndex());
        assertEquals(1, countOccurrences(passages.get(0).text(), "charlie"));
        assertEquals(1, countOccurrences(passages.get(0).text(), "ovr002"));
    }

    @Test
    void nonAdjacentHitsStayInSeparatePassages() {
        fiveChunkDoc("d1");
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 0), chunk("d1", 4)), 0, 1000);
        assertEquals(2, passages.size());
    }

    @Test
    void passagesFollowTheRankOfTheirBestHit() {
        fiveChunkDoc("d1");
        fiveChunkDoc("d2");
        List<Passage> passages = stitcher().stitch(List.of(chunk("d2", 3), chunk("d1", 1)), 0, 1000);
        assertEquals("d2", passages.get(0).docId());
        assertEquals("d1", passages.get(1).docId());
    }

    // ---- edge cases required by the task: first chunk, last chunk, single-chunk document ----

    @Test
    void firstChunkHasNoPreviousNeighbor() {
        fiveChunkDoc("d1");
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 0)), 2, 1000);

        assertEquals(1, passages.size());
        assertEquals(0, passages.get(0).firstChunkIndex());
        assertEquals(2, passages.get(0).lastChunkIndex());
    }

    @Test
    void lastChunkHasNoNextNeighbor() {
        fiveChunkDoc("d1");
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 4)), 2, 1000);

        assertEquals(1, passages.size());
        assertEquals(2, passages.get(0).firstChunkIndex());
        assertEquals(4, passages.get(0).lastChunkIndex());
    }

    @Test
    void singleChunkDocumentIsReturnedAsIs() {
        add("solo", "v1", 0, "only chunk of the document");
        List<Passage> passages = stitcher().stitch(List.of(chunk("solo", 0)), 3, 1000);

        assertEquals(1, passages.size());
        assertEquals("only chunk of the document", passages.get(0).text());
        assertEquals(0, passages.get(0).firstChunkIndex());
        assertEquals(0, passages.get(0).lastChunkIndex());
    }

    // ---- token budget ----

    @Test
    void neverExceedsTheTokenBudget() {
        fiveChunkDoc("d1");
        int budget = 60;
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 2)), 2, budget);

        int total = passages.stream().mapToInt(Passage::tokens).sum();
        assertTrue(total <= budget, "used " + total + " > " + budget);
        // the hit itself is always kept before any neighbor
        assertTrue(passages.stream().anyMatch(p -> p.firstChunkIndex() <= 2 && p.lastChunkIndex() >= 2));
    }

    @Test
    void hitsAreServedBeforeNeighborsWhenBudgetIsTight() {
        fiveChunkDoc("d1");
        // budget fits exactly the two hits (index 0 and 4), no neighbor
        int budget = chunk("d1", 0).text().length() + chunk("d1", 4).text().length();
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 0), chunk("d1", 4)), 1, budget);

        assertEquals(2, passages.size());
        assertEquals(budget, passages.stream().mapToInt(Passage::tokens).sum());
    }

    @Test
    void skipsAHitThatDoesNotFitAndKeepsTheBetterRankedOnes() {
        add("d1", "v1", 0, "short");
        add("d2", "v1", 0, "x".repeat(50));
        List<Passage> passages = stitcher().stitch(List.of(chunk("d1", 0), chunk("d2", 0)), 0, 10);

        assertEquals(1, passages.size());
        assertEquals("d1", passages.get(0).docId());
    }

    // ---- document and version boundaries ----

    @Test
    void neverCrossesDocumentBoundaries() {
        fiveChunkDoc("d1");
        fiveChunkDoc("d2");
        // a faulty lookup that ignores the document id and returns chunks of another document
        NeighborStitcher leaky = new NeighborStitcher(
                (doc, version, index) -> Optional.ofNullable(store.get(key("d2", version, index))), CHARS, 4);

        List<Passage> passages = leaky.stitch(List.of(chunk("d1", 2)), 2, 1000);

        assertEquals(1, passages.size());
        assertEquals("d1", passages.get(0).docId());
        assertEquals(2, passages.get(0).firstChunkIndex());
        assertEquals(2, passages.get(0).lastChunkIndex());
        assertFalse(passages.get(0).text().contains("d2"));
    }

    @Test
    void neverMixesVersionsOfTheSameDocument() {
        fiveChunkDoc("d1");
        add("d1", "v2", 2, "chunk of the new version");
        NeighborStitcher leaky = new NeighborStitcher(
                (doc, version, index) -> Optional.ofNullable(store.get(key(doc, "v2", index))), CHARS, 4);

        List<Passage> passages = leaky.stitch(List.of(chunk("d1", 1)), 1, 1000);

        assertEquals(1, passages.size());
        assertEquals("v1", passages.get(0).version());
        assertEquals(1, passages.get(0).lastChunkIndex());
    }

    @Test
    void ignoresALookupResultWithTheWrongIndex() {
        fiveChunkDoc("d1");
        NeighborStitcher faulty = new NeighborStitcher(
                (doc, version, index) -> Optional.of(chunk("d1", 4)), CHARS, 4);
        List<Passage> passages = faulty.stitch(List.of(chunk("d1", 0)), 1, 1000);
        assertEquals(1, passages.size());
        assertEquals(0, passages.get(0).lastChunkIndex());
    }

    // ---- overlap deduplication ----

    @Test
    void mergeTextRemovesOnlyRealOverlap() {
        assertEquals("hello world and more", NeighborStitcher.mergeText("hello world", "world and more", 4));
        // overlap shorter than the minimum is not trusted: both texts are kept
        assertEquals("abc\nc def", NeighborStitcher.mergeText("abc", "c def", 4));
        assertEquals("one\ntwo", NeighborStitcher.mergeText("one", "two", 4));
    }

    // ---- failure / degenerate input ----

    @Test
    void emptyHitsGiveNoPassages() {
        assertTrue(stitcher().stitch(List.of(), 1, 100).isEmpty());
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> stitcher().stitch(List.of(), -1, 100));
        assertThrows(IllegalArgumentException.class, () -> stitcher().stitch(List.of(), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NeighborStitcher((d, v, i) -> Optional.empty(), CHARS, 0));
    }

    @Test
    void duplicateHitsAreCountedOnce() {
        fiveChunkDoc("d1");
        List<Chunk> hits = new ArrayList<>(List.of(chunk("d1", 2), chunk("d1", 2)));
        List<Passage> passages = stitcher().stitch(hits, 0, 1000);
        assertEquals(1, passages.size());
        assertEquals(chunk("d1", 2).text().length(), passages.get(0).tokens());
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
