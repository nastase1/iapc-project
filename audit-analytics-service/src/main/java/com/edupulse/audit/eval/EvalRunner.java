package com.edupulse.audit.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Runs a golden set against a {@link Retriever} and computes recall@k and MRR. */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    public EvalReport run(GoldenSet goldenSet, Retriever retriever, String systemName, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        List<EvalReport.ItemResult> results = new ArrayList<>();
        int answerable = 0;
        int unanswerable = 0;
        int failed = 0;
        double recallSum = 0;
        double rrSum = 0;

        for (GoldenItem item : goldenSet.items()) {
            List<String> retrieved = List.of();
            String error = null;
            try {
                retrieved = retriever.retrieve(item.question(), k);
            } catch (Exception e) {
                // one failing question must not abort the run; it scores 0 and is reported
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                failed++;
                log.warn("Retrieval failed for golden item {}: {}", item.id(), error);
            }
            double recall = 0;
            double rr = 0;
            if (item.answerable()) {
                answerable++;
                recall = Metrics.recallAtK(item.expectedSources(), retrieved, k);
                rr = Metrics.reciprocalRank(item.expectedSources(), retrieved, k);
                recallSum += recall;
                rrSum += rr;
            } else {
                unanswerable++;
            }
            results.add(new EvalReport.ItemResult(item.id(), item.question(), item.answerable(),
                    item.expectedSources(), retrieved, recall, rr, error));
        }
        double meanRecall = answerable == 0 ? 0 : recallSum / answerable;
        double mrr = answerable == 0 ? 0 : rrSum / answerable;
        log.info("Evaluated {} questions ({} answerable): recall@{}={}, MRR={}, failures={}",
                goldenSet.items().size(), answerable, k, String.format("%.3f", meanRecall),
                String.format("%.3f", mrr), failed);
        return new EvalReport(Instant.now().toString(), systemName, k, answerable, unanswerable, failed,
                meanRecall, mrr, results);
    }
}
