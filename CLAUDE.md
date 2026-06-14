# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 4.0.2 (web + data-jpa) personal-finance backend that imports bank statement CSV
exports. Kotlin 2.3.0 targeting JDK 25. Package namespace: `com.wealthStack`.

**Read `ARCHITECTURE.md` first** — it maps the domain model, packages, import/query flows, and
bank parsers so you don't have to re-read every file each session. Keep it up to date when the
model, endpoints, or parsers change.

## Build Commands

```bash
./gradlew build          # Build the project
./gradlew test           # Run all tests
./gradlew clean          # Clean build artifacts
./gradlew compileKotlin  # Compile Kotlin sources only
```

Run a single test class:
```bash
./gradlew test --tests "com.wealthStack.SomeTest"
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