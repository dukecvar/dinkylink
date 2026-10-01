# Kotlin to Java Migration

## Summary

Convert `backend/api` — the only backend module with real code —
from Kotlin to Java, with no functional change. `backend/workers` is
an empty Gradle scaffold and `backend/api-tests` does not exist yet;
neither has any Kotlin to convert. This is a pure language port:
same package structure, same endpoints, same behavior, same test
coverage, verified by `./gradlew build` / `./gradlew test` passing
afterward.

## Scope

In scope:

- `backend/api/src/main/kotlin/**/*.kt` (5 files) → `src/main/java/**/*.java`
- `backend/api/src/test/kotlin/**/*.kt` (5 files) → `src/test/java/**/*.java`
- `backend/api/build.gradle.kts` → `backend/api/build.gradle`
- `backend/api/settings.gradle.kts` → `backend/api/settings.gradle`
- `backend/api/README.md` (update Kotlin references)
- Root `CLAUDE.md` (update tech stack line and backend code-style bullets)

Out of scope:

- `backend/workers` — no source files exist, nothing to convert.
- `backend/api-tests` — does not exist yet.
- Any behavior, API contract, schema, or infra change.
- Introducing null-safety annotations (`@Nullable` etc.) — this
  project doesn't use them today and adding them is a separate concern
  from the language port.

## Class-by-class mapping

### `DinkyLinkApiApplication.kt` → `DinkyLinkApiApplication.java`

Standard Spring Boot entry point. `fun main(args: Array<String>)` →
`public static void main(String[] args)` inside the class, using
`SpringApplication.run(DinkyLinkApiApplication.class, args)`.

### `record/Record.kt` → `record/Record.java`

Kotlin data class → Java `record`:

```java
@Table("records")
public record Record(
    @Id String shortcode,
    byte[] urlhash,
    String url,
    OffsetDateTime lastTouchedTimestamp
) {}
```

Spring Data JDBC maps entities via an all-args constructor, which
Java records provide natively, so no behavior change.

### `record/RecordController.kt` → `record/RecordController.java`

- The three nested DTOs (`CreateShortUrlRequest`, `CreateShortUrlResponse`,
  `CreateShortUrlErrorResponse`) become Java records.
- `url.isNullOrBlank()` → `url == null || url.isBlank()`.
- Constructor injection, `@Value` on the `baseUrl` constructor param,
  and the `@PostMapping`/`@GetMapping` methods carry over unchanged
  apart from syntax.

### `record/RecordRepository.kt` → `record/RecordRepository.java`

Interface, unchanged apart from syntax:

```java
public interface RecordRepository extends CrudRepository<Record, String> {
    @Query("SELECT insert_record(:urlhash, :url)")
    String insertRecord(@Param("urlhash") byte[] urlhash, @Param("url") String url);
}
```

### `record/RecordService.kt` → `record/RecordService.java`

Constructor-injected `private final` fields replace Kotlin's
constructor-property shorthand. The two Elvis/`also` chains become
explicit control flow:

- `addRecord`: `recordCache.getShortcode(urlhash) ?: run { ... }` →
  an `if (shortcode == null) { ...; shortcode = inserted; }` block.
- `resolveUrl`: `recordCache.getUrl(shortcode) ?: recordRepository.findById(...)...also { ... }`
  → fetch from cache; if null, look up in the repository, and if
  found, populate the cache before returning.

`findById(...).orElseThrow()` / `.orElse(null)` carry over directly
(`java.util.Optional` is already what Kotlin was calling into).

### `record/RecordCache.kt` → `record/RecordCache.java`

- Companion-object constant → `private static final Duration TTL = Duration.ofDays(365);`
- `get`/`put` private helpers and the two key-builder helpers
  (`shortcodeKey`, `urlKey`) carry over as private methods; string
  templates become `String.format` or concatenation.

### `record/UrlHasher.kt` → `record/UrlHasher.java`

Single-method component, unchanged apart from syntax.

## Test migration

All five test classes move from `src/test/kotlin` to `src/test/java`,
keep JUnit 5 (`@Test`, `@SpringBootTest`, `@WebMvcTest`, `@Transactional`,
`@MockitoBean`, `@AfterEach`), and switch assertions from `kotlin.test`
to AssertJ:

