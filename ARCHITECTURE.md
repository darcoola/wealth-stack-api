# Architecture

> Concise map of the codebase for fast onboarding. **Keep this file up to date** whenever
> you add a module, change the domain model, add an endpoint, or add a bank parser.

## What this is

A Spring Boot personal-finance backend that imports bank statement exports (CSV) from
multiple Polish banks, normalizes them into a single `BankingOperation` model, persists them,
lets the user attach human-friendly display names to raw account identifiers, and classify
operations with a user-curated **category dictionary**.

**Single-user, local-first**: there is no login and no user/tenant model — the app is meant to be
installed and run locally by one person, and every endpoint is open. (A Keycloak + party-based
multi-user model existed briefly and was removed again; see V10–V12 below.)

## Stack

- **Kotlin 2.3.0** on **JDK 25**, **Spring Boot 4.0.2** (web + data-jpa + jackson-kotlin).
- Frontend: **Angular 21 + PrimeNG 21** SPA in `frontend/` (see *Frontend* below).
- Build: **Gradle (Groovy DSL)** — `build.gradle` / `settings.gradle` (note: *not* `.kts`).
- Persistence: **PostgreSQL** in prod/dev, **H2** (PostgreSQL mode) for tests.
- Schema is owned by **Flyway** (`src/main/resources/db/migration`), not Hibernate — see below.
- Dev infra: `compose.yaml` (Postgres 17, Elasticsearch) auto-started via
  `spring-boot-docker-compose`.
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

Four JPA entities (`src/main/kotlin/com/wealthStack/bankstatement/`):

- **`BankingOperation`** (`banking_operations`) — one bank transaction. Fields: `date`,
  `description`, `amount` (BigDecimal 19,2), `type` (`OperationType` CREDIT/DEBIT, derived from
  amount sign), `bankName`, `account` (raw account/card identifier), `accountDisplayName?` (the
  mapped account's display name, resolved from mappings), `category?` (`@ManyToOne` FK to `Category`, nullable = Uncategorized; set by the
  user in the UI, or by a **manual** import that names a dictionary category — raw bank parsers
  never set it), `additionalInfo?` (free-text note a manual import may supply when the description
  alone — often just a shop name — isn't enough to deduce a category; raw bank parsers never set it;
  excluded from the fingerprint), `sourceFileName?` (provenance; nullable — absent for
  manual/JSON rows with no source), `fingerprint` + `occurrence` (duplicate-detection identity —
  see below). Unique constraint on `(fingerprint, occurrence)`.
- **`AccountMapping`** (`account_mappings`) — maps a unique `rawAccount` → `displayName`. Every
  operation is connected to one: an import **auto-creates** a mapping for any raw account it hasn't
  seen, defaulting `displayName` to the raw account (the user renames it later on the Accounts page).
  Editing a mapping back-fills `accountDisplayName` on all existing operations with that account.
- **`Category`** (`categories`) — an editable dictionary entry with a unique `name` and a nullable
  `group` (`@ManyToOne` FK to `CategoryGroup`, null = Ungrouped). The group drives reporting: totals
  are summed as-is (no debit/credit split) and charts/tables split into one section per group, not
  by amount sign. Fully user-curated (create / rename / regroup / delete) via `CategoryService`;
  operations point at a category by FK. Deleting a category in use first un-assigns it from its
  operations (FK → null). Shaped to later grow a `parentId` for subcategories.
- **`CategoryGroup`** (`category_groups`) — a user-managed grouping categories are bucketed into,
  with a unique `name`. Replaced the old fixed `CategoryType` enum (spending/income/others), which
  survives only as three editable seed groups. Fully user-curated (create / rename / delete) via
  `CategoryGroupService`; deleting a group in use first un-assigns it from its categories (FK →
  null, back to Ungrouped).

`OperationType` is `CREDIT`/`DEBIT`. Amount sign drives the type (≥0 = CREDIT).

## Database schema & migrations

