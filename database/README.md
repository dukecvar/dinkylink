# database

Flyway migration scripts for the Dinky Link Postgres schema.

## Layout

- `migrations/` — versioned SQL migrations, named `V<version>__<description>.sql`
  per Flyway convention. Applied in order by the `flyway` service in
  `local/docker-compose.yaml`; never applied by the backend itself
  (`spring.flyway.enabled=false` in `backend/api`).

## Adding a migration

Add a new `V<next-version>__<description>.sql` file to `migrations/`, then
run `docker compose up -d flyway` from `local/` to apply it. Never edit an
already-applied migration file — add a new one instead.
