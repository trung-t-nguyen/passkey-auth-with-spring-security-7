# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application
./gradlew bootRun

# Build (produces executable JAR in build/libs/)
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.ttng.demo_web_auth.DemoWebAuthApplicationTests"

# Clean build artifacts
./gradlew clean
```

Java 21 is required (configured via Gradle toolchain).

## Architecture

This is a **Spring Boot 4 / Kotlin** demo project for **WebAuthn/Passkeys** passwordless authentication.

**Stack:**
- Spring Boot 4.0.6 + Kotlin 2.2.21
- Spring Security WebAuthn (`spring-security-webauthn`) for passkey auth
- Spring Data JPA / Hibernate 7 for persistence
- Spring MVC for REST endpoints

**Package root:** `com.ttng.demo_web_auth`

**Key architectural notes:**
- The `allOpen` Kotlin compiler plugin is configured so JPA entity classes don't need to be explicitly open.
- JSR305 strict null-safety is enabled (`-Xjsr305=strict`) — treat all Spring/JPA annotations as null-safe.
- Configuration lives in `src/main/resources/application.yaml`.
- The project is a starter skeleton; controllers, services, and entities are not yet implemented.