Schema is managed by **Flyway**, not Hibernate. Migrations live in
`src/main/resources/db/migration` as `V<n>__<description>.sql` and run automatically on startup
(prod/dev against Postgres, tests against H2 in PostgreSQL mode — H2 support ships in
`flyway-core`). `V1__create_initial_schema.sql` is the baseline; `V2` made `source_file_name` nullable; `V3`
added the `categories` table and replaced `banking_operations.category` (a string) with a nullable
`category_id` FK (old values discarded — operations start Uncategorized). `V5` added report indices
on `banking_operations (date)` and `(category_id)`. `V6` added `categories.type`
(spending/income; existing rows backfilled `SPENDING`). `V7` added the nullable
`banking_operations.additional_info` free-text note. `V8` added `banking_operations.needs_verification`.
`V9` added the `category_groups` table (seeded Spending/Income/Others), replaced `categories.type`
with a nullable `group_id` FK (backfilled from the old type), and indexed `categories (group_id)`.
`V10`/`V11` introduced a multi-user party model (`party`, `app_user`, `party_membership` tables and a
`party_id` on every data table with per-party composite uniques); `V12` removed it again — it keeps
only the bootstrap party's rows (id 1), drops `party_id` and the three party tables, and restores the
global uniques. V10/V11 stay in place only because they were already applied.

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
1. `BankStatementController` `POST /api/v1/bank-statements` (multipart `file` + **optional** `bankName`).
2. `StatementImporter.importStatement` picks the parser: an explicit `bankName` →
   `StatementParserFactory.getParser` (case-insensitive; unknown bank → `IllegalArgumentException`
   → HTTP 400); a blank/absent `bankName` → `StatementParserFactory.detectParser`, which decodes
   the bytes leniently (ISO-8859-1, so detection runs before the real charset is known) and asks
   each parser's `canParse(content)`. Exactly one match wins; **no match or an ambiguous
   multi-match throws `IllegalArgumentException` → HTTP 400** (the user then names the bank).
3. Parser decodes bytes with its own `charset` and returns `List<BankingOperation>`.
4. Importer applies account mappings to set `accountDisplayName` — **auto-creating** a mapping
   (`displayName` = raw account) for any account without one, so every row is connected to a
   mapping — and resolves a category for any row
   that names one (manual imports only — see below); rows from raw bank parsers carry no category and
   start Uncategorized for the user to classify later.
5. Importer assigns each operation a `fingerprint` + `occurrence` (duplicate detection) and
   **overwrites** any existing row sharing that identity instead of inserting a duplicate, then
   `saveAll`.
6. Returns `ImportResult` (summary with `operationsImported` / `operationsOverwritten` + DTOs).

### Manual / JSON ingest (command side)
`BankStatementController` `POST /api/v1/bank-statements/operations` (JSON `ManualOperationsRequest`:
`bankName`, optional `source`, list of `operations` with `date`/`description`/`amount`/`account` and
optional `accountDisplayName`/`additionalInfo`/`category`). For already-prepared rows — historical data or banks without a
parser. `StatementImporter.importOperations` builds entities (deriving `type` from amount sign,
`sourceFileName` from `source`) and runs them through the **same** mapping → category-resolve →
fingerprint → duplicate-overwrite → `saveAll` pipeline (`persist`) as parsed statements, so re-sends
fold onto the same rows. Returns the same `ImportResult`.

### Hand-entered cash operations (command side)
`BankStatementController` `POST /api/v1/bank-statements/operations/manual` (JSON `NewOperationRequest`:
`date`, `description`, signed `amount`, optional `categoryId`/`additionalInfo`/`force`) →
`StatementImporter.addCashOperation` → the created row as an `OperationDto`. Backs the Operations
page's **Add operation** form: cash spending/income that no statement will ever carry. There is no
bank or account in the payload — the row is booked on the cash account
(`StatementImporter.CASH_BANK_NAME` = `cash` / `CASH_ACCOUNT` = `Cash`), whose `AccountMapping` is
auto-created on first use like any other.

