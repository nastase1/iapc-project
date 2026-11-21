package com.edupulse.audit.eval;

import java.util.List;

/** The system under evaluation, seen as: question in, ranked list of source names out. */
@FunctionalInterface
public interface Retriever {

    /** @return sources ordered by relevance, best first (duplicates allowed, only first occurrence counts) */
    List<String> retrieve(String question, int k) throws Exception;
}
