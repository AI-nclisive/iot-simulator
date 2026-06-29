package com.ainclusive.iotsim.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemObjectStoreTest {

    @TempDir
    Path root;

    private FilesystemObjectStore store() {
        return new FilesystemObjectStore(root);
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void putThenGetRoundTrips() throws IOException {
        FilesystemObjectStore store = store();
        String ref = store.put("bundle.zip", bytes("hello"), 5, "application/zip");

        assertThat(ref).isEqualTo("bundle.zip");
        assertThat(store.get(ref)).isPresent();
        assertThat(read(store.get(ref).orElseThrow())).isEqualTo("hello");
    }

    @Test
    void createsRootDirectoryIfMissing() {
        Path nested = root.resolve("a/b/store");
        assertThat(Files.exists(nested)).isFalse();

        new FilesystemObjectStore(nested).put("x", bytes("v"), 1, "text/plain");

        assertThat(Files.isDirectory(nested)).isTrue();
    }

    @Test
    void keyWithSubdirectoriesCreatesParents() throws IOException {
        FilesystemObjectStore store = store();
        String ref = store.put("evidence/run-42/bundle.zip", bytes("data"), 4, "application/zip");

        assertThat(ref).isEqualTo("evidence/run-42/bundle.zip");
        assertThat(read(store.get(ref).orElseThrow())).isEqualTo("data");
        assertThat(Files.isRegularFile(root.resolve("evidence/run-42/bundle.zip"))).isTrue();
    }

    @Test
    void putOverwritesExistingObject() throws IOException {
        FilesystemObjectStore store = store();
        store.put("k", bytes("first"), 5, "text/plain");
        store.put("k", bytes("second"), 6, "text/plain");

        assertThat(read(store.get("k").orElseThrow())).isEqualTo("second");
    }

    @Test
    void getMissingObjectIsEmpty() {
        assertThat(store().get("nope")).isEmpty();
    }

    @Test
    void deleteRemovesObjectAndReportsIt() {
        FilesystemObjectStore store = store();
        store.put("k", bytes("v"), 1, "text/plain");

        assertThat(store.delete("k")).isTrue();
        assertThat(store.get("k")).isEmpty();
    }

    @Test
    void deleteMissingObjectReturnsFalse() {
        assertThat(store().delete("nope")).isFalse();
    }

    @Test
    void rejectsKeysThatEscapeTheRoot() {
        FilesystemObjectStore store = store();
        assertThatThrownBy(() -> store.put("../escape", bytes("v"), 1, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete("a/../../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankKey() {
        FilesystemObjectStore store = store();
        assertThatThrownBy(() -> store.put("  ", bytes("v"), 1, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedPutLeavesNoPartialFileBehind() {
        FilesystemObjectStore store = store();
        InputStream boom = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("stream blew up");
            }
        };

        assertThatThrownBy(() -> store.put("partial", boom, 10, "text/plain"))
                .isInstanceOf(RuntimeException.class);
        assertThat(store.get("partial")).isEmpty();
        // No leftover temp files in the root either.
        assertThat(root.toFile().list()).noneMatch(n -> n.startsWith(".put-"));
    }
}
