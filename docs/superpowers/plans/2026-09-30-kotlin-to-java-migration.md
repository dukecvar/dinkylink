# Kotlin to Java Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port `backend/api` from Kotlin to Java with zero behavior change, verified by the existing test suite passing after conversion.

**Architecture:** Convert class-by-class in dependency order, deleting each Kotlin file as its Java replacement lands so the module never has two definitions of the same type. `Record`, `RecordRepository`, `RecordService`, and `RecordController` must convert together in one task — `Record` becoming a Java record changes its accessor-method names (`shortcode()` instead of a bean-style `getShortcode()`), which breaks any still-Kotlin caller's property-access syntax (`record.shortcode`), so no Kotlin file may reference `Record` after it converts. The Gradle build file and project docs convert last, once no `.kt` file remains.

**Tech Stack:** Java 25 (toolchain already pinned), Spring Boot 4.1.1, Spring Data JDBC, Spring Data Redis, JUnit 5, AssertJ, Mockito, Gradle (Groovy DSL after this migration).

**Spec:** `docs/superpowers/specs/2026-09-30-kotlin-to-java-migration-design.md`

## Global Constraints

- Module root: `backend/api` (standalone Gradle build, `rootProject.name = 'api'`; `backend/workers` and `backend/api-tests` are untouched — no code there).
- Base package: `dev.dukecvar.dinkylink.api` (unchanged).
- No functional change: same endpoints, same JSON shapes, same cache keys, same DB query, same HTTP statuses.
- No null-safety annotations (`@Nullable` etc.) are introduced — out of scope per spec.
- `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` and all non-Kotlin dependency versions (`spring-boot-starter-*` via the Spring Boot BOM at `4.1.1`, `io.spring.dependency-management` at `1.1.7`, `com.google.guava:guava:33.4.8-jre`) carry over unchanged.
- Test assertions use AssertJ (`org.assertj.core.api.Assertions.assertThat`); test method names become camelCase with `@DisplayName("<original sentence>")` preserving the original backtick-named sentence.
- Every task ends with `./gradlew build` and `./gradlew test` passing (Postgres + Redis must be running via `cd local && docker compose up -d`).

## Review Focus

