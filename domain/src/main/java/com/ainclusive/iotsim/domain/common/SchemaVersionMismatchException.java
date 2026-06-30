package com.ainclusive.iotsim.domain.common;

/** Replay requested against a data source whose current schema version differs from the recording. */
public class SchemaVersionMismatchException extends RuntimeException {

    public SchemaVersionMismatchException(String recordingId, int recordingVersion, int currentVersion) {
        super("Recording " + recordingId + " was captured at schema version " + recordingVersion
                + " but data source is now at version " + currentVersion
                + "; set compatibilityAck=true to proceed despite the schema drift");
    }
}
