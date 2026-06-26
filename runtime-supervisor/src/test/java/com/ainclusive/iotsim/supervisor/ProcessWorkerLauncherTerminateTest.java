package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ProcessBuilder.Redirect;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Teardown must kill the worker's whole process tree, not just the launcher wrapper
 * (IS-114). The real worker JVM runs as a <em>descendant</em> of the wrapper script
 * (e.g. {@code worker-opcua.bat} → {@code cmd.exe} → JVM), so destroying only the
 * direct child orphans the worker — and on Windows that orphan can keep an inherited
 * pipe open and stall the spawning JVM.
 */
class ProcessWorkerLauncherTerminateTest {

    @Test
    @Timeout(30) // terminate() can block ~5s grace + await() up to 10s; fail fast if wedged
    void terminateKillsDescendantProcesses() throws Exception {
        // A shell that waits on a long-lived child, mirroring the wrapper → worker
        // indirection. The child is reparented (not killed) when the shell dies, so
        // terminate() must reap it explicitly. IO is discarded so a stray child can
        // never couple to the Gradle test pipe.
        Process wrapper = new ProcessBuilder(treeCommand())
                .redirectOutput(Redirect.DISCARD)
                .redirectError(Redirect.DISCARD)
                .start();
        try {
            await(() -> wrapper.descendants().findAny().isPresent());
            List<ProcessHandle> descendants = wrapper.descendants().toList();
            assertThat(descendants).as("wrapper should have spawned a child").isNotEmpty();

            ProcessWorkerLauncher.terminate(wrapper);

            assertThat(wrapper.isAlive()).as("wrapper should be killed").isFalse();
            for (ProcessHandle child : descendants) {
                await(() -> !child.isAlive());
                assertThat(child.isAlive())
                        .as("descendant " + child.pid() + " should be killed, not orphaned")
                        .isFalse();
            }
        } finally {
            // Belt-and-braces: never leak a process even if an assertion above fails.
            wrapper.descendants().forEach(ProcessHandle::destroyForcibly);
            wrapper.destroyForcibly();
        }
    }

    /** A wrapper process that stays alive holding a long-lived child of its own. */
    private static List<String> treeCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            // cmd stays and waits on ping (~60s of loopback pings); ping is the child.
            return List.of("cmd.exe", "/c", "ping -n 60 127.0.0.1 > NUL");
        }
        // Background the child then wait, so sh does not exec-replace itself with sleep
        // (which would leave no descendant to reap).
        return List.of("sh", "-c", "sleep 60 & wait");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as("condition not met within timeout").isTrue();
    }
}
