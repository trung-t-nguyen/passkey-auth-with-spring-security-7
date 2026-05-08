# demo-web-auth

A Spring Boot / Kotlin demo for **WebAuthn/Passkeys** passwordless authentication using Spring Security's built-in support.

## Use Cases

- [Passkeys (WebAuthn) Authentication](docs/use-cases/passkeys.md)

## Getting Started

```bash
# Start the database
docker-compose up -d

# Run the application
./gradlew bootRun
```

Java 21 is required.

## Reference Documentation

- [WebAuthn for Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)
- [Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/4.0.6/gradle-plugin)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.6/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Gradle Build Scans](https://scans.gradle.com#gradle)