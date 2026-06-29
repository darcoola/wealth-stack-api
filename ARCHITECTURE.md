# Architecture

> Concise map of the codebase for fast onboarding. **Keep this file up to date** whenever
> you add a module, change the domain model, add an endpoint, or add a bank parser.

## What this is

A Spring Boot personal-finance backend that imports bank statement exports (CSV) from
multiple Polish banks, normalizes them into a single `BankingOperation` model, persists them,
and lets the user attach human-friendly display names to raw account identifiers.

## Stack

- **Kotlin 2.3.0** on **JDK 25**, **Spring Boot 4.0.2** (web + data-jpa + jackson-kotlin).
- Frontend: **Angular 21 + PrimeNG 21** SPA in `frontend/` (see *Frontend* below).
- Build: **Gradle (Groovy DSL)** — `build.gradle` / `settings.gradle` (note: *not* `.kts`).
- Persistence: **PostgreSQL** in prod/dev, **H2** (PostgreSQL mode) for tests.
- Schema is owned by **Flyway** (`src/main/resources/db/migration`), not Hibernate — see below.
- Dev DB: `compose.yaml` (Postgres 17) auto-started via `spring-boot-docker-compose`.
- Tests: JUnit 5 (`kotlin-test`) + Spring Boot Test + **assertk** assertions.

## Build & run

```bash
./gradlew build                                   # build
./gradlew test                                    # all tests (uses H2, no Docker needed)
./gradlew test --tests "com.wealthStack.SomeTest" # single test class
./gradlew bootRun                                 # run app (auto-starts Postgres container)
```

App runs on `:8088` (serves both the REST API and the bundled UI). See `dev/*.http` for
ready-to-run request examples.

Frontend dev loop (live reload, no rebuild of the backend):
```bash
cd frontend && npm start   # Angular dev server on :4200, proxies /api → :8088 (proxy.conf.json)
```
Run `./gradlew bootRun` (backend on :8088) alongside it. For a backend-only build that skips the
(slow) npm build, pass `-PskipFrontend`.

## Domain model

Two JPA entities (`src/main/kotlin/com/wealthStack/bankstatement/`):

- **`BankingOperation`** (`banking_operations`) — one bank transaction. Fields: `date`,
  `description`, `amount` (BigDecimal 19,2), `type` (`OperationType` CREDIT/DEBIT, derived from
  amount sign), `bankName`, `account` (raw account/card identifier), `displayName?` (resolved
  from mappings), `category` (bank's own transaction type), `sourceFileName?` (provenance;
  nullable — absent for manual/JSON rows with no source), `fingerprint` +
  `occurrence` (duplicate-detection identity — see below). Unique constraint on
  `(fingerprint, occurrence)`.
- **`AccountMapping`** (`account_mappings`) — maps a unique `rawAccount` → `displayName`.
  Editing a mapping back-fills `displayName` on all existing operations with that account.

`OperationType` is `CREDIT`/`DEBIT`. Amount sign drives the type (≥0 = CREDIT).

## Database schema & migrations

Schema is managed by **Flyway**, not Hibernate. Migrations live in
`src/main/resources/db/migration` as `V<n>__<description>.sql` and run automatically on startup
(prod/dev against Postgres, tests against H2 in PostgreSQL mode — H2 support ships in
`flyway-core`). `V1__create_initial_schema.sql` is the baseline matching the entities above.

Hibernate runs in **`ddl-auto: validate`** (both prod and test): it never touches the schema, only
checks the entities against what Flyway built. **Any entity change (new column/table/constraint)
needs a matching migration** — add a new `V<n>__...sql`; never edit an applied migration. Column
names follow Spring's snake_case physical naming strategy (e.g. `bankName` → `bank_name`).

## Package layout & flow

All code lives under `com.wealthStack.bankstatement`.

```
bankstatement/
  parser/        # bank-specific CSV parsers (write side input)
  query/         # read side: finders + query controllers + DTOs
  (root)         # entities, repositories, command controllers, services, config
```

**Wiring is explicit**, not annotation-scanned: `BankStatementConfig` declares every bean
(parsers, factory, services, controllers, finders) via `@Bean`. Parsers are constructor-injected
as a `List<StatementParser>`. When you add a service/controller/parser, register it there.
(Entities and `JpaRepository` interfaces are still picked up by Spring Data automatically.)

### Import flow (command side)
1. `BankStatementController` `POST /api/v1/bank-statements` (multipart `file` + `bankName`).
2. `StatementImporter.importStatement` → `StatementParserFactory.getParser(bankName)`
   (case-insensitive; throws `IllegalArgumentException` → HTTP 400 for unknown banks).
3. Parser decodes bytes with its own `charset` and returns `List<BankingOperation>`.
4. Importer applies known account mappings to set `displayName`.
5. Importer assigns each operation a `fingerprint` + `occurrence` (duplicate detection) and
   **overwrites** any existing row sharing that identity instead of inserting a duplicate, then
   `saveAll`.
6. Returns `ImportResult` (summary with `operationsImported` / `operationsOverwritten` + DTOs).

