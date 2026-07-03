package com.ainclusive.iotsim.persistence.recording;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.JooqDataSourceRepository;
import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import com.ainclusive.iotsim.persistence.schema.JooqSchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.timeline.JooqValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository.ValueTimelineEntry;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.Quality;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueFilter;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
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

    static DSLContext dsl;
    static RecordingRepository recordings;
    static ValueTimelineRepository timeline;
    static String projectId;
    static String dataSourceId;
    static int schemaVersion;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        projectId = new JooqProjectRepository(dsl).insert("Plant", null, "it").id();
        dataSourceId = new JooqDataSourceRepository(dsl)
                .insert(projectId, "Pump", "OPC_UA", "MANUAL", 4840, null, null, null, "it").id();
        recordings = new JooqRecordingRepository(dsl);
        timeline = new JooqValueTimelineRepository(dsl);
        SchemaRepository schemas = new JooqSchemaRepository(dsl);
        List<SchemaNode> nodes = List.of(
                new SchemaNode("temp", null, "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null),
                new SchemaNode("humidity", null, "Plant/Humidity", "Humidity",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", null));
        schemaVersion = schemas.saveNewVersion(dataSourceId, nodes).version();
    }

    @Test
    void captureAndReplayTimeline() {
        RecordingRow recording = recordings.create(projectId, dataSourceId, 1, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");

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

    @Test
    void findByProjectPagedReturnsBatchNewestFirst() {
        RecordingRow a = recordings.create(projectId, dataSourceId, 1, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");
        RecordingRow b = recordings.create(projectId, dataSourceId, 1, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");
        RecordingRow c = recordings.create(projectId, dataSourceId, 1, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");

        List<RecordingRow> page1 = recordings.findByProjectPaged(projectId, null, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        RecordingRow last = page1.get(page1.size() - 1);
        List<RecordingRow> page2 = recordings.findByProjectPaged(projectId, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(RecordingRow::id).contains(a.id());
        assertThat(page2).extracting(RecordingRow::id).doesNotContain(b.id(), c.id());
    }

    /** IS-136: search filter matches on schema node path. */
    @Test
    void readPageFilterBySearchMatchesPath() {
        RecordingRow rec = recordings.create(projectId, dataSourceId, schemaVersion, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");
        Instant t = Instant.parse("2026-06-01T10:00:00Z");
        timeline.append(rec.id(), List.of(
                NeutralValue.good("temp", t, 22.0),
                NeutralValue.good("humidity", t.plusSeconds(1), 55.0)));

        List<ValueTimelineEntry> matches = timeline.readPage(rec.id(), -1, 10,
                new ValueFilter("Temp", List.of(), null, null));
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).parameterPath()).isEqualTo("Plant/Temp");

        List<ValueTimelineEntry> noMatches = timeline.readPage(rec.id(), -1, 10,
                new ValueFilter("Pressure", List.of(), null, null));
        assertThat(noMatches).isEmpty();
    }

    /** IS-136: quality filter returns only rows with the given quality. */
    @Test
    void readPageFilterByQualityReturnsMatchingRows() {
        RecordingRow rec = recordings.create(projectId, dataSourceId, schemaVersion, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");
        Instant t = Instant.parse("2026-06-01T11:00:00Z");
        timeline.append(rec.id(), List.of(
                NeutralValue.good("temp", t, 20.0),
                new NeutralValue("temp", t.plusSeconds(1), 21.0, Quality.UNCERTAIN, null),
                new NeutralValue("temp", t.plusSeconds(2), 22.0, Quality.BAD, null)));

        List<ValueTimelineEntry> goodOnly = timeline.readPage(rec.id(), -1, 10,
                new ValueFilter(null, List.of(Quality.GOOD), null, null));
        assertThat(goodOnly).hasSize(1);
        assertThat(goodOnly.get(0).value().quality()).isEqualTo(Quality.GOOD);

        List<ValueTimelineEntry> badAndUncertain = timeline.readPage(rec.id(), -1, 10,
                new ValueFilter(null, List.of(Quality.BAD, Quality.UNCERTAIN), null, null));
        assertThat(badAndUncertain).hasSize(2);
    }

    /** IS-136: from/to time range filter restricts returned rows. */
    @Test
    void readPageFilterByTimeRangeReturnsRowsInRange() {
        RecordingRow rec = recordings.create(projectId, dataSourceId, schemaVersion, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        timeline.append(rec.id(), List.of(
                NeutralValue.good("temp", base, 1.0),
                NeutralValue.good("temp", base.plusSeconds(5), 2.0),
                NeutralValue.good("temp", base.plusSeconds(10), 3.0)));

        List<ValueTimelineEntry> inRange = timeline.readPage(rec.id(), -1, 10,
                new ValueFilter(null, List.of(), base.plusSeconds(3), base.plusSeconds(7)));
        assertThat(inRange).hasSize(1);
        assertThat(inRange.get(0).value().value()).isEqualTo(2.0);
    }

    /** IS-136: countFiltered returns accurate filtered total. */
    @Test
    void countFilteredReturnsAccurateTotal() {
        RecordingRow rec = recordings.create(projectId, dataSourceId, schemaVersion, "SCAN_RECORD", "SCHEMA_AND_DATA", "it");
        Instant t = Instant.parse("2026-06-01T13:00:00Z");
        timeline.append(rec.id(), List.of(
                NeutralValue.good("temp", t, 10.0),
                NeutralValue.good("temp", t.plusSeconds(1), 11.0),
                new NeutralValue("humidity", t.plusSeconds(2), 50.0, Quality.BAD, null)));

        long goodCount = timeline.countFiltered(rec.id(),
                new ValueFilter(null, List.of(Quality.GOOD), null, null));
        assertThat(goodCount).isEqualTo(2);

        long tempCount = timeline.countFiltered(rec.id(),
                new ValueFilter("Temp", List.of(), null, null));
        assertThat(tempCount).isEqualTo(2);
    }

    /** IS-093: value_timeline is range-partitioned by source_time with a DEFAULT partition. */
    @Test
    void valueTimelineIsRangePartitioned() {
        // pg_partitioned_table.partstrat = 'r' => RANGE partitioning is in effect.
        String strategy = dsl.resultQuery(
                "select partstrat::text from pg_partitioned_table p "
                        + "join pg_class c on c.oid = p.partrelid where c.relname = 'value_timeline'")
                .fetchOne(0, String.class);
        assertThat(strategy).isEqualTo("r");

        // A DEFAULT partition exists so appends never fail before a monthly partition is added.
        Boolean hasDefault = dsl.resultQuery(
                "select exists (select 1 from pg_class where relname = 'value_timeline_default')")
                .fetchOne(0, Boolean.class);
        assertThat(hasDefault).isTrue();
    }
}
