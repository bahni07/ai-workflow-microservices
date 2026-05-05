# Requirements Document

## Introduction

The User Service is the Phase 3 microservice of the ai-workflow-microservices platform. It provides user registration and JWT-based authentication for the platform. The service exposes a REST API for registering new users and logging in with existing credentials, returning a signed JWT on success. It is independently deployable, backed by PostgreSQL with Flyway-managed schema migrations, registers with the Eureka Server for service discovery, and is accessible through the API Gateway. Security is stateless — no sessions are maintained server-side.

## Glossary

- **User_Service**: The Spring Boot microservice defined in this document, running on port 8081.
- **Auth_Service**: The component responsible for user registration and login business logic within the User_Service.
- **JWT_Service**: The component responsible for generating and validating JSON Web Tokens within the User_Service.
- **User**: A registered platform account with a unique username, unique email address, and a bcrypt-hashed password stored in the database. Fields: `id` (UUID), `username`, `email`, `passwordHash` (BCrypt), `version`.
- **AuthResponse**: The DTO returned on successful registration or login, containing `userId`, `username`, and a signed JWT `token`.
- **RegisterRequest**: The DTO submitted to register a new user, containing `username`, `email`, and `password`.
- **LoginRequest**: The DTO submitted to authenticate an existing user, containing `username` and `password`.
- **JWT**: A JSON Web Token signed with HMAC-SHA256, used as a stateless Bearer credential in the `Authorization` header. Contains `sub` (username), `iat` (issued-at), and `exp` (expiration) claims.
- **Bearer_Token**: The usage convention for JWTs — clients include the token as `Authorization: Bearer <token>` in request headers.
- **BCrypt**: The password hashing algorithm used to store passwords. Plaintext passwords are never persisted. Strength (work factor) is configurable.
- **GlobalExceptionHandler**: The `@ControllerAdvice` component that maps domain exceptions to HTTP error responses.
- **Eureka_Client**: The Spring Cloud Netflix Eureka client embedded in the User_Service that registers the service with the Eureka Server under the name `user-service`.
- **API_Gateway**: The Phase 2 Spring Cloud Gateway that routes `/api/auth/**` requests to the User_Service.

---

## Requirements

### Requirement 1: User Registration

**User Story:** As a new platform user, I want to register an account with a username, email, and password, so that I can authenticate and access protected platform resources.

#### Acceptance Criteria

1. WHEN a `POST /auth/register` request is received with a valid `username`, `email`, and `password`, THE User_Service SHALL create a new User, persist it to the database, and return HTTP 201 with an `AuthResponse` containing the user's `userId`, `username`, and a signed JWT `token`.
2. IF a `POST /auth/register` request is received with a `username` that already exists in the database, THEN THE User_Service SHALL return HTTP 409 with a descriptive error message and SHALL NOT create a new User.
3. IF a `POST /auth/register` request is received with an `email` that already exists in the database, THEN THE User_Service SHALL return HTTP 409 with a descriptive error message and SHALL NOT create a new User.
4. IF a `POST /auth/register` request is received with a blank `username`, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
5. IF a `POST /auth/register` request is received with a `username` shorter than 3 characters or longer than 50 characters, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
6. IF a `POST /auth/register` request is received with an `email` that does not conform to a valid email format, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
7. IF a `POST /auth/register` request is received with a blank `password`, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
8. IF a `POST /auth/register` request is received with a `password` shorter than 8 characters or longer than 100 characters, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
9. IF a `POST /auth/register` request is received with a `password` that does not contain at least one uppercase letter, one lowercase letter, one digit, and one special character, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
10. WHEN a User is registered, THE User_Service SHALL store the password as a BCrypt hash and SHALL NOT store the plaintext password.
11. THE User_Service SHALL assign a UUID as the unique identifier for each registered User.
12. WHEN two concurrent `POST /auth/register` requests arrive with the same `username` or `email`, THE database uniqueness constraint SHALL be the final authority — any duplicate insert attempt SHALL be caught and mapped to HTTP 409, ensuring no duplicate User record is created regardless of application-level race conditions.

---

### Requirement 2: User Login

**User Story:** As a registered platform user, I want to log in with my username and password, so that I can obtain a JWT to authenticate subsequent API requests.

