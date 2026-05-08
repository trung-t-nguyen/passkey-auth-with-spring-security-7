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

## Implementation

### `docker-compose.yml`

Two services:
- **postgres** — PostgreSQL 17 Alpine on port `5432`, DB `demo_web_auth`, user/password `demo/demo`, named volume `postgres_data`, healthcheck via `pg_isready`
- **flyway** — `flyway/flyway:11` image, runs `migrate` and exits, mounts `./src/main/resources/db/migration` into `/flyway/sql`, `depends_on: postgres` with `condition: service_healthy`

### `build.gradle.kts`

Dependencies:
- `implementation("org.springframework.boot:spring-boot-starter-data-jpa")`
- `implementation("org.springframework.boot:spring-boot-starter-security")`
- `implementation("org.springframework.boot:spring-boot-starter-webmvc")`
- `implementation("org.springframework.boot:spring-boot-starter-thymeleaf")`
- `implementation("org.springframework.security:spring-security-webauthn")`
- `implementation("tools.jackson.module:jackson-module-kotlin")`
- `runtimeOnly("org.postgresql:postgresql")`

### `src/main/resources/application.yaml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/demo_web_auth
    username: demo
    password: demo
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway owns the schema; Hibernate only validates
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

### Database Schema (Flyway)

#### `V1__create_users.sql` — application user accounts

```sql
CREATE TABLE users (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    username   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT users_username_unique UNIQUE (username)
);
```

#### `V2__create_webauthn_schema.sql` — Spring Security WebAuthn JDBC tables

Schema derived from `JdbcPublicKeyCredentialUserEntityRepository` and `JdbcUserCredentialRepository` in `spring-security-webauthn:7.0.5` (extracted via `javap`).

```sql
CREATE TABLE user_entities (
    id           TEXT NOT NULL,          -- opaque Bytes, stored as base64url string
    name         VARCHAR(255) NOT NULL,  -- matches application username
    display_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT user_entities_name_unique UNIQUE (name)
);

CREATE TABLE user_credentials (
    credential_id                TEXT      NOT NULL,
    user_entity_user_id          TEXT      NOT NULL REFERENCES user_entities (id),
    public_key                   BYTEA     NOT NULL,
    signature_count              BIGINT    NOT NULL DEFAULT 0,
    uv_initialized               BOOLEAN   NOT NULL DEFAULT FALSE,
    backup_eligible              BOOLEAN   NOT NULL DEFAULT FALSE,
    authenticator_transports     TEXT,
    public_key_credential_type   TEXT,
    backup_state                 BOOLEAN   NOT NULL DEFAULT FALSE,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP NOT NULL,
    last_used                    TIMESTAMP,
    label                        TEXT      NOT NULL,
    PRIMARY KEY (credential_id)
);
```

**Why two user tables?**
- `users` — application identity: username, BCrypt password, first/last name, enabled flag. Used by `UserDetailsService` for form login.
- `user_entities` — WebAuthn/FIDO2 protocol identity: maps an opaque user handle (`id`, random bytes as base64url) to a `name` (username). Required by the FIDO2 spec so the browser can associate credentials with a user without exposing the username. Created automatically on first passkey registration.
- The join key between them is `users.username = user_entities.name`.

#### `V3__seed_test_user.sql` — test credentials

Three test users, all with password `password`:

```sql
INSERT INTO users (username, password, first_name, last_name, enabled) VALUES
    ('alice', '$2a$10$dlHceSp...', 'Alice', 'Smith',   TRUE),
    ('bob',   '$2a$10$KtexYg4...', 'Bob',   'Jones',   TRUE),
    ('carol', '$2a$10$yTYC4qN...', 'Carol', 'Johnson', TRUE);
```

BCrypt hashes generated at project init time using `BCryptPasswordEncoder().encode("password")`.

### `domain/User.kt`

JPA entity for the `users` table. Kotlin's `allOpen` plugin (configured for `@Entity`) makes the class open without the explicit `open` keyword.

