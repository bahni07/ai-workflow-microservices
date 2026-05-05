# Implementation Plan: User Service (Phase 3)

## Overview

Build the `user-service` standalone Spring Boot microservice providing JWT-based user registration and login. Tasks are ordered so the data layer comes first, then security infrastructure, then business logic, then the API layer, and finally tests and gateway integration.

## Tasks

- [x] 1. Project scaffold
  - [x] 1.1 Create `services/user-service/pom.xml` with Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Spring Validation, SpringDoc OpenAPI 2.5.0, Flyway, PostgreSQL driver, JJWT 0.12.5, Spring Cloud Eureka client, jqwik 1.8.4, Testcontainers, and Mockito dependencies
  - [x] 1.2 Create main application class `UserServiceApplication` under `com.aiworkflow.user`
  - [x] 1.3 Create `application.yml` with datasource, JPA, Flyway, SpringDoc, Eureka client (`spring.application.name: user-service`, `defaultZone: http://localhost:8761/eureka/`), and `jwt.secret` / `jwt.expiration-ms` properties
  - _Requirements: 5.1, 5.2, 6.1, 6.2, 8.1, 8.2_

- [x] 2. Domain model and database migration
  - [x] 2.1 Create `User` JPA entity in `entity/` with `id` (UUID, generated), `username` (unique, max 50), `email` (unique), `password` (BCrypt hash), and `@Version` field for optimistic locking
  - [x] 2.2 Create Flyway migration `V1__create_users.sql` with `users` table, UUID primary key, `version` column, and unique constraints on `username` and `email`
  - _Requirements: 5.3, 5.4, 5.5, 5.6_

- [x] 3. Repository layer
  - [x] 3.1 Create `UserRepository` extending `JpaRepository<User, UUID>` with `findByUsername(String username)`, `existsByUsername(String username)`, and `existsByEmail(String email)` methods
  - _Requirements: 1.2, 1.3, 2.2_

- [x] 4. DTOs
  - [x] 4.1 Create `RegisterRequest` record with `@NotBlank @Size(min=3, max=50) String username`, `@Email @NotBlank String email`, and `@NotBlank @Size(min=8, max=100) @ValidPassword String password`
  - [x] 4.2 Create `@ValidPassword` custom Bean Validation constraint and validator that enforces: ≥1 uppercase letter, ≥1 lowercase letter, ≥1 digit, ≥1 special character — returns HTTP 400 on violation
  - [x] 4.3 Create `LoginRequest` record with `@NotBlank String username` and `@NotBlank String password`
  - [x] 4.4 Create `AuthResponse` record with `UUID userId`, `String username`, and `String token` (documented as Bearer token)
  - _Requirements: 1.1, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.1, 2.4, 8.4_

- [x] 5. Exception classes and GlobalExceptionHandler
  - [x] 5.1 Create `UserAlreadyExistsException` in `exception/`
  - [x] 5.2 Create `InvalidCredentialsException` in `exception/`
  - [x] 5.3 Create `GlobalExceptionHandler` (`@ControllerAdvice`) mapping `UserAlreadyExistsException` → 409, `InvalidCredentialsException` → 401, `DataIntegrityViolationException` → 409 (concurrent duplicate registration race condition), `MethodArgumentNotValidException` → 400, `HttpMessageNotReadableException` → 400, and catch-all `Exception` → 500 without exposing stack traces
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 6. JWT infrastructure and security configuration
  - [x] 6.1 Create `JwtProperties` class annotated with `@ConfigurationProperties(prefix = "jwt")` binding `secret` (from `JWT_SECRET` env var) and `expirationMs` (from `JWT_EXPIRATION_MS` env var); document that secret must be ≥ 256 bits
  - [x] 6.2 Create `JwtService` with `generateToken(String username)` (HMAC-SHA256 signed, `sub`=username, `iat`=now, `exp`=now+expirationMs), `extractUsername(String token)` (extracts `sub` claim), and `isTokenValid(String token)` using JJWT 0.12.5
  - [x] 6.3 Create `SecurityConfig` (`@Configuration @EnableWebSecurity`) configuring stateless session policy, CSRF disabled, `/auth/**` + `/actuator/**` + `/swagger-ui/**` + `/v3/api-docs/**` permitted, all other requests authenticated, and a `BCryptPasswordEncoder` bean with configurable strength (default 10 rounds via `security.bcrypt-strength`)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9_

- [x] 7. Service layer
  - [x] 7.1 Create `AuthService` interface in `service/` with `register(RegisterRequest)` and `login(LoginRequest)` returning `AuthResponse`
  - [x] 7.2 Implement `AuthServiceImpl` in `service/impl/` — `register`: call `existsByUsername` and `existsByEmail` (throw `UserAlreadyExistsException` on conflict), encode password with `BCryptPasswordEncoder`, save `User`, call `JwtService.generateToken`, return `AuthResponse`; annotate with `@Transactional`
  - [x] 7.3 Implement `AuthServiceImpl` — `login`: call `findByUsername` (throw `InvalidCredentialsException` if absent), call `BCryptPasswordEncoder.matches` (throw `InvalidCredentialsException` if mismatch), call `JwtService.generateToken`, return `AuthResponse`; both failure paths return the same exception to prevent user enumeration
  - _Requirements: 1.1, 1.2, 1.3, 1.10, 1.11, 1.12, 2.1, 2.2, 2.3, 2.5_

- [x] 8. Controller layer
  - [x] 8.1 Create `AuthController` with `POST /auth/register` (HTTP 201, `@Valid RegisterRequest`) and `POST /auth/login` (HTTP 200, `@Valid LoginRequest`)
  - [x] 8.2 Add SpringDoc `@Operation` and `@ApiResponse` annotations to both endpoints documenting success and error responses
  - _Requirements: 1.1, 2.1, 8.1, 8.2, 8.3, 8.4_