### Manual / JSON ingest (command side)
`BankStatementController` `POST /api/v1/bank-statements/operations` (JSON `ManualOperationsRequest`:
`bankName`, optional `source`, list of `operations` with `date`/`description`/`amount`/`account` and
optional `category`/`displayName`). For already-prepared rows — historical data or banks without a
parser. `StatementImporter.importOperations` builds entities (deriving `type` from amount sign,
`sourceFileName` from `source`) and runs them through the **same** mapping → fingerprint →
duplicate-overwrite → `saveAll` pipeline (`persist`) as parsed statements, so re-sends fold onto the
same rows. Returns the same `ImportResult`.

### Duplicate detection
Bank exports carry no stable transaction id, so identity is content-derived (`OperationFingerprint`):
SHA-256 of `bankName | account | date | amount | description`. `category` is **excluded** (it will
become a user-editable classification), as are `displayName`/`sourceFileName`. Genuinely identical
operations on the same day share a fingerprint and are disambiguated by a zero-based `occurrence`
index assigned in file order, so re-imports fold onto the same physical rows. Current strategy is
**overwrite** (copies `category`, `displayName`, `sourceFileName` onto the existing row); a DB
unique constraint on `(fingerprint, occurrence)` guarantees no duplicates slip in.

### Mapping flow (command side)
- `AccountMappingController` `PUT /api/v1/account-mappings` → `AccountMapper.upsert`
  (upserts the mapping and back-fills `displayName` on matching operations).

### Read side (query package)
- `BankingOperationQueryController` `GET /api/v1/bank-statements` → all operations as `OperationDto`.
- `AccountMappingQueryController` `GET /api/v1/account-mappings` → all mappings as `AccountMappingDto`.
- `BankingOperation.toDto()` lives in `query/BankingOperationFinder.kt`; DTOs in `query/Dtos.kt`.

## Parsers

`StatementParser` interface: `bankName`, `charset` (default UTF-8), `parse(content, sourceFileName)`.
Factory keys parsers by lowercase `bankName`.

- **`MBankCsvParser`** (`bankName="mbank"`, UTF-8): `;`-separated; data starts after the
  `#Data operacji;` header line; amounts use Polish format (comma decimal, ` PLN` suffix).
- **`PkoBpCsvParser`** (`bankName="pkobp"`, **windows-1250**): comma-separated, every field
  quoted, quote-aware splitter (commas can appear inside quoted fields). Data starts after the
  `Data operacji` header; description spans trailing columns; `account` extracted from
  `Numer karty:` / `Rachunek nadawcy:` labels.
- **`ManualCsvParser`** (`bankName="manual"`, UTF-8): WealthStack's **own predefined schema** for
  already-prepared rows (historical data / unparsed banks) — not a bank export. Header row names
  the columns (case-insensitive, order-independent): required `date,bankName,account,description,
  amount`, optional `category,displayName`. Quote-aware, dot-decimal amounts, `type` from amount
  sign. Each row carries its own `bankName`, so one file may mix banks; the upload `bankName=manual`
  only selects the parser. JSON equivalent: `POST /api/v1/bank-statements/operations` (above).

**To add a bank:** implement `StatementParser`, register a `@Bean` in `BankStatementConfig`.
Test fixtures live in `src/test/resources/<bank>-test-statement.csv`.

## Frontend

A single-page app in `frontend/` — **Angular 21** (standalone components + signals) with **PrimeNG
21** components and the free **Aura** theme (`@primeng/themes`), PrimeIcons. It's a thin UI shell
today; pages are stubs to be filled in incrementally.

```
frontend/
  src/app/
    app.ts / app.html / app.scss   # shell: top header + left p-menu + <router-outlet>
    app.config.ts                  # providers: router, HttpClient, providePrimeNG (Aura, .app-dark)
    app.routes.ts                  # lazy-loaded routes; '' → dashboard, '**' → dashboard
    pages/<name>/<name>.ts         # one standalone component per menu item (stubs)
  proxy.conf.json                  # dev: proxy /api → http://localhost:8088
  angular.json                     # build output → frontend/dist/frontend/browser
```

Menu items (left nav, in `app.ts` `menuItems`): **Dashboard**, **Operations**, **Import**,
**Accounts**, **Reports**. Add a page by creating `pages/<name>/<name>.ts`, a route in
`app.routes.ts`, and a `MenuItem` in `app.ts`.

**Build integration & serving (single jar):** `build.gradle` uses the `com.github.node-gradle.node`
plugin (it downloads a pinned **Node 26.4.0** for reproducibility). `frontendBuild` runs the npm
build; `copyFrontend` stages the output under `build/frontend-resources/static/`, which is wired in
as a `main` resources source dir so `processResources` (and thus `bootJar`/`bootRun`) bundle it at
`classpath:/static/`. `web/WebConfig.kt` serves those files and falls back to `index.html` for
non-API, non-file paths so Angular's HTML5 deep links survive a refresh; unknown `api` paths still
404. Skip the whole frontend build with `-PskipFrontend`.

## Conventions

- Services and finders are `open class` with `@Transactional` `open fun` (no `@Service`/`@Component`
  annotations — they're plain classes wired by `BankStatementConfig`); they need `open` for Spring
  proxying. Keep this pattern when adding new ones.
- Command (write) vs query (read) are separated: root package = commands, `query/` = reads.
- API base path: `/api/v1/...`.
