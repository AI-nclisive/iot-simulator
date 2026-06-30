package com.ainclusive.iotsim.persistence.sample;

import java.util.List;
import java.util.Optional;

/** Stores named sample subsets/snapshots. */
public interface SampleRepository {
    SampleRow create(String projectId, String derivedFromRecordingId, String name,
            String selection, String tags, String createdBy);
    Optional<SampleRow> findById(String id);
    List<SampleRow> findByProject(String projectId);
    boolean deleteById(String id);
}
