package com.edupulse.rag.postretrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * Neighbor stitching: expands each retrieved chunk with its adjacent chunks (by {@code chunk_index}),
 * merges runs of adjacent chunks into passages and removes the text overlap between them.
 * <ul>
 *     <li>A token budget is enforced: retrieved chunks are taken first, in rank order; neighbors are added
 *     alternately before/after each hit only while they fit.</li>
 *     <li>Stitching never crosses document (or version) boundaries: a neighbor must have the same
 *     {@code docId} and {@code version} as the hit, whatever the lookup returns.</li>
 * </ul>
 */
public class NeighborStitcher {

    private static final Logger log = LoggerFactory.getLogger(NeighborStitcher.class);

    /** Rough default (about 4 characters per token); replace with the real tokenizer when available. */
    public static final ToIntFunction<String> APPROX_TOKENS = text -> (text.length() + 3) / 4;

    private final ChunkNeighborLookup lookup;
    private final ToIntFunction<String> tokenCounter;
    private final int minOverlapChars;

    /**
     * @param minOverlapChars shortest common suffix/prefix treated as chunk overlap; shorter coincidences
     *                        are kept, so legitimate text is never cut
     */
    public NeighborStitcher(ChunkNeighborLookup lookup, ToIntFunction<String> tokenCounter, int minOverlapChars) {
        if (minOverlapChars < 1) {
            throw new IllegalArgumentException("minOverlapChars must be >= 1");
        }
        this.lookup = lookup;
        this.tokenCounter = tokenCounter;
        this.minOverlapChars = minOverlapChars;
    }

    /**
     * @param hits        retrieved chunks, best first
     * @param window      how many neighbors to take on each side of a hit (0 = no expansion)
     * @param tokenBudget maximum tokens of the whole stitched context
     * @return passages ordered by the best rank of the hit they contain
     */
    public List<Passage> stitch(List<Chunk> hits, int window, int tokenBudget) {
        if (window < 0 || tokenBudget < 1) {
            throw new IllegalArgumentException("window must be >= 0 and tokenBudget >= 1");
        }
        // selected chunks per document+version, ordered by chunk_index
        Map<String, TreeMap<Integer, Chunk>> selected = new LinkedHashMap<>();
        Map<String, Integer> bestRank = new HashMap<>();
        int used = 0;

        // 1) the retrieved chunks themselves have priority over any neighbor
        for (int rank = 0; rank < hits.size(); rank++) {
            Chunk hit = hits.get(rank);
            TreeMap<Integer, Chunk> doc = selected.computeIfAbsent(key(hit), k -> new TreeMap<>());
            bestRank.putIfAbsent(key(hit), rank);
            if (doc.containsKey(hit.chunkIndex())) {
                continue;
            }
            int cost = tokenCounter.applyAsInt(hit.text());
            if (used + cost > tokenBudget) {
                log.debug("Token budget exhausted; skipping hit doc={} chunk={}", hit.docId(), hit.chunkIndex());
                continue;
            }
            doc.put(hit.chunkIndex(), hit);
            used += cost;
        }

        // 2) neighbors, nearest first, alternating before/after, in hit rank order
        for (Chunk hit : hits) {
            TreeMap<Integer, Chunk> doc = selected.get(key(hit));
            if (doc == null || !doc.containsKey(hit.chunkIndex())) {
                continue; // the hit itself did not fit
            }
            for (int distance = 1; distance <= window; distance++) {
                for (int index : new int[]{hit.chunkIndex() - distance, hit.chunkIndex() + distance}) {
                    Optional<Chunk> neighbor = index < 0 || doc.containsKey(index)
                            ? Optional.empty() : lookup.find(hit.docId(), hit.version(), index);
                    if (neighbor.isEmpty() || !sameDocument(hit, neighbor.get()) || neighbor.get().chunkIndex() != index) {
                        continue;
                    }
                    int cost = tokenCounter.applyAsInt(neighbor.get().text());
                    if (used + cost > tokenBudget) {
                        continue;
                    }
                    doc.put(index, neighbor.get());
                    used += cost;
                }
            }
        }

        // 3) merge runs of adjacent chunks into passages
        List<Passage> passages = new ArrayList<>();
        Map<Passage, Integer> rankOf = new HashMap<>();
        for (Map.Entry<String, TreeMap<Integer, Chunk>> entry : selected.entrySet()) {
            for (Passage passage : toPassages(entry.getValue())) {
                passages.add(passage);
                rankOf.put(passage, bestRank.get(entry.getKey()));
            }
        }
        passages.sort(java.util.Comparator.comparingInt(rankOf::get));
        log.info("Stitched {} hits into {} passages ({} of {} tokens)", hits.size(), passages.size(), used, tokenBudget);
        return passages;
    }

    private List<Passage> toPassages(TreeMap<Integer, Chunk> chunks) {
        List<Passage> passages = new ArrayList<>();
        List<Chunk> run = new ArrayList<>();
        for (Chunk chunk : chunks.values()) {
            if (!run.isEmpty() && chunk.chunkIndex() != run.get(run.size() - 1).chunkIndex() + 1) {
                passages.add(toPassage(run));
                run = new ArrayList<>();
            }
            run.add(chunk);
        }
        if (!run.isEmpty()) {
            passages.add(toPassage(run));
        }
        return passages;
    }

    private Passage toPassage(List<Chunk> run) {
        Chunk first = run.get(0);
        StringBuilder text = new StringBuilder(first.text());
        int tokens = tokenCounter.applyAsInt(first.text());
        for (int i = 1; i < run.size(); i++) {
            text = new StringBuilder(mergeText(text.toString(), run.get(i).text(), minOverlapChars));
            tokens += tokenCounter.applyAsInt(run.get(i).text());
        }
        return new Passage(first.docId(), first.version(), first.source(), first.page(),
                first.chunkIndex(), run.get(run.size() - 1).chunkIndex(), text.toString(), tokens);
    }

    /**
     * Appends {@code next} to {@code previous}, dropping the longest suffix of {@code previous} that is also
     * a prefix of {@code next} (the chunk overlap), provided it has at least {@code minOverlap} characters.
     */
    static String mergeText(String previous, String next, int minOverlap) {
        int max = Math.min(previous.length(), next.length());
        for (int length = max; length >= minOverlap; length--) {
            if (previous.regionMatches(previous.length() - length, next, 0, length)) {
                return previous + next.substring(length);
            }
        }
        return previous + "\n" + next;
    }

    private static String key(Chunk chunk) {
        return chunk.docId() + "\u0000" + chunk.version();
    }

    private static boolean sameDocument(Chunk hit, Chunk neighbor) {
        return hit.docId().equals(neighbor.docId()) && java.util.Objects.equals(hit.version(), neighbor.version());
    }
}
