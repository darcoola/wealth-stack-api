# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 4.0.2 (web + data-jpa) personal-finance backend that imports bank statement CSV
exports. Kotlin 2.3.0 targeting JDK 25. Package namespace: `com.wealthStack`. A single-page UI
(**Angular 21 + PrimeNG 21**) lives in `frontend/` and is built into the jar so the app serves
both the REST API and the UI on `:8088`.

**Read `ARCHITECTURE.md` first** — it maps the domain model, packages, import/query flows, and
bank parsers so you don't have to re-read every file each session. Keep it up to date when the
model, endpoints, or parsers change.

## Build Commands

```bash
./gradlew build                  # Build the project (also builds + bundles the Angular UI)
./gradlew build -PskipFrontend   # Backend-only build (skip the slow npm build)
./gradlew test                   # Run all tests
./gradlew clean                  # Clean build artifacts
./gradlew compileKotlin          # Compile Kotlin sources only
./gradlew bootRun                # Run the app (API + UI) on :8088
```

Run a single test class:
```bash
./gradlew test --tests "com.wealthStack.SomeTest"
```

Frontend (Angular) — work inside `frontend/`:
```bash
cd frontend && npm start         # Dev server on :4200, proxies /api → :8088 (run bootRun too)
cd frontend && npm run build     # Production build → frontend/dist/frontend/browser
```

## Architecture

See `ARCHITECTURE.md`. Key points:
- Build uses Gradle **Groovy DSL** (`build.gradle`/`settings.gradle`), not Kotlin DSL.
- Beans are wired explicitly in `BankStatementConfig` (no `@Service`/`@Component` scanning for
  services/controllers); register new ones there.
- PostgreSQL in dev/prod (auto-started via `compose.yaml`); tests run against in-memory H2.
- Schema is owned by **Flyway** (`src/main/resources/db/migration`); Hibernate runs in
  `ddl-auto: validate`. Any entity change needs a new `V<n>__...sql` migration — never edit an
  applied one.
- Testing: JUnit 5 (`kotlin-test`) + Spring Boot Test + assertk.
- Frontend SPA in `frontend/` (Angular 21 + PrimeNG 21); Gradle's `node` plugin builds it into
  `classpath:/static/`, and `web/WebConfig.kt` adds an `index.html` fallback for SPA deep links.