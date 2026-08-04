## MODIFIED Requirements

### Requirement: Authorization is permission-based with role-to-permission mapping
Authorization SHALL be checked against fine-grained permissions (e.g.
`source.start`, `source.configure`, `recording.export`, `admin.access`), with
roles mapping to permission sets. Externally, only `admin` and `user` roles
are exposed today; `user` can observe everything (including evidence) and
operate runtime — start/stop a capture, a replay, or a scenario run, and stop
a running data source — but cannot edit, import/export, or manage access;
`admin` can do everything `user` can plus edit projects/data-sources/schemas/
scenarios, import/export, manage retention, and manage access. Enforcement
SHALL live in the API layer; local mode grants the implicit principal the full
permission set. Note that "starting a source" is never a bare action: the
`source.start` permission gates starting a specific runtime action (capture or
simulate), matching the endpoint surface in the `api-contract` capability.

#### Scenario: A user role cannot edit a schema
- **WHEN** a principal with the `user` role attempts to edit a data source's
  schema
- **THEN** the API rejects the request as unauthorized

#### Scenario: A user role can start a capture
- **WHEN** a principal with the `user` role starts a recording on a data
  source
- **THEN** the request is authorized by `source.start`, because operating
  runtime is within the `user` role's scope
