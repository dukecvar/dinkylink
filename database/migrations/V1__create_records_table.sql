-- docs/design.md specifies a 128-bit (16-byte) urlhash and a plain
-- `binary(128)` column; Postgres has no fixed-length binary type, so
-- this uses bytea sized for a 16-byte hash in practice.
CREATE TABLE records (
    shortcode              CHAR(8)       PRIMARY KEY,
    urlhash                BYTEA         NOT NULL,
    url                    VARCHAR(2048) NOT NULL,
    last_touched_timestamp TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_records_urlhash ON records (urlhash);
