package com.ainclusive.iotsim.app.replay;

import com.ainclusive.iotsim.domain.replay.ReplayLiveRunService;
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
 * Drives {@link ReplayLiveRunService#tickAll()} on a fixed wall-clock cadence so
 * live replay runs (IS-140) drip values at the original recording pace.
 * One daemon thread, mirroring {@code SyntheticLivePacer}.
 */
@Component
public class ReplayLivePacer {

    private static final Logger log = LoggerFactory.getLogger(ReplayLivePacer.class);

    private final ReplayLiveRunService service;
    private final long tickIntervalMs;
    private ScheduledExecutorService scheduler;

    public ReplayLivePacer(ReplayLiveRunService service,
            @Value("${iotsim.replay.live.tick-interval-ms:250}") long tickIntervalMs) {
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
            log.warn("replay-live pacer tick failed; skipping this tick", e);
        }
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "replay-live-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