#### Acceptance Criteria

1. WHEN a `POST /auth/login` request is received with a `username` and `password` that match a registered User, THE User_Service SHALL return HTTP 200 with an `AuthResponse` containing the user's `userId`, `username`, and a signed JWT `token`.
2. IF a `POST /auth/login` request is received with a `username` that does not exist in the database, THEN THE User_Service SHALL return HTTP 401 with a descriptive error message.
3. IF a `POST /auth/login` request is received with a `username` that exists but a `password` that does not match the stored BCrypt hash, THEN THE User_Service SHALL return HTTP 401 with a descriptive error message.
4. IF a `POST /auth/login` request is received with a blank `username` or blank `password`, THEN THE User_Service SHALL return HTTP 400 with a descriptive validation error message.
5. THE User_Service SHALL NOT reveal whether a failed login attempt was due to an unknown username or an incorrect password — both cases SHALL return HTTP 401 with the same generic error message.

---

### Requirement 3: JWT Token Generation and Validation

**User Story:** As a platform engineer, I want JWT tokens to be generated and validated consistently, so that downstream services can trust and verify user identity from the token alone.

#### Acceptance Criteria

1. WHEN a JWT is generated for a User, THE JWT_Service SHALL sign the token using HMAC-SHA256 with a secret key configured via `jwt.secret`.
2. WHEN a JWT is generated for a User, THE JWT_Service SHALL embed the `username` as the `sub` (subject) claim of the token.
3. WHEN a JWT is generated for a User, THE JWT_Service SHALL set the `iat` (issued-at) claim to the current timestamp and the `exp` (expiration) claim based on the `jwt.expiration-ms` configuration property.
4. WHEN a JWT token is validated, THE JWT_Service SHALL return `true` if and only if the token signature is valid and the token has not expired.
5. WHEN an expired JWT token is validated, THE JWT_Service SHALL return `false` and any endpoint requiring authentication SHALL return HTTP 401.
6. WHEN a JWT token's subject is extracted, THE JWT_Service SHALL return the `username` embedded in the token's `sub` claim.
7. THE JWT_Service SHALL be configurable via `jwt.secret` and `jwt.expiration-ms` properties bound through a `JwtProperties` configuration class.
8. THE `jwt.secret` value SHALL NOT be hardcoded in source code — it SHALL be provided via environment variable (`JWT_SECRET`) or external configuration, with a default value permitted only for local development.
9. THE `jwt.secret` SHALL be at least 256 bits (32 bytes) to satisfy HMAC-SHA256 minimum key length requirements.
10. No refresh token mechanism is implemented in Phase 3 — token renewal requires re-authentication via `POST /auth/login`.

---

### Requirement 4: Security Configuration

**User Story:** As a platform engineer, I want the User Service to enforce stateless security, so that no server-side session state is maintained and all protected endpoints require a valid JWT.

#### Acceptance Criteria

1. THE User_Service SHALL configure Spring Security with a stateless session policy — no `HttpSession` SHALL be created or used.
2. THE User_Service SHALL disable CSRF protection, as the service is a stateless REST API consumed by non-browser clients.
3. THE User_Service SHALL permit unauthenticated access to all `/auth/**` endpoints.
4. THE User_Service SHALL permit unauthenticated access to all `/actuator/**` endpoints.
5. THE User_Service SHALL permit unauthenticated access to all Swagger UI and OpenAPI endpoints (`/swagger-ui/**`, `/v3/api-docs/**`).
6. THE User_Service SHALL require authentication for all other endpoints not listed above.
7. THE BCryptPasswordEncoder SHALL be configured with a strength (work factor) of 10 rounds minimum. The strength SHALL be configurable via `security.bcrypt-strength` to allow tuning without code changes.
8. JWT tokens SHALL be used as Bearer tokens — clients SHALL include the token in the `Authorization` header as `Authorization: Bearer <token>`. This convention is the standard for Phase 4+ service-to-service integration.
9. Username comparisons (lookup by username) SHALL be case-sensitive — `Alice` and `alice` are treated as distinct usernames.

---

### Requirement 5: Data Persistence and Migration

**User Story:** As a platform engineer, I want the User Service database schema to be managed by Flyway, so that schema changes are versioned and reproducible across environments.

#### Acceptance Criteria