It reuses the import pipeline's account mapping, auto-categorization (an entry left uncategorized gets
a predicted category flagged `needsVerification`) and ES indexing, but **not** its duplicate-overwrite
rule — two identical cash spends on the same day can be two real operations, so it always inserts,
continuing the `occurrence` index past the stored rows. Since an identical entry is nonetheless more
often a double-submit, an unforced request whose fingerprint matches existing rows is refused with
**409** (`DuplicateOperationException` → `{ error, duplicates: [OperationDto] }`); the UI shows the
matches and re-sends with `force: true` when the user confirms.

**Manual imports may carry a category** (the `category` JSON field above, or a `category` CSV column);
it must name a category that already exists in the dictionary or the whole import is rejected (HTTP
400). Resolution happens in `persist` (`resolveCategories`), the single place that enforces the
"require it to exist" rule for both ingest paths (the parsers/JSON only stash the name on the
transient `BankingOperation.categoryName` carrier). Raw bank parsers never set a category.

### Data export / import (command side)
Whole-dataset backup for moving data between installations (`DataBackupController` →
`DataBackupService`, DTOs in `DataBackup.kt`):
- `GET /api/v1/data/export` → a `DataBackup` JSON attachment (`wealthstack-backup-<date>.json`):
  `format` = `wealthstack-backup`, `version` = 1, `exportedAt`, then `categoryGroups` (`name`),
  `categories` (`name`, `group` name), `accountMappings` (`rawAccount`, `displayName`) and `operations`
  (`date`, `description`, signed `amount`, `bankName`, `account`, `category` name, `additionalInfo`,
  `sourceFileName`, `needsVerification`, `occurrence`). Entries reference each other by **natural keys,
  never database ids**, so a file restores into any installation; `type` and `accountDisplayName` are
  derived again on import.
