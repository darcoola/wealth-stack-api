# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin JVM project using Gradle 9.2.0. Kotlin 2.3.0 targeting JDK 25. Package namespace: `com.wealthStack`.

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

- Standard Gradle/Kotlin project layout (`src/main/kotlin`, `src/test/kotlin`)
- Testing with JUnit 5 via `kotlin-test`
- Foojay toolchain resolver for JDK provisioning
- Official Kotlin code style enforced (configured in `gradle.properties`)