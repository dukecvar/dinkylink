-- Base62 alphabet used to render hashes as shortcodes: [0-9A-Za-z].
-- create_short_hash hashes (salt, urlhash) with hashtextextended, reduces it
-- into the 62^8 value space, and left-pads it to exactly 8 characters to
-- match the `records.shortcode` CHAR(8) column.
CREATE OR REPLACE FUNCTION create_short_hash(p_urlhash BYTEA, p_salt INTEGER)
RETURNS CHAR(8)
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    c_alphabet   CONSTANT TEXT := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
    c_base       CONSTANT BIGINT := 62;
    c_value_space CONSTANT BIGINT := 62 ^ 8; -- 218,340,105,584,896
    v_value      BIGINT;
    v_result     TEXT := '';
BEGIN
    v_value := abs(hashtextextended(encode(p_urlhash, 'hex'), p_salt::BIGINT)) % c_value_space;

    IF v_value = 0 THEN
        v_result := '0';
    END IF;
    WHILE v_value > 0 LOOP
        v_result := substr(c_alphabet, (v_value % c_base)::INTEGER + 1, 1) || v_result;
        v_value := v_value / c_base;
    END LOOP;

    RETURN lpad(v_result, 8, '0');
END;
$$;

-- insert_record generates a shortcode for (urlhash, url) and inserts it,
-- retrying with an incremented salt whenever the shortcode collides with an
-- existing row. Does not check for an existing urlhash; callers are
-- expected to have already checked cache/read-replica for an existing
-- record before calling this. Raises once p_max_attempts is exhausted.
CREATE OR REPLACE FUNCTION insert_record(p_urlhash BYTEA, p_url VARCHAR(2048), p_max_attempts INTEGER DEFAULT 100)
RETURNS CHAR(8)
LANGUAGE plpgsql
AS $$
DECLARE
    v_salt_itr  INTEGER := 0;
    v_shortcode CHAR(8);
BEGIN
    LOOP
        IF v_salt_itr >= p_max_attempts THEN
            RAISE EXCEPTION 'insert_record: exhausted % attempts generating a unique shortcode for urlhash %',
                p_max_attempts, encode(p_urlhash, 'hex');
        END IF;

        v_shortcode := create_short_hash(p_urlhash, v_salt_itr);
        BEGIN
            INSERT INTO records (shortcode, urlhash, url)
            VALUES (v_shortcode, p_urlhash, p_url);
            RETURN v_shortcode;
        EXCEPTION WHEN unique_violation THEN
            v_salt_itr := v_salt_itr + 1;
        END;
    END LOOP;
END;
$$;
