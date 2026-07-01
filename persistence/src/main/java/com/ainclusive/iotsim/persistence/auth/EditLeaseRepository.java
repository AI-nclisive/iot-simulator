package com.ainclusive.iotsim.persistence.auth;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Advisory edit-lease management over the {@code edit_leases} table (D4, 08_AUTH_AND_MODES.md).
 *
 * <p>A lease is advisory UX protection; the {@code version} optimistic-concurrency check
 * remains the authoritative guard against lost updates.  Leases expire automatically so a
 * crashed/abandoned session self-recovers (stale-lock recovery).
 */
public interface EditLeaseRepository {

    /**
     * Acquires (or renews) an edit lease.  If no lease exists for
     * {@code (objectType, objectId)}, one is inserted.  If a lease exists and is held by
     * {@code holder}, it is renewed (expires_at extended).  If a lease exists but is held
     * by a different user and has <em>not</em> yet expired, the existing lease is returned
     * unchanged and the caller must check whether it is theirs.
     *
     * @return the current lease (new, renewed, or conflicting)
     */
    EditLeaseRow acquireOrRenew(String objectType, String objectId, String holder, Duration ttl);

    Optional<EditLeaseRow> findActive(String objectType, String objectId);

    List<EditLeaseRow> findAllActiveByHolder(String holder);

    /** Releases the lease held by {@code holder}.  No-op if the lease is held by someone else. */
    boolean release(String objectType, String objectId, String holder);

    /** Deletes all leases that have already expired.  Called by a scheduled cleanup job. */
    int deleteExpired();
}
