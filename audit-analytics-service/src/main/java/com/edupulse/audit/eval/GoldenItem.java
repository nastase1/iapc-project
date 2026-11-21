package com.edupulse.audit.eval;

import java.util.List;

/**
 * One question of the golden set.
 *
 * @param id              stable identifier (used in reports and recorded results)
 * @param question        the user question, as typed
 * @param answerable      {@code false} for out-of-scope questions the system must refuse
 * @param expectedSources sources (document names) that contain the answer; empty when not answerable
 */
public record GoldenItem(String id, String question, boolean answerable, List<String> expectedSources) {

    public GoldenItem {
        expectedSources = expectedSources == null ? List.of() : List.copyOf(expectedSources);
    }
}
