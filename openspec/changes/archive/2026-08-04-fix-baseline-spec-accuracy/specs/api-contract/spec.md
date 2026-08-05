## ADDED Requirements

### Requirement: Application liveness and readiness come from Actuator
Process-level liveness/readiness SHALL be exposed via Spring Boot Actuator
(`/actuator/health/**`), reachable without authentication in both deployment
modes. There are deliberately no bespoke `/healthz` / `/readyz` endpoints.
Per-data-source health is a separate, authenticated API concern
(`/api/v1/data-sources/{id}/health`) and SHALL NOT be conflated with process
health.

#### Scenario: Orchestrator probes Actuator, not a bespoke path
- **WHEN** a container orchestrator probes the application for liveness
- **THEN** it uses `/actuator/health` and succeeds without a bearer token

#### Scenario: Per-source health is not process health
- **WHEN** one data source reports an `ERROR` runtime state
- **THEN** `/actuator/health` still reports the application UP, because a
  single source's health is not the process's health

### Requirement: No settings API surface yet
Project-scoped and environment-scoped settings are NOT exposed over the API:
there are no `/projects/{id}/settings` or `/environment/settings` endpoints,
even though backing tables exist (see the `db-schema` capability).
Environment-level configuration SHALL continue to come from environment
variables / external config (see the `auth-modes` capability) until a change
introduces that surface.

#### Scenario: Settings are configured out-of-band
- **WHEN** an operator needs to change environment-level configuration
- **THEN** they change the deployment's environment/external config, not an
  API call
