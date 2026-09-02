## ADDED Requirements

### Requirement: Manual schemas can be instantiated from supported built-in profiles

The API SHALL provide `POST /api/v1/projects/{projectId}/manual-schemas/from-template`
for an authorized schema editor to create a standalone manual schema from a
supported built-in profile. The request SHALL provide the stable template key
and a schema name. The created response SHALL be `201`, include its `Location`
and ETag, and retain every explicit protocol binding supplied by that profile.
Unsupported template keys SHALL be rejected with `400` without creating a
schema.

#### Scenario: SunSpec profile creates a bound Modbus manual schema

- **WHEN** an authorized client posts the SunSpec inverter template key with a
  schema name
- **THEN** the response creates a `MODBUS_TCP` manual schema whose variables
  retain their explicit SunSpec holding-register bindings

#### Scenario: Unsupported profile is rejected

- **WHEN** a client posts an unknown template key
- **THEN** the API returns `400` and no manual schema is created