1. THE User_Service SHALL manage all database schema changes through Flyway migration scripts.
2. WHEN the User_Service starts, THE User_Service SHALL apply any pending Flyway migrations automatically.
3. THE User_Service SHALL store User data in a PostgreSQL database in a table named `users`.
4. THE User_Service SHALL enforce uniqueness on the `username` column via a database-level unique index.
5. THE User_Service SHALL enforce uniqueness on the `email` column via a database-level unique index.
6. THE User_Service SHALL use a UUID primary key for the `users` table.

---

### Requirement 6: Service Discovery Registration

**User Story:** As a platform operator, I want the User Service to register with the Eureka Server, so that the API Gateway can discover and route authentication traffic to it dynamically.

#### Acceptance Criteria

1. THE User_Service SHALL register itself with the Eureka Server using the service name `user-service`.
2. WHEN the User_Service starts, THE User_Service SHALL send a registration request to the Eureka Server at `http://localhost:8761/eureka`.
3. WHILE the User_Service is running, THE User_Service SHALL send periodic heartbeats to the Eureka Server to maintain its registration.
4. IF the Eureka Server is unavailable when the User_Service starts, THEN THE User_Service SHALL start successfully and retry Eureka registration in the background without throwing a startup exception.

---

### Requirement 7: API Gateway Route Integration

**User Story:** As an API consumer, I want to call authentication APIs through the gateway, so that I only need to know one address for all platform services.

#### Acceptance Criteria

1. THE API_Gateway SHALL route requests with path `/api/auth/**` to the User_Service using the load-balanced URI `lb://user-service`.
2. THE API_Gateway SHALL strip the `/api` prefix before forwarding to the User_Service, so that the User_Service receives requests at `/auth/**`.
3. WHEN the User_Service returns a response, THE API_Gateway SHALL return that response to the caller with the original HTTP status code and body unchanged.

---

### Requirement 8: API Contract and Documentation

**User Story:** As a developer integrating with the platform, I want a documented OpenAPI spec for the User Service, so that I can understand and consume the authentication API.

#### Acceptance Criteria

1. THE User_Service SHALL expose an OpenAPI 3 specification at `/v3/api-docs`.
2. THE User_Service SHALL expose a Swagger UI at `/swagger-ui.html`.
3. THE User_Service SHALL document all request and response DTOs with field descriptions and validation constraints.
4. ALL API responses SHALL use DTOs — internal entity classes SHALL NOT be returned directly from any endpoint.

---

### Requirement 9: Error Handling

**User Story:** As an API consumer, I want consistent and descriptive error responses, so that I can handle failures programmatically without ambiguity.

#### Acceptance Criteria

1. WHEN a `UserAlreadyExistsException` is raised, THE GlobalExceptionHandler SHALL return HTTP 409 with a JSON error body.
2. WHEN an `InvalidCredentialsException` is raised, THE GlobalExceptionHandler SHALL return HTTP 401 with a JSON error body.
3. WHEN a `MethodArgumentNotValidException` is raised (Bean Validation failure), THE GlobalExceptionHandler SHALL return HTTP 400 with a JSON error body listing the validation errors.
4. WHEN an `HttpMessageNotReadableException` is raised (malformed JSON body), THE GlobalExceptionHandler SHALL return HTTP 400 with a JSON error body.
5. WHEN any unhandled exception is raised, THE GlobalExceptionHandler SHALL return HTTP 500 with a generic JSON error body and SHALL NOT expose internal stack traces or implementation details.
6. WHEN a database uniqueness constraint violation occurs (e.g. concurrent duplicate registration), THE GlobalExceptionHandler SHALL catch the resulting `DataIntegrityViolationException` and return HTTP 409, ensuring the race condition is handled gracefully.

---

### Requirement 10: Rate Limiting (Out of Scope — Phase 3)

**User Story:** As a platform security engineer, I want to be aware of brute-force attack vectors, so that they can be addressed in a future phase.

#### Acceptance Criteria

1. Rate limiting on `/auth/login` and `/auth/register` is NOT implemented in Phase 3.
2. Rate limiting MAY be introduced in a future phase (e.g. via Spring Cloud Gateway filters or a dedicated rate-limiting service) to prevent brute-force and credential-stuffing attacks.
