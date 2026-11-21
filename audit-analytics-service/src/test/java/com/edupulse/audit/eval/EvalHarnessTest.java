package com.edupulse.audit.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalHarnessTest {

    @TempDir
    Path tmp;

    private static GoldenSet goldenSet() {
        return new GoldenSet("test", List.of(
                new GoldenItem("q1", "retention period?", true, List.of("policy.pdf")),
                new GoldenItem("q2", "who approves exceptions?", true, List.of("handbook.pdf")),
                new GoldenItem("q3", "best pizza in town?", false, List.of())));
    }

    private static Retriever fixed(Map<String, List<String>> answers) {
        return (question, k) -> answers.get(question);
    }

    // ---- runner: success path ----

    @Test
    void computesRecallAndMrrOverAnswerableQuestionsOnly() {
        Retriever retriever = fixed(Map.of(
                "retention period?", List.of("policy.pdf"),                       // rank 1
                "who approves exceptions?", List.of("x.pdf", "handbook.pdf"),     // rank 2
                "best pizza in town?", List.of("policy.pdf")));                   // unanswerable: ignored

        EvalReport report = new EvalRunner().run(goldenSet(), retriever, "stub", 5);

        assertEquals(2, report.answerableCount());
        assertEquals(1, report.unanswerableCount());
        assertEquals(1.0, report.meanRecallAtK(), 1e-9);
        assertEquals(0.75, report.mrr(), 1e-9); // (1 + 0.5) / 2
        assertEquals(0, report.failedCount());
    }

    // ---- runner: failure path ----

    @Test
    void failingRetrievalScoresZeroAndDoesNotAbortTheRun() {
        Retriever retriever = (question, k) -> {
            if (question.startsWith("who")) {
                throw new IllegalStateException("service down");
            }
            return List.of("policy.pdf");
        };

        EvalReport report = new EvalRunner().run(goldenSet(), retriever, "stub", 5);

        assertEquals(1, report.failedCount());
        assertEquals(0.5, report.meanRecallAtK(), 1e-9);
        EvalReport.ItemResult failed = report.items().stream().filter(i -> i.id().equals("q2")).findFirst().orElseThrow();
        assertNotNull(failed.error());
        assertTrue(failed.error().contains("service down"));
        assertTrue(report.toMarkdown().contains("ERROR"));
    }

    @Test
    void rejectsInvalidK() {
        assertThrows(IllegalArgumentException.class, () -> new EvalRunner().run(goldenSet(), (q, k) -> List.of(), "stub", 0));
    }

    // ---- golden set validation ----

    @Test
    void loadsGoldenSetFromFile() throws IOException {
        Path file = tmp.resolve("golden.json");
        Files.writeString(file, """
                {"description":"d","items":[
                  {"id":"q1","question":"a?","answerable":true,"expectedSources":["a.pdf"]},
                  {"id":"q2","question":"b?","answerable":false}]}""");
        GoldenSet set = GoldenSet.load(file);
        assertEquals(2, set.items().size());
        assertEquals(List.of(), set.items().get(1).expectedSources());
    }

    @Test
    void rejectsMalformedGoldenSets() throws IOException {
        assertInvalid("{\"items\":[]}");
        assertInvalid("{\"items\":[{\"id\":\"q1\",\"question\":\"a?\",\"answerable\":true}]}");           // no sources
        assertInvalid("{\"items\":[{\"id\":\"q1\",\"question\":\"a?\",\"answerable\":false},"
                + "{\"id\":\"q1\",\"question\":\"b?\",\"answerable\":false}]}");                          // duplicate id
        assertInvalid("not json");
        assertThrows(IllegalArgumentException.class, () -> GoldenSet.load(tmp.resolve("missing.json")));
    }

    private void assertInvalid(String json) throws IOException {
        Path file = tmp.resolve("bad-" + json.hashCode() + ".json");
        Files.writeString(file, json);
        assertThrows(IllegalArgumentException.class, () -> GoldenSet.load(file));
    }

    // ---- regression gate ----

    private static EvalReport report(double recall, double mrr, int failed) {
        return new EvalReport("t", "s", 5, 10, 2, failed, recall, mrr, List.of());
    }

    @Test
    void gatePassesWhenMetricsAreEqualOrBetter() {
        assertTrue(RegressionGate.check(report(0.80, 0.70, 0), report(0.85, 0.72, 0), 0.02).isEmpty());
        assertTrue(RegressionGate.check(report(0.80, 0.70, 0), report(0.79, 0.69, 0), 0.02).isEmpty()); // within tolerance
    }

    @Test
    void gateFailsOnRecallMrrOrFailureRegression() {
        assertEquals(1, RegressionGate.check(report(0.80, 0.70, 0), report(0.70, 0.70, 0), 0.02).size());
        assertEquals(1, RegressionGate.check(report(0.80, 0.70, 0), report(0.80, 0.60, 0), 0.02).size());
        assertEquals(1, RegressionGate.check(report(0.80, 0.70, 0), report(0.80, 0.70, 2), 0.02).size());
    }

    @Test
    void gateRefusesToCompareDifferentK() {
        EvalReport other = new EvalReport("t", "s", 10, 10, 2, 0, 0.9, 0.9, List.of());
        assertFalse(RegressionGate.check(report(0.8, 0.7, 0), other, 0.02).isEmpty());
    }

    // ---- command line, end to end (offline mode) ----

    private Path writeInputs(String recorded) throws IOException {
        Files.writeString(tmp.resolve("golden.json"), """
                {"items":[
                  {"id":"q1","question":"retention period?","answerable":true,"expectedSources":["policy.pdf"]},
                  {"id":"q2","question":"best pizza in town?","answerable":false}]}""");
        Files.writeString(tmp.resolve("recorded.json"), recorded);
        return tmp;
    }

    private String[] args(String... extra) {
        String[] base = {"--golden", tmp.resolve("golden.json").toString(),
                "--results", tmp.resolve("recorded.json").toString(),
                "--out", tmp.resolve("out").toString(), "--k", "5"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    @Test
    void commandLineWritesReportsAndPassesAgainstOwnBaseline() throws IOException {
        writeInputs("{\"retention period?\":[\"policy.pdf\"],\"best pizza in town?\":[]}");
        assertEquals(0, EvalMain.run(args()));
        assertTrue(Files.exists(tmp.resolve("out/report.json")));
        assertTrue(Files.readString(tmp.resolve("out/report.md")).contains("recall@5"));

        assertEquals(0, EvalMain.run(args("--baseline", tmp.resolve("out/report.json").toString())));
    }

    @Test
    void commandLineExitsWithOneOnRegressionAndTwoOnBadInput() throws IOException {
        writeInputs("{\"retention period?\":[\"policy.pdf\"],\"best pizza in town?\":[]}");
        assertEquals(0, EvalMain.run(args()));
        Path baseline = tmp.resolve("baseline.json");
        Files.copy(tmp.resolve("out/report.json"), baseline);

        // the system gets worse: the expected source is no longer retrieved
        Files.writeString(tmp.resolve("recorded.json"), "{\"retention period?\":[\"other.pdf\"],\"best pizza in town?\":[]}");
        assertEquals(1, EvalMain.run(args("--baseline", baseline.toString())));

        assertEquals(2, EvalMain.run(new String[]{"--golden", tmp.resolve("missing.json").toString(), "--results", "x"}));
        assertEquals(2, EvalMain.run(new String[]{"--golden"}));
    }
}
