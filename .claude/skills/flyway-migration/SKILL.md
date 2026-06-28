---
name: flyway-migration
description: >-
  Create a new Flyway database migration with a collision-safe version, the way
  this repo's parallel-work rules require. Use whenever you need a DB schema
  change (new table/column/index/constraint). Invoke as
  `/flyway-migration add_scenario_tags`.
---

# New Flyway migration

Owning rules: `CONTRIBUTING.md` → "Parallel-work conventions" (IS-109). Migrations
are **append-only** and live in `persistence/src/main/resources/db/migration`
(current latest: `V6__auth_and_leases.sql`).

**Input:** a short snake_case description from the argument (e.g.
`add_scenario_tags`). If none, ask.

## 1. Pick a collision-safe version

Many branches run in parallel, so a plain `V7__…` can collide with another open PR
that also grabbed `V7` — and **a version is never reused**. Default to a
**timestamped** version, which can't collide:

```
V<YYYYMMDD>_<HHMM>__<snake_description>.sql      e.g. V20260628_1432__add_scenario_tags.sql
```

Use a plain sequential `V7__…` only if you are certain no open PR claims it
(`git fetch && git log --all --oneline -- 'persistence/src/main/resources/db/migration'`,
and check open PRs). When unsure, timestamp it.

## 2. Create the file

Write the SQL to `persistence/src/main/resources/db/migration/<version>__<name>.sql`.
Keep it PostgreSQL-portable — **no required PG extensions** (per `STACK.md`: must run
on managed Postgres like RDS/Cloud SQL). No engine-specific features assumed.

## 3. Append-only — never edit an applied migration

Don't modify an existing `V*` file that may already be applied anywhere; correct it
with a **new** migration. Flyway applies pending migrations on app startup and in the
Testcontainers ITs.

## 4. Regenerate jOOQ if types changed

If the change alters tables/columns the typed jOOQ code reads, regenerate:

```bash
./gradlew :persistence:generateJooq    # needs Docker; runs off the default build
```

Generated jOOQ code stays under `build/` and is **never committed** (`CONTRIBUTING.md`
→ DoD). Reference the new types from `persistence`/`domain` as usual.

## 5. Build & verify

`./gradlew build` green (ITs run the migration against Testcontainers Postgres) before
finishing ([[always-compile-and-test]]). If Docker is unavailable locally the ITs skip
— say so explicitly; CI always runs them.
