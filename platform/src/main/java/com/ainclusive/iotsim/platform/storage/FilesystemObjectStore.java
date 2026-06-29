package com.ainclusive.iotsim.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * Filesystem {@link ObjectStore} adapter — the local (default) blob store
 * (decision D3, {@code backend-specs/08_AUTH_AND_MODES.md}). Objects are written
 * as files under a configured root directory; the returned reference is the
 * object's key relative to that root, so a stored ref keeps resolving across
 * restarts.
 *
 * <p>Keys are treated as forward-slash relative paths (e.g.
 * {@code evidence/run-42/bundle.zip}); they may contain subdirectories, which are
 * created on write. Path traversal outside the root (e.g. {@code ../}, absolute
 * paths) is rejected so a key can never escape the store.
 *
 * <p>The declared {@code contentType} is not persisted — the filesystem has no
 * native place for it and {@link #get} returns only bytes; callers that need the
 * media type carry it in their own metadata (e.g. the {@code evidence} row).
 */
public final class FilesystemObjectStore implements ObjectStore {

    private final Path root;

    /**
     * Creates a store rooted at {@code root}, creating the directory tree if it does
     * not yet exist.
     *
     * @throws UncheckedIOException if the root directory cannot be created
     */
    public FilesystemObjectStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create object-store root " + this.root, e);
        }
    }

    @Override
    public String put(String key, InputStream content, long sizeBytes, String contentType) {
        Objects.requireNonNull(content, "content");
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Write to a sibling temp file then move into place, so a reader never
            // observes a half-written object and a failed write leaves no partial file.
            Path tmp = Files.createTempFile(target.getParent(), ".put-", ".tmp");
            try {
                Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
                moveIntoPlace(tmp, target);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store object " + key, e);
        }
        return relativeRef(target);
    }

    @Override
    public Optional<InputStream> get(String ref) {
        Path target = resolve(ref);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open object " + ref, e);
        }
    }

    @Override
    public boolean delete(String ref) {
        try {
            return Files.deleteIfExists(resolve(ref));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete object " + ref, e);
        }
    }

    private void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicUnsupported) {
            // Not every filesystem supports an atomic replace; fall back to a plain move.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Resolves a key/ref to an absolute path, rejecting anything that escapes the root. */
    private Path resolve(String keyOrRef) {
        if (keyOrRef == null || keyOrRef.isBlank()) {
            throw new IllegalArgumentException("object key must not be blank");
        }
        Path resolved = root.resolve(keyOrRef).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("object key escapes the store root: " + keyOrRef);
        }
        return resolved;
    }

    /** The stored reference: the object's path relative to the root, with forward slashes. */
    private String relativeRef(Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }
}
