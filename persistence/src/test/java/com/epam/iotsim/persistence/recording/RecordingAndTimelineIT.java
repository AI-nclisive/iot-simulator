package com.epam.iotsim.persistence.recording;

import static org.assertj.core.api.Assertions.assertThat;

import com.epam.iotsim.persistence.datasource.JooqDataSourceRepository;
import com.epam.iotsim.persistence.project.JooqProjectRepository;
import com.epam.iotsim.persistence.timeline.JooqValueTimelineRepository;
import com.epam.iotsim.persistence.timeline.ValueTimelineRepository;
import com.epam.iotsim.protocolmodel.NeutralValue;
import com.epam.iotsim.protocolmodel.Quality;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Recording metadata + append-optimized value timeline against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
class RecordingAndTimelineIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static RecordingRepository recordings;
    static ValueTimelineRepository timeline;
    static String projectId;
    static String dataSourceId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        projectId = new JooqProjectRepository(dsl).insert("Plant", null, "it").id();
        dataSourceId = new JooqDataSourceRepository(dsl)
                .insert(projectId, "Pump", "OPC_UA", "MANUAL", null, null, "it").id();
        recordings = new JooqRecordingRepository(dsl);
        timeline = new JooqValueTimelineRepository(dsl);
    }

    @Test
    void captureAndReplayTimeline() {
        RecordingRow recording = recordings.create(projectId, dataSourceId, 1, "SCAN_RECORD", "it");

        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long written = timeline.append(recording.id(), List.of(
                NeutralValue.good("temp", t, 21.5),
                NeutralValue.good("temp", t.plusMillis(10), 21.7),
                new NeutralValue("flag", t.plusMillis(20), true, Quality.UNCERTAIN, "STALE")));
        assertThat(written).isEqualTo(3);
        assertThat(timeline.count(recording.id())).isEqualTo(3);

        List<NeutralValue> range = timeline.readRange(
                recording.id(), t.minusSeconds(1), t.plusSeconds(1));
        assertThat(range).hasSize(3);
        assertThat(range.get(0).nodeId()).isEqualTo("temp");
        assertThat(range.get(0).value()).isEqualTo(21.5);
        assertThat(range.get(2).value()).isEqualTo(true);
        assertThat(range.get(2).quality()).isEqualTo(Quality.UNCERTAIN);
        assertThat(range.get(2).qualityReason()).isEqualTo("STALE");

        RecordingRow finalized = recordings.finalizeStats(
                recording.id(), t.atOffset(ZoneOffset.UTC), t.plusMillis(20).atOffset(ZoneOffset.UTC),
                timeline.count(recording.id()), 0L);
        assertThat(finalized.valueCount()).isEqualTo(3);
    }
}
