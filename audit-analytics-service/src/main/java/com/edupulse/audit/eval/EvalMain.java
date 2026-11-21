package com.edupulse.audit.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point of the evaluation harness (no Spring context needed).
 * <pre>
 * --golden  &lt;file&gt;      golden set (default eval/golden-set.json)
 * --url     &lt;base url&gt;  live system, e.g. http://localhost:8080 (token from env EVAL_TOKEN)
 * --results &lt;file&gt;      offline mode: recorded results instead of --url
 * --k       &lt;n&gt;         top-k (default 5)
 * --out     &lt;dir&gt;       report directory (default eval/reports): writes report.json and report.md
 * --baseline &lt;file&gt;     committed report.json; the run fails (exit 1) on a regression
 * --max-drop &lt;x&gt;        allowed absolute drop of recall@k / MRR vs baseline (default 0.02)
 * </pre>
 * Exit codes: 0 ok, 1 regression against the baseline, 2 bad arguments or unreadable input.
 */
public final class EvalMain {

    private EvalMain() {
    }

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    static int run(String[] args) throws IOException {
        Args a;
        Retriever retriever;
        GoldenSet goldenSet;
        try {
            a = Args.parse(args);
            goldenSet = GoldenSet.load(Path.of(a.get("golden", "eval/golden-set.json")));
            if (a.has("results")) {
                retriever = new RecordedRetriever(Path.of(a.get("results", null)));
            } else if (a.has("url")) {
                retriever = new HttpRagRetriever(a.get("url", null), System.getenv("EVAL_TOKEN"));
            } else {
                throw new IllegalArgumentException("Pass either --url <base url> or --results <file>");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
        int k = Integer.parseInt(a.get("k", "5"));
        String system = a.has("results") ? "recorded:" + a.get("results", null) : a.get("url", null);

        EvalReport report = new EvalRunner().run(goldenSet, retriever, system, k);

        Path outDir = Path.of(a.get("out", "eval/reports"));
        Files.createDirectories(outDir);
        report.writeJson(outDir.resolve("report.json"));
        Files.writeString(outDir.resolve("report.md"), report.toMarkdown());
        System.out.println(report.toMarkdown());

        if (a.has("baseline")) {
            EvalReport baseline = EvalReport.readJson(Path.of(a.get("baseline", null)));
            List<String> violations = RegressionGate.check(baseline, report, Double.parseDouble(a.get("max-drop", "0.02")));
            if (!violations.isEmpty()) {
                violations.forEach(v -> System.err.println("REGRESSION: " + v));
                return 1;
            }
            System.out.println("No regression against the baseline.");
        }
        return 0;
    }

    private record Args(java.util.Map<String, String> values) {

        static Args parse(String[] args) {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            for (int i = 0; i < args.length; i++) {
                if (!args[i].startsWith("--") || i + 1 >= args.length) {
                    throw new IllegalArgumentException("Bad argument: " + args[i]);
                }
                map.put(args[i].substring(2), args[++i]);
            }
            return new Args(map);
        }

        boolean has(String key) {
            return values.containsKey(key);
        }

        String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }
    }
}
