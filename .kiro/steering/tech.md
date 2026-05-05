# Tech Stack

## Language & Runtime
- Java 21 — LTS release with virtual threads, records, and sealed classes

## Build & Packaging
- Maven (`pom.xml` per service) — each service builds as an independent executable JAR
- Docker images created per service in Phase 7

## Frameworks & Libraries

### Phase 1 (active)
- Spring Boot 3.x — rapid development, production-ready defaults, strong ecosystem
- Spring Web — RESTful controllers with JSON request/response
- Spring Data JPA — repository abstraction over Hibernate ORM
- Spring Validation — Bean Validation (Jakarta) at the controller boundary
- SpringDoc OpenAPI — auto-generated Swagger UI and OpenAPI 3 spec at `/swagger-ui.html`
- Flyway — version-controlled, repeatable database schema migrations
- SLF4J + Logback — structured logging from day one (key=value format, domain IDs in context)
- jqwik — property-based testing integrated with JUnit 5
- Testcontainers — integration tests against real PostgreSQL (no mocks for DB layer)

### Phase 2 (planned)
- Spring Cloud Gateway — single entry point, routing, and load balancing
- Netflix Eureka — service registry and discovery

### Phase 3 (planned)
- Spring Security — authentication and authorisation framework
- JWT — stateless token-based authentication for `user-service`

### Phase 4 (planned)
- OpenAI Java client — generates next-step suggestions based on workflow name and step context; used by `ai-agent-service`

### Phase 5 (planned)
- Apache Kafka — asynchronous, decoupled event streaming between services
- Spring Kafka — Kafka producer/consumer integration for Spring Boot

### Phase 7 (planned)
- Docker + Docker Compose — containerised deployment of all services and infrastructure
- Structured logging improvements — correlation IDs, centralised log aggregation

## Database
- PostgreSQL — reliable relational database with strong consistency guarantees
- Each service owns its own schema; no cross-service database access

## Tech Stack Rationale

| Decision | Rationale |
|----------|-----------|
| Spring Boot | Mature ecosystem, production-ready defaults, minimal boilerplate |
| PostgreSQL | Strong consistency, ACID guarantees, well-supported with JPA |
| Kafka | Enables async, decoupled communication; supports event replay and independent scaling |
| Flyway | Schema changes are versioned, auditable, and reproducible across environments |
| Testcontainers | Integration tests run against real infrastructure — no false confidence from mocks |
| jqwik | Property-based testing catches edge cases that example-based tests miss |
| Java 21 | LTS stability, records for clean DTOs, strong Spring Boot 3.x compatibility |

## API Design
- RESTful APIs using JSON for all request and response bodies
- Standard HTTP status codes: 200, 201, 204, 400, 404, 409, 500
- OpenAPI 3 spec auto-generated via SpringDoc — available at `/v3/api-docs` and `/swagger-ui.html`
- All endpoints documented with `@Operation` and `@ApiResponse` annotations

## Persistence Strategy
- Spring Data JPA with Hibernate as the ORM
- Flyway manages all schema changes — no manual DDL, no `spring.jpa.hibernate.ddl-auto=create`
- Each service owns its database — no shared schemas, no cross-service joins
- Entities live in `entity/`, never exposed at the API boundary

## Communication Patterns
- Synchronous: REST APIs for immediate, request-response operations (Phases 1–4)
- Asynchronous: Kafka events for decoupled, non-blocking processing (Phase 5+)
- Inter-service REST calls use typed clients in `client/` (Feign or WebClient)

## Testing Strategy

| Layer | Tool | Approach |
|-------|------|----------|
| Controller | JUnit 5 + MockMvc (`@WebMvcTest`) | HTTP status, validation, response shape |
| Service | JUnit 5 + Mockito | Business logic, all paths and error cases |
| Repository | Spring Data JPA + Testcontainers | Real PostgreSQL queries and Flyway migrations |
| Integration | Spring Boot Test + Testcontainers | Full stack — controller through DB |
| Property-based | jqwik | Universal properties, minimum 100 iterations per property |

## Configuration Management (Planned)
- Per-service `application.yml` for Phase 1
- Spring Cloud Config Server as an optional enhancement for centralised configuration in later phases

## Observability (Future Enhancements)
- Metrics: Micrometer + Prometheus
- Distributed tracing: OpenTelemetry
- Log aggregation: structured JSON logs consumable by ELK or similar

## Common Commands

| Task | Command |
|------|---------|
| Build | `mvn package` |
| Test | `mvn test` |
| Run | `mvn spring-boot:run` |
