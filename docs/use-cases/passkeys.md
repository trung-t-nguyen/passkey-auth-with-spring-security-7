# Use Case: Passkeys (WebAuthn) Authentication

Reference: https://www.baeldung.com/spring-security-integrate-passkeys

## Goal

Enable passwordless login via passkeys (WebAuthn/FIDO2) using Spring Security's built-in support. Users first log in with a username/password, register a passkey, then can authenticate with biometrics/device PIN on subsequent visits.

## Flow

1. User visits `/` — redirected to `/login`
2. User logs in with username + password (form login)
3. After login, user visits `/webauthn/register` to register a passkey
4. Spring Security prompts the browser to create and store a passkey credential
5. On next visit, user can authenticate at `/login` using their passkey (no password needed)

## Changes Required

### `docker-compose.yml` (new)
- PostgreSQL 17 service on port 5432, DB `demo_web_auth`, user/password `demo/demo`
- Named volume for data persistence across restarts
- Healthcheck via `pg_isready`
- Flyway service (`flyway/flyway` image) that runs migrations and exits
  - `depends_on: postgres` with `condition: service_healthy`
  - Mounts `./src/main/resources/db/migration` into `/flyway/sql`
  - Connects to postgres using internal Docker network hostname

### `build.gradle.kts`
- Add `runtimeOnly("org.postgresql:postgresql")` — PostgreSQL JDBC driver
- Add `implementation("org.springframework.boot:spring-boot-starter-thymeleaf")` — server-side templates for home page

### `src/main/resources/application.yaml`
- Configure PostgreSQL datasource (matches docker-compose credentials)
- JPA `ddl-auto: update` — auto-create/migrate schema on startup

### `src/main/kotlin/com/ttng/demo_web_auth/domain/User.kt` (new)
- JPA entity mapped to `users` table
- Fields: `id` (UUID, generated), `username` (unique), `password` (encoded), `enabled`

### `src/main/kotlin/com/ttng/demo_web_auth/domain/UserRepository.kt` (new)
- Spring Data JPA repository with `findByUsername(username: String): User?`

### `src/main/kotlin/com/ttng/demo_web_auth/config/SecurityConfig.kt` (new)
- `SecurityFilterChain` bean with:
  - `.webAuthn()` — `rpId = "localhost"`, `rpName`, `allowedOrigins = "http://localhost:8080"`
  - `.formLogin()` — fallback password-based login to allow initial passkey registration
  - `authorizeHttpRequests` — permit `/login`, protect everything else
- `UserDetailsService` bean — `JdbcUserDetailsService` backed by `UserRepository` (loads users from PostgreSQL `users` table)
- `PasswordEncoder` bean — `BCryptPasswordEncoder`
- `PublicKeyCredentialUserEntityRepository` bean — `JdbcPublicKeyCredentialUserEntityRepository` (backed by PostgreSQL)
- `UserCredentialRepository` bean — `JdbcUserCredentialRepository` (backed by PostgreSQL)
- WebAuthn JDBC schema is handled by Flyway migration `V2`

### `src/main/resources/db/migration/` (new Flyway migrations)
- `V1__create_users.sql` — create `users` table (`id` UUID PK, `username` unique, `password`, `enabled`)
- `V2__create_webauthn_schema.sql` — Spring Security WebAuthn JDBC schema (tables for `user_entities` and `user_credentials`)
- `V3__seed_test_user.sql` — insert test user with BCrypt-encoded password

### `src/main/kotlin/com/ttng/demo_web_auth/web/HomeController.kt` (new)
- `GET /` — returns `home` view, exposes authenticated username via model

### `src/main/resources/templates/home.html` (new)
- Shows authenticated username
- Link to `/webauthn/register` for passkey registration
- Logout form

## Constraints / Notes

- `rpId` must match the hostname exactly. For local dev: `localhost`.
- `allowedOrigins` must use `http://localhost:8080` for local dev (HTTPS required in production).
- Use JDBC credential repositories (`JdbcPublicKeyCredentialUserEntityRepository`, `JdbcUserCredentialRepository`) so registered passkeys survive restarts.
- Spring Security's built-in `/login` page already renders passkey authentication UI when `webAuthn` is configured — no custom JS required.
- Users are stored in a `users` JPA entity/table; passwords are BCrypt-encoded.
- Flyway runs as a Docker Compose service (not embedded in the Spring Boot app) — migrations complete before the app starts.
- Flyway migration scripts live in `src/main/resources/db/migration/` and are mounted into the Flyway container.
- JPA `ddl-auto` must be set to `validate` or `none` — Flyway owns the schema.

## Status

- [x] docker-compose.yml — PostgreSQL 17
- [x] build.gradle.kts — add PostgreSQL driver
- [x] application.yaml — PostgreSQL datasource
- [ ] build.gradle.kts — add Thymeleaf
- [ ] application.yaml — set JPA ddl-auto to validate (Flyway owns schema)
- [ ] docker-compose.yml — add Flyway service
- [ ] V1__create_users.sql — users table
- [ ] V2__create_webauthn_schema.sql — Spring Security WebAuthn JDBC schema
- [ ] V3__seed_test_user.sql — seed test user with BCrypt password
- [ ] User.kt — JPA entity for users table
- [ ] UserRepository.kt — Spring Data JPA repository
- [ ] SecurityConfig.kt — WebAuthn + form login, DB-backed UserDetailsService, BCryptPasswordEncoder
- [ ] HomeController.kt — protected home endpoint
- [ ] home.html — home page template
