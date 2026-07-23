package com.ainclusive.iotsim.domain.run;

import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventQuery;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared in-memory {@link RuntimeEventRepository} fake for unit tests (IS-182) — records every
 * appended event so tests can assert a {@code RUN_COMPLETED}/{@code RUN_STOPPED}/
 * {@code RUN_FAILED} event was recorded on a run's terminal transition.
 */
public final class FakeRuntimeEventRepository implements RuntimeEventRepository {

    public final List<RuntimeEventRow> appended = new ArrayList<>();
    private long seq;

    @Override
    public RuntimeEventRow append(String projectId, String dataSourceId, String runId,
            String type, OffsetDateTime at, String payloadJson) {
        RuntimeEventRow row = new RuntimeEventRow(++seq, projectId, dataSourceId, runId, type, at, payloadJson);
        appended.add(row);
        return row;
    }

    @Override
    public Optional<RuntimeEventRow> findById(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RuntimeEventRow> findByProject(String projectId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RuntimeEventRow> findByRun(String runId) {
        return appended.stream().filter(r -> runId.equals(r.runId())).toList();
    }

    @Override
    public List<RuntimeEventRow> findByDataSource(String dataSourceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RuntimeEventRow> query(RuntimeEventQuery filter) {
        throw new UnsupportedOperationException();
    }
}
