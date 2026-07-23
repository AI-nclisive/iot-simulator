package com.ainclusive.iotsim.persistence.timeline;

import static com.ainclusive.iotsim.persistence.jooq.tables.RunValueTimeline.RUN_VALUE_TIMELINE;
import static org.jooq.impl.DSL.max;

import com.ainclusive.iotsim.persistence.jooq.tables.records.RunValueTimelineRecord;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqRunValueTimelineRepository implements RunValueTimelineRepository {

    private final DSLContext dsl;

    public JooqRunValueTimelineRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public long append(String runId, List<NeutralValue> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();
            Long maxSeq = tx.select(max(RUN_VALUE_TIMELINE.SEQ))
                    .from(RUN_VALUE_TIMELINE)
                    .where(RUN_VALUE_TIMELINE.RUN_ID.eq(runId))
                    .fetchOne(0, Long.class);
            long seq = (maxSeq == null ? -1L : maxSeq) + 1;
            for (NeutralValue v : values) {
                ValueCodec.Encoded enc = ValueCodec.encode(v.value());
                tx.insertInto(RUN_VALUE_TIMELINE)
                        .set(RUN_VALUE_TIMELINE.RUN_ID, runId)
                        .set(RUN_VALUE_TIMELINE.NODE_ID, v.nodeId())
                        .set(RUN_VALUE_TIMELINE.SOURCE_TIME, v.sourceTime().atOffset(ZoneOffset.UTC))
                        .set(RUN_VALUE_TIMELINE.SEQ, seq++)
                        .set(RUN_VALUE_TIMELINE.VALUE_KIND, enc.kind().name())
                        .set(RUN_VALUE_TIMELINE.VALUE_ENC, enc.bytes())
                        .set(RUN_VALUE_TIMELINE.QUALITY, v.quality().name())
                        .set(RUN_VALUE_TIMELINE.QUALITY_REASON, v.qualityReason())
                        .execute();
            }
            return (long) values.size();
        });
    }

    @Override
    public List<NeutralValue> readAll(String runId) {
        return dsl.selectFrom(RUN_VALUE_TIMELINE)
                .where(RUN_VALUE_TIMELINE.RUN_ID.eq(runId))
                .orderBy(RUN_VALUE_TIMELINE.SOURCE_TIME, RUN_VALUE_TIMELINE.SEQ)
                .fetch()
                .map(this::toValue);
    }

    @Override
    public void deleteByRun(String runId) {
        dsl.deleteFrom(RUN_VALUE_TIMELINE).where(RUN_VALUE_TIMELINE.RUN_ID.eq(runId)).execute();
    }

    private NeutralValue toValue(RunValueTimelineRecord r) {
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
