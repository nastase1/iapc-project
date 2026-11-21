package com.edupulse.rag.postretrieval;

/**
 * A stored chunk, as far as stitching needs to know it.
 *
 * @param docId      owning document
 * @param version    document version the chunk belongs to
 * @param chunkIndex position inside the document (0-based, contiguous)
 * @param source     human-readable source name (for citations)
 * @param page       page number, or {@code null} when unknown
 * @param text       chunk text
 */
public record Chunk(String docId, String version, int chunkIndex, String source, Integer page, String text) {
}
