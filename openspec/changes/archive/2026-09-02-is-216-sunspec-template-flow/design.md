## Context

`ModbusTemplates` is the authoritative Java catalog. Copying SunSpec's register
map into TypeScript would create two sources of truth and risk losing explicit
address bindings.

## Decisions

1. The API accepts a stable template key and a user-supplied schema name at
   `POST /api/v1/projects/{projectId}/manual-schemas/from-template`.
2. The domain service resolves the key and persists the catalog nodes through
   the ordinary manual-schema creation path. Unknown template keys are rejected
   as validation errors.
3. The Manual Schemas dialog exposes the SunSpec option only for `MODBUS_TCP`.
   Selecting it invokes the new endpoint and opens the ordinary editor for the
   resulting independent schema.

## Risks

The endpoint is intentionally narrow: it exposes only templates the service
can materialize today. Additional profiles can be added by extending the
server-side catalog, without a frontend register-map copy.
