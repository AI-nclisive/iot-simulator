package com.ainclusive.iotsim.app.auth;

import com.ainclusive.iotsim.persistence.auth.EditLeaseRepository;
import com.ainclusive.iotsim.persistence.auth.EditLeaseRow;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Advisory edit-lease service (D4, 08_AUTH_AND_MODES.md).
 *
 * <p>When a user opens an editor (e.g. Full Schema Editor, Scenario Builder), this service
 * grants a time-bounded lease so other users see a read-only view while the lease is active.
 * Leases expire automatically (stale-lock recovery); a scheduled cleanup purges expired rows.
 *
 * <p>The lease is advisory UX protection only; the {@code version} optimistic-concurrency
 * check remains the authoritative guard against lost updates.
 */
@Service
public class EditLeaseService {

    /** Object type constant for data-source leases. */
    public static final String TYPE_DATA_SOURCE = "data-source";

    /** Object type constant for scenario leases. */
    public static final String TYPE_SCENARIO = "scenario";

    private final EditLeaseRepository repository;
    private final Duration ttl;

    public EditLeaseService(
            EditLeaseRepository repository,
            @Value("${app.edit-lease.ttl-seconds:300}") long ttlSeconds) {
        this.repository = repository;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Acquires (or renews) an edit lease for the given object on behalf of {@code holder}.
     *
     * <p>If no active lease exists, a new lease is granted. If the holder already owns
     * the lease, it is renewed (expiry extended). If a different user holds an active
     * lease, that existing lease is returned unchanged; the caller should check
     * {@link #isHeldByOther} to detect the conflict.
     *
     * @param objectType one of {@link #TYPE_DATA_SOURCE} or {@link #TYPE_SCENARIO}
     * @param objectId   the identifier of the object to lock
     * @param holder     the identity of the requesting user
     * @return the current lease (new, renewed, or conflicting)
     */
    public EditLeaseRow acquire(String objectType, String objectId, String holder) {
        return repository.acquireOrRenew(objectType, objectId, holder, ttl);
    }

    /**
     * Releases the lease held by {@code holder} for the given object.
     *
     * <p>No-op if no lease exists or the lease is held by a different user.
     *
     * @return {@code true} if the lease was released, {@code false} if it was not held
     */
    public boolean release(String objectType, String objectId, String holder) {
        return repository.release(objectType, objectId, holder);
    }

    /**
     * Returns the current active lease for the given object, if one exists.
     *
     * @return an {@link Optional} containing the active lease, or empty if none
     */
    public Optional<EditLeaseRow> findActive(String objectType, String objectId) {
        return repository.findActive(objectType, objectId);
    }

    /**
     * Returns {@code true} if an active lease exists for the given object and is held
     * by a user other than {@code holder}.
     *
     * <p>Use this to decide whether to present a read-only view to the requesting user.
     *
     * @param objectType one of {@link #TYPE_DATA_SOURCE} or {@link #TYPE_SCENARIO}
     * @param objectId   the identifier of the object to check
     * @param holder     the identity of the requesting user
     * @return {@code true} if a different user currently holds the lease
     */
    public boolean isHeldByOther(String objectType, String objectId, String holder) {
        return repository.findActive(objectType, objectId)
                .map(lease -> !lease.holder().equals(holder))
                .orElse(false);
    }

    /**
     * Deletes all expired leases from the database.
     *
     * <p>Scheduled to run every 60 seconds to ensure stale leases from crashed or
     * abandoned sessions are recovered promptly.
     *
     * @return the number of expired leases deleted
     */
    @Scheduled(fixedDelay = 60_000)
    public int cleanupExpired() {
        return repository.deleteExpired();
    }
}
