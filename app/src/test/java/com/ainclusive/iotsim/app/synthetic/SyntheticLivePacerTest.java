package com.ainclusive.iotsim.app.synthetic;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SyntheticLivePacerTest {

    @Test
    void scheduledPacerInvokesTickAllRepeatedlyThenStops() {
        AtomicInteger ticks = new AtomicInteger();
        SyntheticLiveRunService service = mock(SyntheticLiveRunService.class);
        doAnswer(inv -> {
            ticks.incrementAndGet();
            return null;
        }).when(service).tickAll();

        SyntheticLivePacer pacer = new SyntheticLivePacer(service, 20L); // 20ms interval
        pacer.start();
        // At least 3 ticks fire within the timeout.
        verify(service, timeout(2000).atLeast(3)).tickAll();
        pacer.stop();

        int afterStop = ticks.get();
        // No further ticks after stop(): count stays put over a short window.
        await().pollDelay(Duration.ofMillis(150)).atMost(Duration.ofMillis(500))
                .until(() -> ticks.get() == afterStop);
    }
}
