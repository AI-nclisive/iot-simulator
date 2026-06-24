package com.ainclusive.iotsim.persistence.timeline;

import static com.ainclusive.iotsim.persistence.jooq.tables.ValueTimeline.VALUE_TIMELINE;
import static org.jooq.impl.DSL.max;

import com.ainclusive.iotsim.persistence.jooq.tables.records.ValueTimelineRecord;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.DSLContext;
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
