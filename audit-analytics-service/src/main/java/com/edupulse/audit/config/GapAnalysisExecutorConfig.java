package com.edupulse.audit.config;

import com.edupulse.audit.gap.GapAnalysisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Pool marginit pentru analizele de gap: {@code concurrency} rulari simultane + coada limitata.
 * Cand coada e plina, {@link ThreadPoolExecutor.AbortPolicy} respinge, iar API-ul raspunde 429
 * (backpressure explicit, in loc de cereri pierdute sau memorie nemarginita).
 */
@Configuration
public class GapAnalysisExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor gapAnalysisExecutor(GapAnalysisProperties props) {
        return new ThreadPoolExecutor(props.concurrency(), props.concurrency(), 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.queueCapacity()),
                r -> {
                    Thread t = new Thread(r, "gap-analysis");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
