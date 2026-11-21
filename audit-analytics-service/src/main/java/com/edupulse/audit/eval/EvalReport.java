package com.edupulse.audit.eval;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Result of one evaluation run. Retrieval metrics are averaged over the answerable questions only;
 * unanswerable questions are listed separately (their grounding/refusal check belongs to the
 * citations task, S4 T3). Faithfulness is not measured yet.
 */
public record EvalReport(String generatedAt, String system, int k, int answerableCount, int unanswerableCount,
                         int failedCount, double meanRecallAtK, double mrr, List<ItemResult> items) {

    /**
     * @param error the retriever failure for this question, or {@code null}; a failed question scores 0
     */
    public record ItemResult(String id, String question, boolean answerable, List<String> expectedSources,
                             List<String> retrieved, double recallAtK, double reciprocalRank, String error) {
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation report\n\n");
        sb.append("- Generated at: ").append(generatedAt).append('\n');
        sb.append("- System: ").append(system).append('\n');
        sb.append("- k: ").append(k).append('\n');
        sb.append("- Answerable questions: ").append(answerableCount)
                .append(", unanswerable: ").append(unanswerableCount)
                .append(", retrieval failures: ").append(failedCount).append("\n\n");
        sb.append(String.format("| Metric | Value |%n| --- | --- |%n"));
        sb.append(String.format("| recall@%d | %.3f |%n", k, meanRecallAtK));
        sb.append(String.format("| MRR | %.3f |%n", mrr));
        sb.append("| faithfulness | not measured yet (S4 T3) |\n\n");
        sb.append("## Per question\n\n| id | answerable | recall@k | RR | retrieved (top) | note |\n| --- | --- | --- | --- | --- | --- |\n");
        for (ItemResult r : items) {
            sb.append(String.format("| %s | %s | %s | %s | %s | %s |%n", r.id(), r.answerable() ? "yes" : "no",
                    r.answerable() ? String.format("%.2f", r.recallAtK()) : "-",
                    r.answerable() ? String.format("%.2f", r.reciprocalRank()) : "-",
                    String.join(", ", r.retrieved().stream().limit(5).toList()),
                    r.error() == null ? "" : "ERROR: " + r.error().replace('|', '/')));
        }
        return sb.toString();
    }

    public void writeJson(Path file) throws IOException {
        Files.writeString(file, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(this));
    }

    public static EvalReport readJson(Path file) {
        try {
            return JsonMapper.builder().build().readValue(Files.readString(file), EvalReport.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read report " + file + ": " + e.getMessage(), e);
        }
    }
}