- `POST /api/v1/data/import?replace=false|true` (JSON body = the backup file) → `DataImportResult`
  (created groups/categories/mappings, `operationsImported` / `operationsOverwritten`). One transaction:
  a wrong `format`/`version`, a reference to an unknown group or category, or the same
  `(fingerprint, occurrence)` twice fails with **400 and changes nothing**.
  - **merge** (default): groups/categories match by name, mappings by raw account, operations by their
    duplicate-detection identity (fingerprint recomputed from the content + the file's `occurrence`), so
    re-importing the same file is a no-op. On a match the **file wins** (a category's group, a mapping's
    display name — back-filled onto local operations —, an operation's category/note/provenance/
    verification flag); local data the file doesn't mention is kept.
  - **replace**: deletes all operations, categories, groups and mappings first (and clears the ES index
    via `AutoCategorizationService.clearIndex`), leaving exactly the backup's content.
  Loaded categorized operations are indexed into ES like any import. Raw accounts without a mapping in
  the file get one auto-created, as after a statement import.

### Duplicate detection
Bank exports carry no stable transaction id, so identity is content-derived (`OperationFingerprint`):
SHA-256 of `bankName | account | date | amount | description`. `category` is **excluded** (it is a
user-editable classification), as are `accountDisplayName`/`sourceFileName`. Genuinely identical
operations on the same day share a fingerprint and are disambiguated by a zero-based `occurrence`
index assigned in file order, so re-imports fold onto the same physical rows. Current strategy is
**overwrite** (copies `accountDisplayName` and `sourceFileName` onto the existing row, plus `category`
**only when the incoming row carries one** — so a manual re-import that names a category re-classifies
the row, while a raw-bank re-import, carrying none, preserves the user's UI assignment); a DB unique
constraint on `(fingerprint, occurrence)` guarantees no duplicates slip in.

### Mapping flow (command side)
- `AccountMappingController` (`/api/v1/account-mappings`) → `AccountMapper`: `POST` create,
  `PUT /{id}` update, `DELETE /{id}` delete. `rawAccount` is unique. Every change back-fills the
  denormalized `accountDisplayName` on operations with that raw account; delete (and editing a
  mapping's `rawAccount`) clears it on the orphaned operations so they revert to the raw account.

### Category flow (command side)
- `CategoryController` (`/api/v1/categories`) → `CategoryService`: `POST` create (`{ name, groupId? }`,
  group defaults null = Ungrouped), `PUT /{id}` update name + group (`{ name, groupId? }`; the
  controller always sets the group via `SetGroup`, so it can also clear it to null; internal
  `rename` uses `KeepGroup` to leave it untouched), `DELETE /{id}` delete (un-assigns from
  operations first). Names are unique.
- `CategoryGroupController` (`/api/v1/category-groups`) → `CategoryGroupService`: `POST` create
  (`{ name }`), `PUT /{id}` rename (`{ name }`), `DELETE /{id}` delete (un-assigns from categories
  first, back to Ungrouped). Names are unique.
- `OperationCommandController` `PUT /api/v1/bank-statements/operations/{id}/category`
  (`{ "categoryId": Long? }`) → `CategoryService.assignToOperation` — assign or, with `null`, clear.
- `OperationCommandController` `PUT /api/v1/bank-statements/operations/{id}/additional-info`
  (`{ "additionalInfo": String? }`) → `OperationCommandService.updateAdditionalInfo` — sets the
  free-text note on any operation (blank/null clears it; trimmed); works on bank-imported rows too.
- `OperationCommandController` bulk actions: `PUT /api/v1/bank-statements/operations/category`
  (`{ "operationIds": [Long], "categoryId": Long? }`) → `CategoryService.assignToOperations`
  (bulk assign/clear); `DELETE /api/v1/bank-statements/operations`
  (`{ "operationIds": [Long] }`) → `OperationCommandService.deleteAll` (permanent bulk delete);
  `DELETE /api/v1/bank-statements/operations/all` → `OperationCommandService.deleteEverything`
  (wipes every operation, returns `{ "deletedCount": Long }`; mappings/categories/groups untouched —
  backs the Administration page's "Remove all operations").

### Read side (query package)
- `BankingOperationQueryController` `GET /api/v1/bank-statements` → paginated operations as
  `OperationDto` (includes `id`, `additionalInfo`, `categoryId`, and the category `name`). Optional
  filters (built into a JPA `Specification` in `BankingOperationFinder`): `globalFilter` (substring
  over description/info/account/category), `needsVerificationOnly`, `uncategorizedOnly`, `accounts`
  (raw-account multiselect) OR-ed with `unmappedAccount` (the "(No account)" option → null
  `accountDisplayName`), `groupIds` (category-group multiselect), and a `dateFrom`/`dateTo`
  inclusive date span.
- `AccountMappingQueryController` `GET /api/v1/account-mappings` → all mappings as `AccountMappingDto`
  (id + rawAccount + displayName), sorted by display name.
- `CategoryQueryController` `GET /api/v1/categories` → all categories as `CategoryDto`
  (id + name + groupId + groupName), sorted by name.
- `CategoryGroupQueryController` `GET /api/v1/category-groups` → all groups as `CategoryGroupDto`
  (id + name), sorted by name.
- `ReportQueryController` `GET /api/v1/reports/category-monthly-totals`
  → `MonthlyCategoryTotalDto` list (one per `(month, category)` bucket, `month` = `YYYY-MM`,
  `groupId`/`groupName` = the category's group or null for Ungrouped/Uncategorized, `total` = signed
  `SUM(amount)` as-is). `ReportFinder` just maps one aggregation query
  (`BankingOperationRepository.aggregateByMonthAndCategory`, the only `@Query`/GROUP BY in the code;
  `LEFT JOIN` keeps Uncategorized rows and Ungrouped categories). No mode param — the frontend splits
  rows by `groupId` into one section per group (Ungrouped/Uncategorized folded together) and
  filters/pivots client-side.
- `BankingOperation.toDto()` lives in `query/BankingOperationFinder.kt`; DTOs in `query/Dtos.kt`.

## Parsers

`StatementParser` interface: `bankName`, `charset` (default UTF-8), `canParse(content)`,
`parse(content, sourceFileName)`. Factory keys parsers by lowercase `bankName` (`getParser`) and
auto-detects one from file content (`detectParser`) via each parser's `canParse`. Each `canParse`
keys off a distinctive ASCII header marker (they don't overlap): mBank's `#Data operacji;` line,
PKO BP's `Data operacji` CSV header field, Revolut's `Rodzaj,Produkt,` header prefix, the manual
schema's required column names.

- **`MBankCsvParser`** (`bankName="mbank"`, UTF-8): `;`-separated; data starts after the
  `#Data operacji;` header line; amounts use Polish format (comma decimal, ` PLN` suffix).
- **`PkoBpCsvParser`** (`bankName="pkobp"`, **windows-1250**): comma-separated, every field
  quoted, quote-aware splitter (commas can appear inside quoted fields). Data starts after the
  `Data operacji` header; `date` comes from the **value date** (`Data waluty`, 2nd column), not the
  operation date, which can shift between statement generations; description spans trailing columns;
  `account` extracted from `Numer karty:` / `Rachunek nadawcy:` labels.
- **`RevolutCsvParser`** (`bankName="revolut"`, UTF-8): comma-separated, quote-aware (fields quoted
  only when they contain a comma). Columns: `Rodzaj, Produkt, Data rozpoczęcia, Data zrealizowania,
  Opis, Kwota, Opłata, Waluta, State, Saldo`. Date from the `Data rozpoczęcia` (started) timestamp's
  date part; `account` = `Produkt` (the pocket — `Bieżące`/`Oszczędności` — since Revolut exports
  carry no IBAN); dot-decimal signed `Kwota`. Only completed rows (`State == ZAKOŃCZONO`) are
  imported; reverted/pending (e.g. `COFNIĘTO`) are skipped. `Opłata`/`Saldo` ignored.
- **`ManualCsvParser`** (`bankName="manual"`, UTF-8): WealthStack's **own predefined schema** for
  already-prepared rows (historical data / unparsed banks) — not a bank export. Header row names
  the columns (case-insensitive, order-independent): required `date,bankName,account,description,
  amount`, optional `accountDisplayName,additionalInfo,category`. Quote-aware, dot-decimal amounts, `type` from amount
  sign. A non-blank `category` must name an existing dictionary entry (resolved at import; unknown
  name → 400); blank/absent leaves the row Uncategorized. Each row carries its own `bankName`, so one
  file may mix banks; the upload `bankName=manual` only selects the parser. JSON equivalent:
  `POST /api/v1/bank-statements/operations` (above).

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

Menu items (left nav, in `app.ts` `menuItems`): **Dashboard**, **Operations**, **Categories**,
**Groups**, **Import**, **Accounts**, **Reports**, **Administration** (a **Backup** section — *Export
data* downloads `GET /api/v1/data/export` as a file via `core/data-backup.service.ts`, *Import data*
reads a chosen `.json` and posts it to `POST /api/v1/data/import` with a Merge/Replace select, Replace
asking for confirmation — and a danger-zone "Remove all operations" button hitting
`DELETE .../operations/all`). The Operations table has a server-side filter bar
(global search, a date-span range picker, and prefetched **account** and **category-group**
multiselects — options pulled from the account-mappings and category-groups endpoints); an **Add
operation** button opens a `p-dialog` form (Expense/Income toggle, positive amount — the toggle sets
the sign —, date, description, optional category and note) that posts to
`POST .../operations/manual` for hand-entered cash, goes full-width on phones via the dialog's
`[breakpoints]`, and on the endpoint's 409 shows the operations it matched and turns Save into
**Add anyway** (re-sends with `force`); it assigns a
category per row
via an inline `p-select` (`PUT .../operations/{id}/category`) and edits the free-text **Info** note
per row via an inline cell editor (`PUT .../operations/{id}/additional-info`, saved on blur); the
Categories page is the dictionary CRUD (`core/categories.service.ts`) — each row's **group** is
editable via an inline `p-select` (options from `core/category-groups.service.ts`, plus a "— None —"
Ungrouped entry), and the add-row sets the new category's group. The **Groups** page
(`pages/category-groups/`, `core/category-groups.service.ts`) is the group-dictionary CRUD
(add / rename inline / delete, mirroring Categories). The **Reports** page (`pages/reports/`,
`core/reports.service.ts`) has Monthly/Yearly/Table tabs (`primeng/tabs`); Monthly & Yearly render
`p-chart` (`primeng/chart`, needs the `chart.js` peer dep), Table renders a `p-table` pivot. Each
tab renders one view **per group present in the data** (derived client-side from `groupId`, sorted
by name, with an **Ungrouped** section last that also absorbs Uncategorized rows): Monthly = grouped
bar (categories on X, one bar per picked month), Yearly = line (12 months of a chosen year, one line
per category), Table = a
spreadsheet-style pivot for a chosen year (category rows × 12 month columns + a right-hand **Sum**
column, plus a bottom **Total** row; frozen first column, negatives in the danger colour, rows
sorted by magnitude). Totals are shown as-is (spending negative, income positive); an inline
chart.js plugin (`valueLabelsPlugin`, passed via the PrimeNG `[plugins]` input) prints each
bar/point's value. One fetch, all selection client-side. Add a page by creating
`pages/<name>/<name>.ts`, a route in `app.routes.ts`, and a `MenuItem` in `app.ts`.

**Build integration & serving (single jar):** `build.gradle` uses the `com.github.node-gradle.node`
plugin (it downloads a pinned **Node 26.4.0** for reproducibility). `frontendBuild` runs the npm
build; `copyFrontend` stages the output under `build/frontend-resources/static/`, which is wired in
as a `main` resources source dir so `processResources` (and thus `bootJar`/`bootRun`) bundle it at
`classpath:/static/`. `web/WebConfig.kt` serves those files and falls back to `index.html` for
non-API, non-file paths so Angular's HTML5 deep links survive a refresh; unknown `api` paths still
404. Skip the whole frontend build with `-PskipFrontend`.

**PWA (installable on mobile):** the app is an installable Progressive Web App via
`@angular/service-worker`. `provideServiceWorker('ngsw-worker.js', …)` in `app.config.ts` registers
the worker **only in production builds** (`enabled: !isDevMode()`, so `npm start` on :4200 has no
SW) and only after the app stabilizes (`registerWhenStable:30000`) so it never delays first paint. `angular.json` sets `"serviceWorker": "ngsw-config.json"` on the
`production` config only. `ngsw-config.json` precaches the app shell + hashed JS/CSS and lazily
caches `/icons/**` and media; it has **no `dataGroups`, so `/api/**` responses are never cached**
(no stale financial data), and `!/api/**` in `navigationUrls` keeps API paths out of
the index.html navigation fallback. `public/manifest.webmanifest` (name/theme `#10b981`, standalone,
`/icons/*`) plus iOS `apple-touch-icon` + `apple-mobile-web-app-*` meta tags in `index.html` cover
Android and iOS home-screen install. Icons in `public/icons/` were rasterized from `icon.svg`
(rounded, `purpose:any`) and `icon-maskable.svg` (full-bleed safe-zone, `purpose:maskable`) via
macOS `sips`. The SW activates only over the served jar (secure-context requirement met by
`localhost`/HTTPS), so test it with `./gradlew bootRun`, not the dev server.

## Conventions

- Services and finders are `open class` with `@Transactional` `open fun` (no `@Service`/`@Component`
  annotations — they're plain classes wired by `BankStatementConfig`); they need `open` for Spring
  proxying. Keep this pattern when adding new ones.
- Command (write) vs query (read) are separated: root package = commands, `query/` = reads.
- API base path: `/api/v1/...`.
