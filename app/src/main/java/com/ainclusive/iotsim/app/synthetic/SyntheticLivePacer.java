package com.ainclusive.iotsim.app.synthetic;

import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Drives {@link SyntheticLiveRunService#tickAll()} on a fixed wall-clock cadence so
 * continuous synthetic runs (IS-119, Model B) emit paced values. One daemon thread,
 * mirroring the SSE flush scheduler pattern. Auto-starts on context refresh and shuts
 * down on close.
 */
@Component
public class SyntheticLivePacer {

    private static final Logger log = LoggerFactory.getLogger(SyntheticLivePacer.class);

    private final SyntheticLiveRunService service;
    private final long tickIntervalMs;
    private ScheduledExecutorService scheduler;

    public SyntheticLivePacer(SyntheticLiveRunService service,
            @Value("${iotsim.synthetic.live.tick-interval-ms:250}") long tickIntervalMs) {
        this.service = service;
        this.tickIntervalMs = tickIntervalMs;
    }

    @PostConstruct
    void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory());
        scheduler.scheduleAtFixedRate(this::safeTick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void safeTick() {
        try {
            service.tickAll();
        } catch (RuntimeException e) {
            // tickAll already isolates per-run failures; never let the scheduler thread die.
            log.warn("synthetic-live pacer tick failed; skipping this tick", e);
        }
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "synthetic-live-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