- **Whole-`Record` equality in tests/code silently breaks.** `Record`'s generated `equals()` compares the `byte[] urlhash` component by reference (same pitfall Kotlin's data class had — the original test defensively converted to `.toList()` to work around it). A reviewer could "simplify" a test to `assertThat(retrieved).isEqualTo(added)` and get a spurious failure. Task 3's `RecordServiceTest` pins this by asserting fields individually, with `urlhash` compared via `assertThat(retrieved.urlhash()).isEqualTo(added.urlhash())` (AssertJ's array overload of `isEqualTo` *is* content-based, unlike the record's own `equals()`).
- **Jackson's native record deserialization of a JSON body that omits the `url` key entirely** (not just a blank string) is currently untested — the existing suite only covers `"url": "  "`. Once `jackson-module-kotlin` is removed, Jackson's built-in record support handles missing reference-type components by defaulting to `null`, same as before, but nothing proves it. Task 3 adds `rejectsARequestBodyMissingTheUrlFieldWith400` to `RecordControllerTest` posting `{}`.
- **AssertJ isn't actually guaranteed to be on the test classpath.** The project doesn't use the umbrella `spring-boot-starter-test` (it uses the modular `-actuator-test`/`-data-jdbc-test`/etc. starters), so AssertJ may not be pulled in transitively. Task 1 resolves this empirically (try without the dependency first; add `testImplementation("org.assertj:assertj-core")` if the test won't compile) rather than assuming either way.
- **Leftover Kotlin after the "migration" is done** — a stray `.kt` file, an empty `src/main/kotlin` directory, or the Kotlin stdlib still resolving on the classpath — isn't exercised by any unit test. Task 5 verifies directly: `find backend/api/src -name '*.kt'` must be empty, and `./gradlew dependencies` must show no `kotlin-stdlib`/`kotlin-reflect` entries.
- **`CreateShortUrlResponse`/`CreateShortUrlErrorResponse` JSON field names must stay `shortURL`/`url`/`error` exactly** — Java records serialize component names as-is via Jackson's native record support, same as Kotlin's data class properties did; the existing `content().json(...)` assertions in `RecordControllerTest` (carried over in Task 3) already pin this for every response shape.

---

## Task 1: Convert `UrlHasher`

**Files:**
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/UrlHasher.java`
- Create: `backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/UrlHasherTest.java`
- Delete: `backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/UrlHasher.kt`
- Delete: `backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/record/UrlHasherTest.kt`
- Modify: `backend/api/build.gradle.kts` (add an AssertJ test dependency, still in Kotlin DSL syntax — the whole file converts to Groovy in Task 5)

**Interfaces:**
- Produces: `@Component public class UrlHasher { public byte[] hash(String url) }` in package `dev.dukecvar.dinkylink.api.record` — every later task's `RecordService` conversion depends on this exact signature.

- [ ] **Step 1: Delete the Kotlin files**

Delete `UrlHasher.kt` and `UrlHasherTest.kt`.

- [ ] **Step 2: Confirm the build now fails**

Run: `cd backend/api && ./gradlew compileKotlin`
Expected: FAIL — `RecordService.kt` has an unresolved reference to `UrlHasher`.

- [ ] **Step 3: Write the Java test**

```java
package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlHasherTest {

    private final UrlHasher hasher = new UrlHasher();

    @Test
    @DisplayName("hashes to 16 bytes")
    void hashesTo16Bytes() {
        assertThat(hasher.hash("https://example.com")).hasSize(16);
    }

    @Test
    @DisplayName("same url hashes deterministically")
    void sameUrlHashesDeterministically() {
        String url = "https://example.com/a";
        assertThat(hasher.hash(url)).isEqualTo(hasher.hash(url));
    }

    @Test
    @DisplayName("different urls hash differently")
    void differentUrlsHashDifferently() {
        assertThat(hasher.hash("https://example.com/a"))
            .isNotEqualTo(hasher.hash("https://example.com/b"));
    }
}
```

- [ ] **Step 4: Try compiling as-is; add AssertJ only if needed**

Run: `cd backend/api && ./gradlew compileTestJava`
If it fails because `org.assertj.core.api.Assertions` can't be resolved, add
`testImplementation("org.assertj:assertj-core")` to the `dependencies {}`
block of `build.gradle.kts`, then re-run. Record which outcome occurred —
Task 5 needs to know whether the dependency is explicit or transitive.

- [ ] **Step 5: Run the new test and confirm it fails (production class missing)**

Run: `cd backend/api && ./gradlew test --tests "dev.dukecvar.dinkylink.api.record.UrlHasherTest"`
Expected: FAIL — `UrlHasher` does not exist in `src/main/java`.

- [ ] **Step 6: Implement `UrlHasher.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import com.google.common.hash.Hashing;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class UrlHasher {
    public byte[] hash(String url) {
        return Hashing.murmur3_128().hashString(url, StandardCharsets.UTF_8).asBytes();
    }
}
```

- [ ] **Step 7: Run the full test suite**

Run: `cd backend/api && ./gradlew test`
Expected: PASS (requires `cd local && docker compose up -d` already running).

- [ ] **Step 8: Commit**

```bash
git add backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/UrlHasher.java \
        backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/UrlHasherTest.java \
        backend/api/build.gradle.kts
git rm backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/UrlHasher.kt \
       backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/record/UrlHasherTest.kt
git commit -m "refactor: port UrlHasher to Java"
```

---

## Task 2: Convert `RecordCache`

**Files:**
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordCache.java`
- Create: `backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/RecordCacheTest.java`
- Delete: `backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/RecordCache.kt`
- Delete: `backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/record/RecordCacheTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `@Component public class RecordCache { public String getShortcode(byte[] urlhash); public void putShortcode(byte[] urlhash, String shortcode); public String getUrl(String shortcode); public void putUrl(String shortcode, String url); }` — Task 3's `RecordService` depends on these four exact signatures. Cache key formats (`"shortcodes:" + hex`, `"urls:" + shortcode`) and the 365-day TTL are exact values from the spec and must not change.

- [ ] **Step 1: Delete the Kotlin files**

Delete `RecordCache.kt` and `RecordCacheTest.kt`.

- [ ] **Step 2: Confirm the build now fails**

Run: `cd backend/api && ./gradlew compileKotlin`
Expected: FAIL — `RecordService.kt` has an unresolved reference to `RecordCache`.

- [ ] **Step 3: Write the Java test**

```java
package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecordCacheTest {

    @Autowired
    private RecordCache recordCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<String> usedKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (!usedKeys.isEmpty()) {
            redisTemplate.delete(usedKeys);
        }
        usedKeys.clear();
    }

    private byte[] randomUrlhash() {
        return UUID.randomUUID().toString().getBytes();
    }

    @Test
    @DisplayName("stores and retrieves a shortcode by urlhash")
    void storesAndRetrievesAShortcodeByUrlhash() {
        byte[] urlhash = randomUrlhash();
        usedKeys.add("shortcodes:" + HexFormat.of().formatHex(urlhash));

        recordCache.putShortcode(urlhash, "abc12345");

        assertThat(recordCache.getShortcode(urlhash)).isEqualTo("abc12345");
    }

    @Test
    @DisplayName("returns null for an unknown urlhash")
    void returnsNullForAnUnknownUrlhash() {
        assertThat(recordCache.getShortcode(randomUrlhash())).isNull();
    }

    @Test
    @DisplayName("stores and retrieves a url by shortcode")
    void storesAndRetrievesAUrlByShortcode() {
        String shortcode = UUID.randomUUID().toString().substring(0, 8);
        usedKeys.add("urls:" + shortcode);

        recordCache.putUrl(shortcode, "https://example.com/foo");

        assertThat(recordCache.getUrl(shortcode)).isEqualTo("https://example.com/foo");
    }

    @Test
    @DisplayName("returns null for an unknown shortcode")
    void returnsNullForAnUnknownShortcode() {
        assertThat(recordCache.getUrl(UUID.randomUUID().toString().substring(0, 8))).isNull();
    }

    @Test
    @DisplayName("refreshes the url TTL on a cache hit")
    void refreshesTheUrlTtlOnACacheHit() {
        String shortcode = UUID.randomUUID().toString().substring(0, 8);
        String key = "urls:" + shortcode;
        usedKeys.add(key);

        recordCache.putUrl(shortcode, "https://example.com/foo");
        redisTemplate.expire(key, Duration.ofSeconds(5));

        recordCache.getUrl(shortcode);

        Long remaining = redisTemplate.getExpire(key, TimeUnit.DAYS);
        assertThat(remaining).as("expected TTL to be refreshed close to 365 days, was %d days", remaining).isGreaterThan(300);
    }
}
```

- [ ] **Step 4: Run the new test and confirm it fails (production class missing)**

Run: `cd backend/api && ./gradlew test --tests "dev.dukecvar.dinkylink.api.record.RecordCacheTest"`
Expected: FAIL — `RecordCache` does not exist in `src/main/java`.

- [ ] **Step 5: Implement `RecordCache.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class RecordCache {
    private static final Duration TTL = Duration.ofDays(365);

    private final StringRedisTemplate redisTemplate;

    public RecordCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getShortcode(byte[] urlhash) {
        return get(shortcodeKey(urlhash));
    }

    public void putShortcode(byte[] urlhash, String shortcode) {
        put(shortcodeKey(urlhash), shortcode);
    }

    public String getUrl(String shortcode) {
        return get(urlKey(shortcode));
    }

    public void putUrl(String shortcode, String url) {
        put(urlKey(shortcode), url);
    }

    private String get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            redisTemplate.expire(key, TTL);
        }
        return value;
    }

    private void put(String key, String value) {
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    private String shortcodeKey(byte[] urlhash) {
        return "shortcodes:" + HexFormat.of().formatHex(urlhash);
    }

    private String urlKey(String shortcode) {
        return "urls:" + shortcode;
    }
}
```

- [ ] **Step 6: Run the full test suite**

Run: `cd backend/api && ./gradlew test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordCache.java \
        backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/RecordCacheTest.java