| Kotlin | Java/AssertJ |
|---|---|
| `assertEquals(a, b)` | `assertThat(b).isEqualTo(a)` |
| `assertNull(x)` | `assertThat(x).isNull()` |
| `assertNotNull(x)` | `assertThat(x).isNotNull()` |
| `assertTrue(cond, msg)` | `assertThat(cond).as(msg).isTrue()` |
| `assertNotEquals(a, b)` | `assertThat(b).isNotEqualTo(a)` |

Backtick-named test methods (e.g. `` `stores and retrieves a shortcode by urlhash`() ``)
become camelCase methods with `@DisplayName("...")` carrying the
original readable sentence, e.g.:

```java
@Test
@DisplayName("stores and retrieves a shortcode by urlhash")
void storesAndRetrievesAShortcodeByUrlhash() { ... }
```

Mockito's backtick-escaped `` `when`(...) `` becomes plain `when(...)`
(not a reserved word in Java). `lateinit var` fields become plain
`@Autowired`/`@MockitoBean` fields (non-final, since JUnit instantiates
and Spring injects them). `mutableListOf<String>()` → `new ArrayList<>()`.

`backend/api/build.gradle.kts`'s `testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")`
is replaced with `testImplementation("org.assertj:assertj-core")` —
during implementation, confirm whether AssertJ is already pulled in
transitively by the modular test starters (`spring-boot-starter-actuator-test`,
etc.) before adding it explicitly, to avoid a redundant dependency.

## Build files

`backend/api/build.gradle.kts` → `backend/api/build.gradle` (Groovy
DSL), per the "convert all Kotlin, including build tooling" scope
decision:

- Drop the `kotlin("jvm")` and `kotlin("plugin.spring")` plugin
  entries; keep `id("org.springframework.boot")` and
  `id("io.spring.dependency-management")`, add the standard `java`
  plugin.
- Drop the `kotlin {}` compiler-options block entirely (`-Xjsr305=strict`
  and `-Xannotation-default-target=param-property` are Kotlin-only
  flags with no Java equivalent).
- Drop `implementation("org.jetbrains.kotlin:kotlin-reflect")` and
  `implementation("tools.jackson.module:jackson-module-kotlin")` (Java
  records serialize with plain Jackson, no Kotlin module needed).
- Drop `testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")`,
  add `testImplementation("org.assertj:assertj-core")` if not already
  present transitively (see above).
- Keep the `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`
  block and all other dependencies (`spring-boot-starter-actuator`,
  `-data-jdbc`, `-data-redis`, `-webmvc` and their `-test` variants,
  `guava`, `postgresql`) unchanged.
- Keep `tasks.withType(Test) { useJUnitPlatform() }`.

`backend/api/settings.gradle.kts` → `backend/api/settings.gradle`:
just `rootProject.name = 'api'`.

## Documentation updates

- `CLAUDE.md` tech stack line: `Backend: Kotlin, Spring Boot 4, Gradle (kts), JVM 25`
  → `Backend: Java, Spring Boot 4, Gradle, JVM 25`.
- `CLAUDE.md` backend code style bullets: `Prefer val over var; use data classes for DTOs.`
  → `Prefer final fields; use Java records for DTOs.` The remaining
  bullets (constructor injection, thin controllers, isolated unit
  tests, E2E tests in `backend/api-tests`) are language-agnostic and
  unchanged.
- `backend/api/README.md`: update any Kotlin-specific references
  (file extensions, "Kotlin/Spring Boot" phrasing, directory paths
  like `src/main/kotlin`) to their Java equivalents. Exact edits
  depend on the file's current content, reviewed during
  implementation.

## Verification

With `local/docker compose up -d` running (Postgres + Redis):

- `./gradlew build` succeeds with no Kotlin plugin/compiler involved.
- `./gradlew test` passes, covering the same cases as today
  (context load, cache TTL behavior, controller request validation
  and routing, service-level record creation/resolution, hasher
  determinism).
- `./gradlew bootRun` + a manual `POST /` and `GET /<shortcode>`
  round-trip confirms unchanged runtime behavior.

No new tests are added — this migration preserves existing coverage
exactly; it does not expand it.
