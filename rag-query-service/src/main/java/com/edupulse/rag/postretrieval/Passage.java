package com.edupulse.rag.postretrieval;

/**
 * A stitched context passage: one or more adjacent chunks of a single document, overlap removed.
 *
 * @param firstChunkIndex first chunk included
 * @param lastChunkIndex  last chunk included
 * @param page            page of the first chunk, or {@code null}
 * @param tokens          tokens counted against the budget (sum of the chunks, before overlap removal)
 */
public record Passage(String docId, String version, String source, Integer page,
                      int firstChunkIndex, int lastChunkIndex, String text, int tokens) {
}