- [x] 9. Unit tests
  - [x] 9.1 Write unit tests for `AuthController` using `@WebMvcTest` / `MockMvc` — register 201, blank username 400, invalid email 400, duplicate username 409, login 200, wrong password 401
  - [x] 9.2 Write unit tests for `JwtService` — `generateToken` returns non-blank string, `extractUsername` returns correct subject, `isTokenValid` returns true for fresh token and false for tampered token
  - _Requirements: 1.1, 1.4, 1.6, 1.9, 2.1, 2.3, 3.2, 3.4, 3.5_

- [x] 10. Property-based tests (jqwik)
  - [x] 10.1 Write property test for Property 1 — for any valid registration, returned token is a valid JWT containing the registered username
    - **Property 1: Registered user token is valid and contains correct username**
    - **Validates: Requirements 1.1, 3.2, 3.5**
  - [x] 10.2 Write property test for Property 2 — for any registered username, re-registering with same username returns 409
    - **Property 2: Duplicate username is rejected**
    - **Validates: Requirements 1.2, 5.4**
  - [x] 10.3 Write property test for Property 3 — for any registered email, re-registering with same email returns 409
    - **Property 3: Duplicate email is rejected**
    - **Validates: Requirements 1.3, 5.5**
  - [x] 10.4 Write property test for Property 4 — for any non-existent username or wrong password, login returns 401
    - **Property 4: Invalid credentials are rejected**
    - **Validates: Requirements 2.2, 2.3, 2.5**
  - [x] 10.5 Write property test for Property 5 — for any registered user, stored password hash does not equal plaintext
    - **Property 5: Password is never stored in plaintext**
    - **Validates: Requirements 1.9**
  - [x] 10.6 Write property test for Property 6 — for any invalid registration input, service returns 400 and no user is created
    - **Property 6: Validation rejects invalid inputs**
    - **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**
  - [x] 10.7 Write property test for Property 7 — for any username string, generateToken then extractUsername returns original username
    - **Property 7: JWT token round-trip**
    - **Validates: Requirements 3.1, 3.2, 3.4, 3.5**

- [x] 11. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Integration tests (Testcontainers)
  - [x] 12.1 Write integration test for full register flow against real PostgreSQL — assert HTTP 201 and returned token is non-blank
  - [x] 12.2 Write integration test for duplicate username — register twice with same username, assert second attempt returns HTTP 409
  - [x] 12.3 Write integration test for full login flow — register then login, assert HTTP 200 and returned token is non-blank
  - [x] 12.4 Write integration test for wrong password — register then login with wrong password, assert HTTP 401
  - [x] 12.5 Write integration test verifying Flyway migration `V1__create_users.sql` applies cleanly on startup
  - _Requirements: 1.1, 1.2, 2.1, 2.3, 5.1, 5.2_

- [x] 13. API Gateway route update
  - [x] 13.1 Add route to `services/api-gateway/src/main/resources/application.yml` — id `user-service`, uri `lb://user-service`, predicate `Path=/api/auth/**`, filter `StripPrefix=1`
  - _Requirements: 7.1, 7.2, 7.3_

- [x] 14. Final checkpoint — Ensure all tests pass
  - Run `mvn test` across `user-service` and `api-gateway`; ensure all tests pass, ask the user if questions arise.

- [x] 15. Manual verification
  - [x] 15.1 Start `eureka-server`, `api-gateway`, and `user-service` (in that order); verify `user-service` appears in the Eureka dashboard at `http://localhost:8761`
  - [x] 15.2 Test `POST /auth/register` via Swagger UI at `http://localhost:8081/swagger-ui.html` — verify HTTP 201 and a JWT token in the response
  - [x] 15.3 Test `POST /auth/login` via Swagger UI — verify HTTP 200 and a JWT token in the response
  - [x] 15.4 Test duplicate registration via Swagger UI — verify HTTP 409 is returned
  - [x] 15.5 Test login with wrong password via Swagger UI — verify HTTP 401 is returned
  - [x] 15.6 Call `POST http://localhost:8090/api/auth/register` through the gateway — verify the request is routed correctly and returns HTTP 201

## Notes

- Ensure port 8081 is free before starting `user-service` locally
- JJWT 0.12.5 requires `jjwt-api`, `jjwt-impl`, and `jjwt-jackson` artifacts; `jjwt-impl` and `jjwt-jackson` must be `runtime` scope
- `jwt.secret` MUST NOT be hardcoded in source code — provide via `JWT_SECRET` environment variable. The default value in `application.yml` is for local development only
- `jwt.secret` must be at least 256 bits (32 bytes / 64 hex characters) for HMAC-SHA256
- `spring-boot-starter-web` is required (not WebFlux) — `user-service` is a standard MVC service
- The `StripPrefix=1` filter on the gateway route removes the `/api` prefix so the user-service receives `/auth/register` not `/api/auth/register`
- Clients use the JWT as a Bearer token: `Authorization: Bearer <token>` — document this in Swagger
- BCrypt strength defaults to 10 rounds; configurable via `security.bcrypt-strength` — higher values increase security at the cost of latency
- Username comparisons are case-sensitive — `Alice` and `alice` are distinct users
- Rate limiting is out of scope for Phase 3; note this in API documentation
- Testcontainers requires Docker to be running locally for integration tests
- jqwik property tests should use `@Property(tries = 100)` minimum; use `@ForAll @AlphaChars @StringLength(min=3, max=50)` for username generation