```kotlin
@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(unique = true, nullable = false) val username: String,
    @Column(nullable = false) val password: String,
    @Column(nullable = false) val firstName: String,
    @Column(nullable = false) val lastName: String,
    @Column(nullable = false) val enabled: Boolean = true,
)
```

Hibernate maps `firstName`/`lastName` to `first_name`/`last_name` via its default camelCase-to-snake_case naming strategy.

### `domain/UserRepository.kt`

```kotlin
interface UserRepository : JpaRepository<User, UUID> {
    fun findByUsername(username: String): User?
}
```

### `config/SecurityConfig.kt`

```kotlin
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .webAuthn { it.rpId("localhost").rpName("Demo Web Auth").allowedOrigins("http://localhost:8080") }
        .formLogin { }
        .authorizeHttpRequests { it.requestMatchers("/login").permitAll().anyRequest().authenticated() }
    return http.build()
}
```

Key beans:
- **`userDetailsService`** — lambda, delegates to `UserRepository.findByUsername`. Builds `UserDetails` from `username`, `password`, and `enabled`. `firstName`/`lastName` are not part of `UserDetails` — retrieve them from `UserRepository` if needed in a controller.
- **`passwordEncoder`** — `BCryptPasswordEncoder()`
- **`publicKeyCredentialUserEntityRepository`** — `JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations)` — persists WebAuthn user entities to `user_entities`
- **`userCredentialRepository`** — `JdbcUserCredentialRepository(jdbcOperations)` — persists passkey credentials to `user_credentials`; `setLobHandler` setter exists but not required

Spring Security's built-in `/login` page automatically renders passkey UI when `.webAuthn()` is configured. No custom JS needed.

### `web/HomeController.kt`

```kotlin
@GetMapping("/")
fun home(authentication: Authentication, model: Model): String {
    model.addAttribute("username", authentication.name)
    return "home"
}
```

Uses `Authentication` (not `@AuthenticationPrincipal UserDetails`) because `@AuthenticationPrincipal` returns `null` when the principal type doesn't match — e.g. after WebAuthn authentication. `Authentication.name` returns the username regardless of auth method.

To display `firstName`/`lastName`, inject `UserRepository` and call `findByUsername(authentication.name)`.

### `templates/home.html`

Thymeleaf template showing authenticated username, a link to `/webauthn/register`, and a logout form (`POST /logout` — Spring Security's CSRF protection requires a form POST for logout, not a plain link).

## Constraints / Notes

- `rpId` must match the origin hostname exactly — `localhost` for local dev, the actual domain in production.
- `allowedOrigins` must be `http://localhost:8080` for local dev; production requires HTTPS.
- `JdbcPublicKeyCredentialUserEntityRepository` and `JdbcUserCredentialRepository` take a `JdbcOperations` auto-configured by Spring Boot. No manual `DataSource` wiring needed.
- Flyway runs as a Docker Compose service, not embedded — all migrations complete before the app starts.
- `ddl-auto: validate` means Hibernate fails fast on startup if entities don't match the schema. Good safety net.
- To reset the DB (e.g. after schema changes): `docker-compose down -v && docker-compose up -d`

## Status

All items complete.

- [x] docker-compose.yml — PostgreSQL 17 + Flyway service
- [x] build.gradle.kts — PostgreSQL driver + Thymeleaf
- [x] application.yaml — datasource + `ddl-auto: validate`
- [x] V1__create_users.sql — users table (id, username, password, first_name, last_name, enabled)
- [x] V2__create_webauthn_schema.sql — Spring Security WebAuthn JDBC schema (user_entities + user_credentials)
- [x] V3__seed_test_user.sql — 3 test users (alice, bob, carol) with BCrypt password
- [x] User.kt — JPA entity with firstName, lastName
- [x] UserRepository.kt — Spring Data JPA repository
- [x] SecurityConfig.kt — WebAuthn + form login, JDBC credential repos, BCrypt encoder
- [x] HomeController.kt — uses `Authentication.name` (works for both form login and passkey auth)
- [x] home.html — welcome page with passkey registration link and logout
