package com.edupulse.audit.eval;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The golden set: question-and-source pairs, including unanswerable questions.
 *
 * @param description free-text description of the set
 * @param items       the questions
 */
public record GoldenSet(String description, List<GoldenItem> items) {

    public GoldenSet {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static GoldenSet load(Path file) {
        GoldenSet set;
        try {
            set = JsonMapper.builder().build().readValue(Files.readString(file), GoldenSet.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read golden set " + file + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid golden set " + file + ": " + e.getMessage(), e);
        }
        set.validate();
        return set;
    }

    /** Fails fast on a malformed set, so a bad file never produces a misleading report. */
    void validate() {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Golden set has no items");
        }
        Set<String> ids = new HashSet<>();
        for (GoldenItem item : items) {
            if (item.id() == null || item.id().isBlank()) {
                throw new IllegalArgumentException("Golden item without id");
            }
            if (!ids.add(item.id())) {
                throw new IllegalArgumentException("Duplicate golden item id: " + item.id());
            }
            if (item.question() == null || item.question().isBlank()) {
                throw new IllegalArgumentException("Golden item " + item.id() + " has no question");
            }
            if (item.answerable() && item.expectedSources().isEmpty()) {
                throw new IllegalArgumentException("Answerable item " + item.id() + " needs expectedSources");
            }
        }
    }
}