git rm backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/RecordCache.kt \
       backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/record/RecordCacheTest.kt
git commit -m "refactor: port RecordCache to Java"
```

---

## Task 3: Convert `Record`, `RecordRepository`, `RecordService`, `RecordController`

These four must convert in one task: once `Record` becomes a Java
`record`, its accessors are `shortcode()`/`url()` (no `get` prefix), not
Kotlin-bean-getter style. Any Kotlin file still doing property access on
a `Record` instance (`record.shortcode`, `record.url`) stops compiling
the moment `Record` converts. Only `RecordService.kt` and
`RecordController.kt` do that — so they, plus `RecordRepository` (which
both depend on), convert alongside `Record` in this same task, in that
dependency order.

**Files:**
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/Record.java`
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordRepository.java`
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordService.java`
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordController.java` (also defines package-private records `CreateShortUrlRequest`, `CreateShortUrlResponse`, `CreateShortUrlErrorResponse` in the same file, matching how the Kotlin file grouped them)
- Create: `backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/RecordServiceTest.java`
- Create: `backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/RecordControllerTest.java`
- Delete: the four corresponding `.kt` main files and two `.kt` test files in `src/main/kotlin/.../record/` and `src/test/kotlin/.../record/`

**Interfaces:**
- Consumes: `UrlHasher.hash(String)` (Task 1), `RecordCache.{getShortcode,putShortcode,getUrl,putUrl}` (Task 2).
- Produces: `public record Record(String shortcode, byte[] urlhash, String url, OffsetDateTime lastTouchedTimestamp)`; `public interface RecordRepository extends CrudRepository<Record, String> { String insertRecord(byte[] urlhash, String url); }`; `@Service public class RecordService { public Record addRecord(String url); public Record getRecord(String shortcode); public String resolveUrl(String shortcode); }`; `@RestController public class RecordController` with `POST /` and `GET /{shortcode}` unchanged. Task 4 (`DinkyLinkApiApplication`) and Task 5 (build file) don't depend on these signatures directly, but Task 5's full-suite run re-verifies them.

- [ ] **Step 1: Delete the four Kotlin main files and two Kotlin test files**

Delete `Record.kt`, `RecordRepository.kt`, `RecordService.kt`, `RecordController.kt`, `RecordServiceTest.kt`, `RecordControllerTest.kt`.

- [ ] **Step 2: Confirm the build now fails**

Run: `cd backend/api && ./gradlew compileKotlin`
Expected: FAIL (or trivially succeed with nothing left to compile in `src/main/kotlin` — in which case confirm instead with `./gradlew compileJava`, which fails because nothing defines `Record`/`RecordRepository`/`RecordService`/`RecordController` yet).

- [ ] **Step 3: Write `RecordServiceTest.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RecordServiceTest {

    @Autowired
    private RecordService recordService;

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private UrlHasher urlHasher;

    private String uniqueUrl() {
        return "https://example.com/" + UUID.randomUUID();
    }

    @Test
    @DisplayName("adds and retrieves a record by shortcode")
    void addsAndRetrievesARecordByShortcode() {
        String url = uniqueUrl();

        Record added = recordService.addRecord(url);
        assertThat(added.shortcode()).hasSize(8);
        assertThat(added.url()).isEqualTo(url);

        Record retrieved = recordService.getRecord(added.shortcode());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.shortcode()).isEqualTo(added.shortcode());
        assertThat(retrieved.url()).isEqualTo(url);
        assertThat(retrieved.urlhash()).isEqualTo(added.urlhash());
    }

    @Test
    @DisplayName("retrieving an unknown shortcode returns null")
    void retrievingAnUnknownShortcodeReturnsNull() {
        assertThat(recordService.getRecord("00000000")).isNull();
    }

    @Test
    @DisplayName("adding the same URL twice returns the same shortcode")
    void addingTheSameUrlTwiceReturnsTheSameShortcode() {
        String url = uniqueUrl();

        Record first = recordService.addRecord(url);
        Record second = recordService.addRecord(url);

        assertThat(second.shortcode()).isEqualTo(first.shortcode());
    }

    @Test
    @DisplayName("resolveUrl returns the url for a freshly added record")
    void resolveUrlReturnsTheUrlForAFreshlyAddedRecord() {
        String url = uniqueUrl();
        Record added = recordService.addRecord(url);

        assertThat(recordService.resolveUrl(added.shortcode())).isEqualTo(url);
    }

    @Test
    @DisplayName("resolveUrl falls back to the database when the url isn't cached")
    void resolveUrlFallsBackToTheDatabaseWhenTheUrlIsNotCached() {
        String url = uniqueUrl();
        byte[] urlhash = urlHasher.hash(url);
        String shortcode = recordRepository.insertRecord(urlhash, url);

        assertThat(recordService.resolveUrl(shortcode)).isEqualTo(url);
    }

    @Test
    @DisplayName("resolveUrl returns null for an unknown shortcode")
    void resolveUrlReturnsNullForAnUnknownShortcode() {
        assertThat(recordService.resolveUrl("00000000")).isNull();
    }
}
```

- [ ] **Step 4: Write `RecordControllerTest.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecordController.class)
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordService recordService;

    @Test
    @DisplayName("creates a short url and returns 201")
    void createsAShortUrlAndReturns201() throws Exception {
        String url = "https://example.com/some/long/path";
        Record record = new Record("abc12345", new byte[16], url, OffsetDateTime.now());
        when(recordService.addRecord(url)).thenReturn(record);

        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{\"url\":\"" + url + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(content().json("{\"url\":\"" + url + "\",\"shortURL\":\"http://localhost:8080/abc12345\"}"));
    }

    @Test
    @DisplayName("rejects a blank url with 400")
    void rejectsABlankUrlWith400() throws Exception {
        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{\"url\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"url\":\"  \",\"error\":\"url must not be blank\"}"));

        verifyNoInteractions(recordService);
    }

    @Test
    @DisplayName("rejects a request body missing the url field with 400")
    void rejectsARequestBodyMissingTheUrlFieldWith400() throws Exception {
        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"url\":null,\"error\":\"url must not be blank\"}"));

        verifyNoInteractions(recordService);
    }

    @Test
    @DisplayName("rejects a url longer than 2048 characters with 400")
    void rejectsAUrlLongerThan2048CharactersWith400() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(2048);

        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{\"url\":\"" + tooLong + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"url\":\"" + tooLong + "\",\"error\":\"url must be 2048 characters or fewer\"}"));

        verifyNoInteractions(recordService);
    }

    @Test
    @DisplayName("redirects to the original url with 300 when the shortcode is found")
    void redirectsToTheOriginalUrlWith300WhenTheShortcodeIsFound() throws Exception {
        when(recordService.resolveUrl("abc12345")).thenReturn("https://example.com/target");

        mockMvc.perform(get("/abc12345"))
            .andExpect(status().isMultipleChoices())
            .andExpect(header().string("Location", "https://example.com/target"));
    }

    @Test
    @DisplayName("returns 404 when the shortcode is not found")
    void returns404WhenTheShortcodeIsNotFound() throws Exception {
        when(recordService.resolveUrl("00000000")).thenReturn(null);

        mockMvc.perform(get("/00000000"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 5: Run both new tests and confirm they fail (production classes missing)**

Run: `cd backend/api && ./gradlew test --tests "dev.dukecvar.dinkylink.api.record.RecordServiceTest" --tests "dev.dukecvar.dinkylink.api.record.RecordControllerTest"`
Expected: FAIL to compile — none of `Record`/`RecordRepository`/`RecordService`/`RecordController` exist in `src/main/java` yet.

- [ ] **Step 6: Implement `Record.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Table("records")
public record Record(
    @Id String shortcode,
    byte[] urlhash,
    String url,
    OffsetDateTime lastTouchedTimestamp
) {}
```

- [ ] **Step 7: Implement `RecordRepository.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RecordRepository extends CrudRepository<Record, String> {
    @Query("SELECT insert_record(:urlhash, :url)")
    String insertRecord(@Param("urlhash") byte[] urlhash, @Param("url") String url);
}
```

- [ ] **Step 8: Implement `RecordService.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.springframework.stereotype.Service;

@Service
public class RecordService {

    private final RecordRepository recordRepository;
    private final RecordCache recordCache;
    private final UrlHasher urlHasher;

    public RecordService(RecordRepository recordRepository, RecordCache recordCache, UrlHasher urlHasher) {
        this.recordRepository = recordRepository;
        this.recordCache = recordCache;
        this.urlHasher = urlHasher;
    }

    public Record addRecord(String url) {
        byte[] urlhash = urlHasher.hash(url);
        String shortcode = recordCache.getShortcode(urlhash);
        if (shortcode == null) {
            shortcode = recordRepository.insertRecord(urlhash, url);
            recordCache.putShortcode(urlhash, shortcode);
        }
        recordCache.putUrl(shortcode, url);
        return recordRepository.findById(shortcode).orElseThrow();
    }

    public Record getRecord(String shortcode) {
        return recordRepository.findById(shortcode).orElse(null);
    }

    public String resolveUrl(String shortcode) {
        String cachedUrl = recordCache.getUrl(shortcode);
        if (cachedUrl != null) {
            return cachedUrl;
        }
        Record record = recordRepository.findById(shortcode).orElse(null);
        if (record == null) {
            return null;
        }
        recordCache.putUrl(shortcode, record.url());
        return record.url();
    }
}
```

- [ ] **Step 9: Implement `RecordController.java`**

```java
package dev.dukecvar.dinkylink.api.record;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

record CreateShortUrlRequest(String url) {}
record CreateShortUrlResponse(String url, String shortURL) {}
record CreateShortUrlErrorResponse(String url, String error) {}

@RestController
public class RecordController {

    private final RecordService recordService;
    private final String baseUrl;

    public RecordController(RecordService recordService, @Value("${dinkylink.base-url}") String baseUrl) {
        this.recordService = recordService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/")
    public ResponseEntity<Object> createShortUrl(@RequestBody CreateShortUrlRequest request) {
        String url = request.url();

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(new CreateShortUrlErrorResponse(request.url(), "url must not be blank"));
        }
        if (url.length() > 2048) {
            return ResponseEntity.badRequest().body(new CreateShortUrlErrorResponse(url, "url must be 2048 characters or fewer"));
        }

        Record record = recordService.addRecord(url);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreateShortUrlResponse(record.url(), baseUrl + "/" + record.shortcode()));
    }

    @GetMapping("/{shortcode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortcode) {
        String url = recordService.resolveUrl(shortcode);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.MULTIPLE_CHOICES)
            .header("Location", url)
            .build();
    }
}
```

- [ ] **Step 10: Run the full test suite**

Run: `cd backend/api && ./gradlew test`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/Record.java \
        backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordRepository.java \
        backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordService.java \
        backend/api/src/main/java/dev/dukecvar/dinkylink/api/record/RecordController.java \
        backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/RecordServiceTest.java \
        backend/api/src/test/java/dev/dukecvar/dinkylink/api/record/RecordControllerTest.java
git rm backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/Record.kt \
       backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/RecordRepository.kt \
       backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/RecordService.kt \
       backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/record/RecordController.kt \
       backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/record/RecordServiceTest.kt \
       backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/record/RecordControllerTest.kt
git commit -m "refactor: port Record, RecordRepository, RecordService, RecordController to Java"
```

---

## Task 4: Convert `DinkyLinkApiApplication`

**Files:**
- Create: `backend/api/src/main/java/dev/dukecvar/dinkylink/api/DinkyLinkApiApplication.java`
- Create: `backend/api/src/test/java/dev/dukecvar/dinkylink/api/DinkyLinkApiApplicationTests.java`
- Delete: `backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplication.kt`
- Delete: `backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplicationTests.kt`

**Interfaces:**
- Consumes: nothing — this is the Spring Boot entry point; `@SpringBootTest` picks up every `@Component`/`@Service`/`@RestController`/`@RestController` bean already converted in Tasks 1–3.
- Produces: nothing further depends on this task; it's a terminal check that the whole context boots.

- [ ] **Step 1: Delete the Kotlin files**

Delete `DinkyLinkApiApplication.kt` and `DinkyLinkApiApplicationTests.kt`.

- [ ] **Step 2: Confirm the build now fails**

Run: `cd backend/api && ./gradlew compileJava`
Expected: FAIL — no class defines `DinkyLinkApiApplication`, which the already-converted beans' package scanning and `RecordServiceTest`/`RecordCacheTest`'s `@SpringBootTest` need to find an application context root.

- [ ] **Step 3: Write the Java test**

```java
package dev.dukecvar.dinkylink.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DinkyLinkApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run it and confirm it fails (production class missing)**

Run: `cd backend/api && ./gradlew test --tests "dev.dukecvar.dinkylink.api.DinkyLinkApiApplicationTests"`
Expected: FAIL — `DinkyLinkApiApplication` does not exist.

- [ ] **Step 5: Implement `DinkyLinkApiApplication.java`**

```java
package dev.dukecvar.dinkylink.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DinkyLinkApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DinkyLinkApiApplication.class, args);
    }
}
```

- [ ] **Step 6: Run the full test suite**

Run: `cd backend/api && ./gradlew test`
Expected: PASS. At this point `find backend/api/src -name '*.kt'` returns nothing.

- [ ] **Step 7: Commit**

```bash
git add backend/api/src/main/java/dev/dukecvar/dinkylink/api/DinkyLinkApiApplication.java \
        backend/api/src/test/java/dev/dukecvar/dinkylink/api/DinkyLinkApiApplicationTests.java
git rm backend/api/src/main/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplication.kt \
       backend/api/src/test/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplicationTests.kt
git commit -m "refactor: port DinkyLinkApiApplication to Java"
```

---

## Task 5: Convert the Gradle build files and remove Kotlin from the build

**Files:**
- Create: `backend/api/build.gradle`
- Create: `backend/api/settings.gradle`
- Delete: `backend/api/build.gradle.kts`
- Delete: `backend/api/settings.gradle.kts`
- Delete: now-empty `backend/api/src/main/kotlin/` and `backend/api/src/test/kotlin/` directory trees

**Interfaces:**
- Consumes: the outcome recorded in Task 1 Step 4 (whether AssertJ needed an explicit dependency or was already transitively available).
- Produces: nothing further depends on this task; it's verified by the full build/test run and the Kotlin-free checks below.

- [ ] **Step 1: Write `settings.gradle`**

```groovy
rootProject.name = 'api'
```

- [ ] **Step 2: Write `build.gradle`**

Drop the `kotlin("jvm")`/`kotlin("plugin.spring")` plugins and the
`kotlin {}` compiler-options block entirely (no Java equivalent needed).
Drop `org.jetbrains.kotlin:kotlin-reflect`, `tools.jackson.module:jackson-module-kotlin`,
and `org.jetbrains.kotlin:kotlin-test-junit5`. Keep `assertj-core` only if
Task 1 Step 4 found it wasn't already transitive; otherwise omit it.

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'dev.dukecvar'
version = '0.0.1-SNAPSHOT'
description = 'Dinky Link API'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'com.google.guava:guava:33.4.8-jre'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jdbc-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-redis-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    // testImplementation 'org.assertj:assertj-core'  // only if Task 1 Step 4 required it explicitly
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.withType(Test) {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Delete the Kotlin build files and empty Kotlin source directories**

```bash
rm backend/api/build.gradle.kts backend/api/settings.gradle.kts
rm -rf backend/api/src/main/kotlin backend/api/src/test/kotlin
```

- [ ] **Step 4: Run the full build and test suite**

Run: `cd backend/api && ./gradlew build`
Expected: PASS (this also runs `test` as part of `build`).

- [ ] **Step 5: Verify zero Kotlin remains**

Run: `find backend/api/src -name '*.kt'`
Expected: no output.

Run: `cd backend/api && ./gradlew dependencies --configuration runtimeClasspath | grep -i kotlin`
Expected: no output (no `kotlin-stdlib`/`kotlin-reflect` resolved).

- [ ] **Step 6: Manual runtime round-trip**

With `cd local && docker compose up -d` running, start the app in the
background, exercise both endpoints, then stop it:

```bash
cd backend/api && ./gradlew bootRun &
APP_PID=$!
sleep 15   # wait for Spring Boot to finish starting
curl -i -X POST http://localhost:8081/ -H "Content-Type: application/json" -d '{"url":"https://example.com/manual-check"}'
# copy the shortcode from the shortURL in the response, then:
curl -i http://localhost:8081/<shortcode>
kill $APP_PID
```

Expected: the `POST` returns `201` with a `shortURL`; the `GET` on that
shortcode returns `300` with a `Location` header pointing back at
`https://example.com/manual-check`.

- [ ] **Step 7: Commit**

```bash
git add backend/api/build.gradle backend/api/settings.gradle
git rm backend/api/build.gradle.kts backend/api/settings.gradle.kts
git commit -m "build: convert backend/api Gradle files to Groovy DSL, drop Kotlin"
```

---

## Task 6: Update project documentation

**Files:**
- Modify: `backend/api/README.md:3`
- Modify: `CLAUDE.md` (Tech Stack backend line; backend Code Style bullet)

**Interfaces:**
- Consumes: nothing code-level — this task is docs-only and has no test cycle beyond a visual diff review, per the spec's "Keeping Docs in Sync" requirement.

- [ ] **Step 1: Update `backend/api/README.md`**

Change line 3 from:
```
The Dinky Link public API (Kotlin, Spring Boot).
```
to:
```
The Dinky Link public API (Java, Spring Boot).
```

- [ ] **Step 2: Update `CLAUDE.md` tech stack line**

Change:
```
- **Backend**: Kotlin, Spring Boot 4, Gradle (kts), JVM 25. API Project in `backend/api`. Workers Project in `backend/workers`
```
to:
```
- **Backend**: Java, Spring Boot 4, Gradle, JVM 25. API Project in `backend/api`. Workers Project in `backend/workers`
```

- [ ] **Step 3: Update `CLAUDE.md` backend code style bullet**

Change:
```
  - Prefer `val` over `var`; use data classes for DTOs.
```
to:
```
  - Prefer final fields; use Java records for DTOs.
```

- [ ] **Step 4: Commit**

```bash
git add backend/api/README.md CLAUDE.md
git commit -m "docs: update tech stack and code style references from Kotlin to Java"
```
