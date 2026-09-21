-- Integration tests for create_short_hash() and insert_record(), added by
-- V2__add_shortcode_generation.sql. Everything runs inside one transaction
-- that is rolled back at the end, so the test data never persists.
--
-- Run against a migrated database:
--   docker exec -i local-postgres-1 psql -U dinkylink -d dinkylink \
--     -v ON_ERROR_STOP=1 < database/tests/test_shortcode_generation.sql

BEGIN;

-- create_short_hash: always returns exactly 8 base62 characters.
DO $$
DECLARE
    v_hash BYTEA := decode('000102030405060708090a0b0c0d0e0f', 'hex');
    v_code CHAR(8);
BEGIN
    v_code := create_short_hash(v_hash, 0);
    IF v_code IS NULL OR v_code !~ '^[0-9A-Za-z]{8}$' THEN
        RAISE EXCEPTION 'FAIL: expected 8 base62 chars, got %', v_code;
    END IF;
    RAISE NOTICE 'PASS: create_short_hash returns 8 base62 chars (%)', v_code;
END $$;

-- create_short_hash: deterministic for the same urlhash + salt.
DO $$
DECLARE
    v_hash BYTEA := decode('101112131415161718191a1b1c1d1e1f', 'hex');
BEGIN
    IF create_short_hash(v_hash, 3) IS DISTINCT FROM create_short_hash(v_hash, 3) THEN
        RAISE EXCEPTION 'FAIL: create_short_hash is not deterministic for the same inputs';
    END IF;
    RAISE NOTICE 'PASS: create_short_hash is deterministic';
END $$;

-- create_short_hash: different salt values produce different codes.
DO $$
DECLARE
    v_hash BYTEA := decode('202122232425262728292a2b2c2d2e2f', 'hex');
BEGIN
    IF create_short_hash(v_hash, 0) = create_short_hash(v_hash, 1) THEN
        RAISE EXCEPTION 'FAIL: expected different salts to change the generated code';
    END IF;
    RAISE NOTICE 'PASS: different salts produce different codes';
END $$;

-- insert_record: inserts a row and returns its shortcode.
DO $$
DECLARE
    v_hash BYTEA := decode('303132333435363738393a3b3c3d3e3f', 'hex');
    v_url  VARCHAR(2048) := 'https://example.com/insert-record-basic';
    v_code CHAR(8);
    v_row  records%ROWTYPE;
BEGIN
    v_code := insert_record(v_hash, v_url);

    SELECT * INTO v_row FROM records WHERE shortcode = v_code;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'FAIL: no record found for returned shortcode %', v_code;
    END IF;
    IF v_row.urlhash IS DISTINCT FROM v_hash OR v_row.url IS DISTINCT FROM v_url THEN
        RAISE EXCEPTION 'FAIL: inserted record does not match input (urlhash=%, url=%)', v_row.urlhash, v_row.url;
    END IF;
    RAISE NOTICE 'PASS: insert_record inserts a matching row (%)', v_code;
END $$;

-- insert_record: retries with an incremented salt on a shortcode collision.
DO $$
DECLARE
    v_hash        BYTEA := decode('404142434445464748494a4b4c4d4e4f', 'hex');
    v_url         VARCHAR(2048) := 'https://example.com/insert-record-collision';
    v_salt0_code  CHAR(8) := create_short_hash(v_hash, 0);
    v_salt1_code  CHAR(8) := create_short_hash(v_hash, 1);
    v_returned    CHAR(8);
BEGIN
    -- Occupy the salt=0 shortcode with an unrelated record first.
    INSERT INTO records (shortcode, urlhash, url)
    VALUES (v_salt0_code, decode('ffeeddccbbaa99887766554433221100', 'hex'), 'https://example.com/occupant');

    v_returned := insert_record(v_hash, v_url);

    IF v_returned IS DISTINCT FROM v_salt1_code THEN
        RAISE EXCEPTION 'FAIL: expected collision retry to land on salt=1 code %, got %', v_salt1_code, v_returned;
    END IF;
    RAISE NOTICE 'PASS: insert_record retries past a collision to salt=1 (%)', v_returned;
END $$;

-- insert_record: raises once the attempt cap is exhausted.
DO $$
DECLARE
    v_hash BYTEA := decode('505152535455565758595a5b5c5d5e5f', 'hex');
    v_raised BOOLEAN := FALSE;
BEGIN
    -- Occupy both codes that the first two attempts (salt 0 and 1) would use.
    INSERT INTO records (shortcode, urlhash, url)
    VALUES (create_short_hash(v_hash, 0), decode('aa000000000000000000000000000000', 'hex'), 'https://example.com/occupant-0');
    INSERT INTO records (shortcode, urlhash, url)
    VALUES (create_short_hash(v_hash, 1), decode('bb000000000000000000000000000000', 'hex'), 'https://example.com/occupant-1');

    BEGIN
        PERFORM insert_record(v_hash, 'https://example.com/insert-record-exhausted', 2);
    EXCEPTION WHEN OTHERS THEN
        v_raised := TRUE;
    END;

    IF NOT v_raised THEN
        RAISE EXCEPTION 'FAIL: expected insert_record to raise once max attempts were exhausted';
    END IF;
    RAISE NOTICE 'PASS: insert_record raises once max attempts are exhausted';
END $$;

ROLLBACK;
