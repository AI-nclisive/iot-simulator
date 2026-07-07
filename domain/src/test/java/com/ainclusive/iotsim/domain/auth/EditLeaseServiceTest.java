package com.ainclusive.iotsim.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.persistence.auth.EditLeaseRepository;
import com.ainclusive.iotsim.persistence.auth.EditLeaseRow;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EditLeaseService}. */
class EditLeaseServiceTest {

    private static final String OBJECT_TYPE = EditLeaseService.TYPE_DATA_SOURCE;
    private static final String OBJECT_ID = "ds-42";
    private static final String HOLDER = "alice";
    private static final String OTHER = "bob";

    private EditLeaseRepository repository;
    private EditLeaseService service;

    @BeforeEach
    void setUp() {
        repository = mock(EditLeaseRepository.class);
        // TTL default: 300 seconds
        service = new EditLeaseService(repository, 300L);
    }

    // ── acquire ──────────────────────────────────────────────────────────────────

    @Test
    void acquire_delegatesToRepositoryWithConfiguredTtl() {
        EditLeaseRow row = row(HOLDER);
        given(repository.acquireOrRenew(eq(OBJECT_TYPE), eq(OBJECT_ID), eq(HOLDER), any(Duration.class)))
                .willReturn(row);

        EditLease result = service.acquire(OBJECT_TYPE, OBJECT_ID, HOLDER);

        assertThat(result.objectType()).isEqualTo(row.objectType());
        assertThat(result.objectId()).isEqualTo(row.objectId());
        assertThat(result.holder()).isEqualTo(row.holder());
        verify(repository).acquireOrRenew(OBJECT_TYPE, OBJECT_ID, HOLDER, Duration.ofSeconds(300));
    }

    @Test
    void acquire_returnsConflictingLeaseWhenHeldByOther() {
        EditLeaseRow conflictingRow = row(OTHER);
        given(repository.acquireOrRenew(any(), any(), eq(HOLDER), any(Duration.class)))
                .willReturn(conflictingRow);

        EditLease result = service.acquire(OBJECT_TYPE, OBJECT_ID, HOLDER);

        // The conflicting lease is returned as-is; caller must check holder
        assertThat(result.holder()).isEqualTo(OTHER);
    }

    // ── release ───────────────────────────────────────────────────────────────────

    @Test
    void release_delegatesToRepositoryAndReturnsTrue() {
        given(repository.release(OBJECT_TYPE, OBJECT_ID, HOLDER)).willReturn(true);

        boolean released = service.release(OBJECT_TYPE, OBJECT_ID, HOLDER);

        assertThat(released).isTrue();
        verify(repository).release(OBJECT_TYPE, OBJECT_ID, HOLDER);
    }

    @Test
    void release_returnsFalseWhenLeaseNotHeld() {
        given(repository.release(OBJECT_TYPE, OBJECT_ID, HOLDER)).willReturn(false);

        boolean released = service.release(OBJECT_TYPE, OBJECT_ID, HOLDER);

        assertThat(released).isFalse();
    }

    // ── findActive ────────────────────────────────────────────────────────────────

    @Test
    void findActive_returnsLeaseFromRepository() {
        EditLeaseRow lease = row(HOLDER);
        given(repository.findActive(OBJECT_TYPE, OBJECT_ID)).willReturn(Optional.of(lease));

        Optional<EditLease> result = service.findActive(OBJECT_TYPE, OBJECT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().holder()).isEqualTo(HOLDER);
    }

    @Test
    void findActive_returnsEmptyWhenNoActiveLease() {
        given(repository.findActive(OBJECT_TYPE, OBJECT_ID)).willReturn(Optional.empty());

        Optional<EditLease> result = service.findActive(OBJECT_TYPE, OBJECT_ID);

        assertThat(result).isEmpty();
    }

    // ── isHeldByOther ─────────────────────────────────────────────────────────────

    @Test
    void isHeldByOther_returnsFalseWhenNoActiveLease() {
        given(repository.findActive(OBJECT_TYPE, OBJECT_ID)).willReturn(Optional.empty());

        assertThat(service.isHeldByOther(OBJECT_TYPE, OBJECT_ID, HOLDER)).isFalse();
    }

    @Test
    void isHeldByOther_returnsFalseWhenLeaseHeldByRequestingUser() {
        given(repository.findActive(OBJECT_TYPE, OBJECT_ID)).willReturn(Optional.of(row(HOLDER)));

        assertThat(service.isHeldByOther(OBJECT_TYPE, OBJECT_ID, HOLDER)).isFalse();
    }

    @Test
    void isHeldByOther_returnsTrueWhenLeaseHeldByDifferentUser() {
        given(repository.findActive(OBJECT_TYPE, OBJECT_ID)).willReturn(Optional.of(row(OTHER)));

        assertThat(service.isHeldByOther(OBJECT_TYPE, OBJECT_ID, HOLDER)).isTrue();
    }

    // ── cleanupExpired ────────────────────────────────────────────────────────────

    @Test
    void cleanupExpired_delegatesToRepositoryAndReturnsCount() {
        given(repository.deleteExpired()).willReturn(3);

        int deleted = service.cleanupExpired();

        assertThat(deleted).isEqualTo(3);
        verify(repository).deleteExpired();
    }

    @Test
    void cleanupExpired_returnsZeroWhenNothingToDelete() {
        given(repository.deleteExpired()).willReturn(0);

        int deleted = service.cleanupExpired();

        assertThat(deleted).isEqualTo(0);
    }

    // ── object type constants ─────────────────────────────────────────────────────

    @Test
    void objectTypeConstants_haveExpectedValues() {
        assertThat(EditLeaseService.TYPE_DATA_SOURCE).isEqualTo("data-source");
        assertThat(EditLeaseService.TYPE_SCENARIO).isEqualTo("scenario");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static EditLeaseRow row(String holder) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new EditLeaseRow(OBJECT_TYPE, OBJECT_ID, holder, now, now.plusMinutes(5));
    }
}
