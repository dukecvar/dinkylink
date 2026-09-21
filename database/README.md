# database

Flyway migration scripts for the Dinky Link Postgres schema.

## Layout

- `migrations/` — versioned SQL migrations, named `V<version>__<description>.sql`
  per Flyway convention. Applied in order by the `flyway` service in
  `local/docker-compose.yaml`; never applied by the backend itself
  (`spring.flyway.enabled=false` in `backend/api`).

- `tests/` — plain SQL integration tests for schema objects (functions,
  procedures) that aren't exercised by the backend yet. Not run
  automatically; see below.

## Adding a migration

Add a new `V<next-version>__<description>.sql` file to `migrations/`, then
run `docker compose up -d flyway` from `local/` to apply it. Never edit an
already-applied migration file — add a new one instead.

## Shortcode generation

`V2__add_shortcode_generation.sql` adds two routines used to create
shortcodes for new records:

- `create_short_hash(urlhash BYTEA, salt INTEGER) RETURNS CHAR(8)` — hashes
  `(urlhash, salt)` with `hashtextextended` and encodes it as an 8-character
  base62 string (`[0-9A-Za-z]`). Deterministic; no table access.
- `insert_record(urlhash BYTEA, url VARCHAR, max_attempts INTEGER DEFAULT 100) RETURNS CHAR(8)`
  — generates a shortcode via `create_short_hash` starting at salt `0` and
  attempts to insert `(shortcode, urlhash, url)`. On a shortcode collision
  (unique violation), increments the salt and retries; raises once
  `max_attempts` is exhausted. Does not check for an existing `urlhash` —
  callers are expected to have already checked the cache/read replica
  before calling it.

Run the integration tests for these against a migrated database:
```bash
docker exec -i local-postgres-1 psql -U dinkylink -d dinkylink \
  -v ON_ERROR_STOP=1 < database/tests/test_shortcode_generation.sql
```
The script runs inside a transaction that's rolled back at the end, so no
test data is left behind.
