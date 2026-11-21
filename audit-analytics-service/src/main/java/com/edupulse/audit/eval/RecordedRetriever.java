package com.edupulse.audit.eval;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Offline retriever: replays recorded results ({@code {"<question>": ["source A", "source B"]}}).
 * Makes the harness runnable without the services (CI, demos, before the pipeline exists).
 */
public class RecordedRetriever implements Retriever {

    private final Map<String, List<String>> recorded;

    public RecordedRetriever(Path file) {
        try {
            this.recorded = JsonMapper.builder().build()
                    .readValue(Files.readString(file), new TypeReference<Map<String, List<String>>>() {
                    });
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read recorded results " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> retrieve(String question, int k) {
        List<String> sources = recorded.get(question);
        if (sources == null) {
            throw new IllegalStateException("No recorded result for question");
        }
        return sources;
    }
}
