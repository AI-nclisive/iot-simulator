package com.ainclusive.iotsim.persistence.timeline;

import static com.ainclusive.iotsim.persistence.jooq.tables.Recordings.RECORDINGS;
import static com.ainclusive.iotsim.persistence.jooq.tables.SchemaNodes.SCHEMA_NODES;
import static com.ainclusive.iotsim.persistence.jooq.tables.Schemas.SCHEMAS;
import static com.ainclusive.iotsim.persistence.jooq.tables.ValueTimeline.VALUE_TIMELINE;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.max;

import com.ainclusive.iotsim.persistence.jooq.tables.records.ValueTimelineRecord;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.protocolmodel.ValueFilter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqValueTimelineRepository implements ValueTimelineRepository {

    private final DSLContext dsl;

    public JooqValueTimelineRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public long append(String recordingId, List<NeutralValue> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();
            Long maxSeq = tx.select(max(VALUE_TIMELINE.SEQ))
                    .from(VALUE_TIMELINE)
                    .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                    .fetchOne(0, Long.class);
            long seq = (maxSeq == null ? -1L : maxSeq) + 1;
            for (NeutralValue v : values) {
                ValueCodec.Encoded enc = ValueCodec.encode(v.value());
                tx.insertInto(VALUE_TIMELINE)
                        .set(VALUE_TIMELINE.RECORDING_ID, recordingId)
                        .set(VALUE_TIMELINE.NODE_ID, v.nodeId())
                        .set(VALUE_TIMELINE.SOURCE_TIME, v.sourceTime().atOffset(ZoneOffset.UTC))
                        .set(VALUE_TIMELINE.SEQ, seq++)
                        .set(VALUE_TIMELINE.VALUE_KIND, enc.kind().name())
                        .set(VALUE_TIMELINE.VALUE_ENC, enc.bytes())
                        .set(VALUE_TIMELINE.QUALITY, v.quality().name())
                        .set(VALUE_TIMELINE.QUALITY_REASON, v.qualityReason())
                        .execute();
            }
            return (long) values.size();
        });
    }

    @Override
    public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
        return dsl.selectFrom(VALUE_TIMELINE)
                .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                .and(VALUE_TIMELINE.SOURCE_TIME.between(
                        from.atOffset(ZoneOffset.UTC), to.atOffset(ZoneOffset.UTC)))
                .orderBy(VALUE_TIMELINE.SOURCE_TIME, VALUE_TIMELINE.SEQ)
                .fetch()
                .map(this::toValue);
    }

    @Override
    public List<NeutralValue> readAll(String recordingId) {
        return dsl.selectFrom(VALUE_TIMELINE)
                .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                .orderBy(VALUE_TIMELINE.SOURCE_TIME, VALUE_TIMELINE.SEQ)
                .fetch()
                .map(this::toValue);
    }

    @Override
    public long count(String recordingId) {
        Integer total = dsl.selectCount()
                .from(VALUE_TIMELINE)
                .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                .fetchOne(0, Integer.class);
        return total == null ? 0 : total;
    }

    @Override
    public long sumBytes(String recordingId) {
        Long total = dsl.select(DSL.sum(DSL.function(
                        "octet_length", Integer.class, VALUE_TIMELINE.VALUE_ENC)))
                .from(VALUE_TIMELINE)
                .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                .fetchOne(0, Long.class);
        return total == null ? 0 : total;
    }

    @Override
    public void deleteByRecording(String recordingId) {
        dsl.deleteFrom(VALUE_TIMELINE).where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId)).execute();
    }

    @Override
    public List<ValueTimelineEntry> readPage(
            String recordingId, long afterSeq, int limit, ValueFilter filter) {
        return dsl
                .select(VALUE_TIMELINE.SEQ, SCHEMA_NODES.PATH,
                        VALUE_TIMELINE.NODE_ID, VALUE_TIMELINE.SOURCE_TIME,
                        VALUE_TIMELINE.VALUE_KIND, VALUE_TIMELINE.VALUE_ENC,
                        VALUE_TIMELINE.QUALITY, VALUE_TIMELINE.QUALITY_REASON)
                .from(VALUE_TIMELINE)
                .join(RECORDINGS).on(RECORDINGS.ID.eq(VALUE_TIMELINE.RECORDING_ID))
                .join(SCHEMAS).on(SCHEMAS.DATA_SOURCE_ID.eq(RECORDINGS.DATA_SOURCE_ID)
                        .and(SCHEMAS.VERSION.eq(RECORDINGS.SCHEMA_VERSION)))
                .leftJoin(SCHEMA_NODES).on(SCHEMA_NODES.SCHEMA_ID.eq(SCHEMAS.ID)
                        .and(SCHEMA_NODES.NODE_ID.eq(VALUE_TIMELINE.NODE_ID)))
                .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                .and(VALUE_TIMELINE.SEQ.greaterThan(afterSeq))
                .and(buildFilterConditions(filter))
                .orderBy(VALUE_TIMELINE.SEQ)
                .limit(limit)
                .fetch()
                .map(r -> new ValueTimelineEntry(
                        r.get(VALUE_TIMELINE.SEQ),
                        r.get(SCHEMA_NODES.PATH),
                        new NeutralValue(
                                r.get(VALUE_TIMELINE.NODE_ID),
                                r.get(VALUE_TIMELINE.SOURCE_TIME).toInstant(),
                                ValueCodec.decode(
                                        ValueCodec.Kind.valueOf(r.get(VALUE_TIMELINE.VALUE_KIND)),
                                        r.get(VALUE_TIMELINE.VALUE_ENC)),
                                Quality.valueOf(r.get(VALUE_TIMELINE.QUALITY)),
                                r.get(VALUE_TIMELINE.QUALITY_REASON))));
    }

    @Override
    public long countFiltered(String recordingId, ValueFilter filter) {
        Integer total = dsl
                .selectCount()
                .from(VALUE_TIMELINE)
                .join(RECORDINGS).on(RECORDINGS.ID.eq(VALUE_TIMELINE.RECORDING_ID))
                .join(SCHEMAS).on(SCHEMAS.DATA_SOURCE_ID.eq(RECORDINGS.DATA_SOURCE_ID)
                        .and(SCHEMAS.VERSION.eq(RECORDINGS.SCHEMA_VERSION)))
                .leftJoin(SCHEMA_NODES).on(SCHEMA_NODES.SCHEMA_ID.eq(SCHEMAS.ID)
                        .and(SCHEMA_NODES.NODE_ID.eq(VALUE_TIMELINE.NODE_ID)))
                .where(VALUE_TIMELINE.RECORDING_ID.eq(recordingId))
                .and(buildFilterConditions(filter))
                .fetchOne(0, Integer.class);
        return total == null ? 0 : total;
    }

    private Condition buildFilterConditions(ValueFilter filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.search() != null && !filter.search().isBlank()) {
            String escaped = filter.search().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            conditions.add(
                    coalesce(SCHEMA_NODES.PATH, VALUE_TIMELINE.NODE_ID)
                            .likeIgnoreCase(pattern, '\\'));
        }
        if (!filter.qualities().isEmpty()) {
            List<String> names = filter.qualities().stream().map(Enum::name).toList();
            conditions.add(VALUE_TIMELINE.QUALITY.in(names));
        }
        if (filter.from() != null) {
            conditions.add(VALUE_TIMELINE.SOURCE_TIME.greaterOrEqual(
                    filter.from().atOffset(ZoneOffset.UTC)));
        }
        if (filter.to() != null) {
            conditions.add(VALUE_TIMELINE.SOURCE_TIME.lessOrEqual(
                    filter.to().atOffset(ZoneOffset.UTC)));
        }
        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    private NeutralValue toValue(ValueTimelineRecord r) {
        ValueCodec.Kind kind = ValueCodec.Kind.valueOf(r.getValueKind());
        Object value = ValueCodec.decode(kind, r.getValueEnc());
        return new NeutralValue(
                r.getNodeId(),
                r.getSourceTime().toInstant(),
                value,
                Quality.valueOf(r.getQuality()),
                r.getQualityReason());
    }
}
