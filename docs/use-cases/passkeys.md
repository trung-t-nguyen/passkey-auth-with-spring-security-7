# Use Case: Passkeys (WebAuthn) Authentication — SPA

Reference: https://www.baeldung.com/spring-security-integrate-passkeys

## Goal

Enable passwordless login via passkeys (WebAuthn/FIDO2) using Spring Security's built-in support in a **SPA + REST API** architecture. The Spring Boot backend exposes JSON REST endpoints; the frontend SPA drives all browser WebAuthn interactions via JavaScript.

## Flow

### Initial registration (first-time user)
1. SPA calls `POST /api/login` with `{ username, password }` → server creates a session, returns `{ username }`
2. SPA calls `POST /webauthn/register/options` → server returns a `PublicKeyCredentialCreationOptions` JSON object
3. SPA calls `navigator.credentials.create(options)` — browser prompts the user to create a passkey (biometrics / PIN)
4. SPA calls `POST /webauthn/register` with the new credential → server stores it; returns success

### Passkey login (returning user)
1. SPA calls `POST /webauthn/authenticate/options` with `{ username }` → server returns `PublicKeyCredentialRequestOptions`
2. SPA calls `navigator.credentials.get(options)` — browser prompts the user to authenticate
3. SPA calls `POST /webauthn/authenticate` with the assertion → server validates and creates a session; returns `{ username }`
4. SPA navigates to the home/dashboard view

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
- **No Thymeleaf** — the frontend is a static SPA served separately (or from `src/main/resources/static/`)

### `src/main/resources/application.yaml`
- Configure PostgreSQL datasource (matches docker-compose credentials)
- JPA `ddl-auto: validate` — Flyway owns the schema
- CORS allowed origin for the SPA dev server (e.g., `http://localhost:5173`)

### `src/main/kotlin/com/ttng/demo_web_auth/domain/User.kt` (new)
- JPA entity mapped to `users` table
- Fields: `id` (UUID, generated), `username` (unique), `password` (encoded), `enabled`

### `src/main/kotlin/com/ttng/demo_web_auth/domain/UserRepository.kt` (new)
- Spring Data JPA repository with `findByUsername(username: String): User?`

### `src/main/kotlin/com/ttng/demo_web_auth/config/SecurityConfig.kt` (new)
- `SecurityFilterChain` bean with:
  - `.webAuthn()` — `rpId = "localhost"`, `rpName`, `allowedOrigins = "http://localhost:8080"`
  - `.formLogin { it.disable() }` — no server-rendered login page
  - `httpBasic { it.disable() }`
  - CORS configured via `CorsConfigurationSource` bean (allow SPA origin, credentials)
  - CSRF: disabled or configured with `CookieCsrfTokenRepository` for SPA
  - `exceptionHandling`:
    - `authenticationEntryPoint` → return HTTP 401 JSON (`{ "error": "Unauthorized" }`) instead of redirect
    - `accessDeniedHandler` → return HTTP 403 JSON
  - `authorizeHttpRequests` — permit `/api/login`, `/webauthn/**`, `/`, `/index.html`, `/*.js`, `/*.css`; protect `/api/**`
- `UserDetailsService` bean backed by `UserRepository`
- `PasswordEncoder` bean — `BCryptPasswordEncoder`
- `PublicKeyCredentialUserEntityRepository` bean — `JdbcPublicKeyCredentialUserEntityRepository`
- `UserCredentialRepository` bean — `JdbcUserCredentialRepository`

### `src/main/kotlin/com/ttng/demo_web_auth/web/AuthController.kt` (new)
- `POST /api/login` — accepts `{ username, password }` JSON, authenticates via `AuthenticationManager`, creates session, returns `{ username }`
- `GET /api/me` — returns current authenticated user or 401
- `POST /api/logout` — invalidates session, returns 200

### `src/main/resources/db/migration/` (new Flyway migrations)
- `V1__create_users.sql` — create `users` table (`id` UUID PK, `username` unique, `password`, `enabled`)
- `V2__create_webauthn_schema.sql` — Spring Security WebAuthn JDBC schema (`user_entities`, `user_credentials`)
- `V3__seed_test_user.sql` — insert test user with BCrypt-encoded password

### `src/main/resources/static/` (new SPA frontend)
- `index.html` — single-page shell
- `app.js` — SPA logic:
  - Login form: calls `POST /api/login`
  - Registration: calls WebAuthn registration endpoints, then `navigator.credentials.create()`
  - Passkey login: calls WebAuthn authentication endpoints, then `navigator.credentials.get()`
  - Home view: displays username, logout button, register-passkey button

## Constraints / Notes

- `rpId` must match the hostname exactly. For local dev: `localhost`.
- `allowedOrigins` in `.webAuthn()` must be `http://localhost:8080` (the origin that serves the SPA during dev). If the SPA runs on a different port, add it here.
- CORS must allow credentials (`allowCredentials = true`) so the browser sends the session cookie.
- CSRF: for a same-origin SPA served from the same Spring Boot origin it can be disabled in dev; for cross-origin dev (e.g., Vite on `:5173`) use `CookieCsrfTokenRepository.withHttpOnlyFalse()` and read the `XSRF-TOKEN` cookie in JS.
- Use JDBC credential repositories so registered passkeys survive restarts.
- Flyway runs as a Docker Compose service (not embedded in the app) — migrations complete before the app starts.
- JPA `ddl-auto` must be `validate` or `none` — Flyway owns the schema.
- The WebAuthn challenge-response JSON shapes are defined by the W3C spec; use `JSON.parse` / `JSON.stringify` and `btoa`/`atob` for Base64URL encoding on the frontend.

## Status

- [x] docker-compose.yml — PostgreSQL 17
- [x] build.gradle.kts — add PostgreSQL driver
- [x] application.yaml — PostgreSQL datasource
- [ ] application.yaml — set JPA ddl-auto to validate (Flyway owns schema)
- [ ] application.yaml — CORS allowed origin for SPA dev server
- [ ] docker-compose.yml — add Flyway service
- [ ] V1__create_users.sql — users table
- [ ] V2__create_webauthn_schema.sql — Spring Security WebAuthn JDBC schema
- [ ] V3__seed_test_user.sql — seed test user with BCrypt password
- [ ] User.kt — JPA entity
- [ ] UserRepository.kt — Spring Data JPA repository
- [ ] SecurityConfig.kt — WebAuthn, CORS, JSON error responses, session-based auth
- [ ] AuthController.kt — `/api/login`, `/api/me`, `/api/logout` REST endpoints
- [ ] index.html + app.js — SPA frontend with passkey registration and login flows
