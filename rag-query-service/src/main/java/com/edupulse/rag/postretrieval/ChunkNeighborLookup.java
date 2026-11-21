package com.edupulse.rag.postretrieval;

import java.util.Optional;

/**
 * Fetches a chunk by its position. Implemented on top of the vector store once the metadata schema
 * ({@code doc_id}, {@code version}, {@code chunk_index}) exists; tests use an in-memory map.
 */
@FunctionalInterface
public interface ChunkNeighborLookup {

    /** @return the chunk, or empty when the index is outside the document */
    Optional<Chunk> find(String docId, String version, int chunkIndex);
}
