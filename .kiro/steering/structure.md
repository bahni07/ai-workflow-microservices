# Project Structure

```
/
├── .kiro/                          # Kiro config (steering, specs, hooks)
│   ├── steering/
│   │   ├── product.md
│   │   ├── structure.md
│   │   └── tech.md
│   └── specs/
│       └── workflow-service/           # Phase 1 — active
│           ├── requirements.md
│           ├── design.md
│           └── tasks.md
├── services/                       # Individual microservice modules
│   └── workflow-service/               # Phase 1 — build this first
│       ├── README.md                   # API docs, how to run, DB config
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/com/aiworkflow/workflow/
│           │   │   ├── controller/     # REST controllers
│           │   │   ├── service/        # Business logic interfaces
│           │   │   │   └── impl/       # Business logic implementations
│           │   │   ├── repository/     # Spring Data JPA repositories
│           │   │   ├── entity/         # JPA-annotated domain entities
│           │   │   ├── dto/            # Request/response DTOs (API boundary only)
│           │   │   ├── mapper/         # Entity ↔ DTO conversion
│           │   │   ├── event/          # Domain event POJOs (WorkflowCreatedEvent, etc.)
│           │   │   ├── enums/          # Enumerations (WorkflowStatus, StepStatus)
│           │   │   ├── validation/     # Custom validators and business rule validation
│           │   │   ├── client/         # REST clients for inter-service calls (Feign/WebClient)
│           │   │   ├── config/         # Spring @Configuration beans (Kafka, Security, etc.)
│           │   │   ├── constants/      # Shared constants (event names, error codes)
│           │   │   └── exception/      # Custom exceptions + GlobalExceptionHandler
│           │   └── resources/
│           │       ├── application.yml
│           │       └── db/migration/   # Flyway SQL scripts (V1__, V2__, ...)
│           └── test/java/com/aiworkflow/workflow/
│               ├── controller/         # MockMvc controller tests
│               ├── service/            # Unit tests (mocked dependencies)
│               └── integration/        # Testcontainers full-stack tests
├── infra/                          # Infrastructure configuration (Phase 5+)
│   ├── docker/                     # Docker Compose files
│   ├── kafka/                      # Kafka broker configuration
│   └── gateway/                    # API Gateway configuration
├── scripts/                        # Developer convenience scripts
│   ├── start-all.sh
│   └── stop-all.sh
├── .gitignore
└── README.md                       # Architecture diagram, services overview, how to run
```

## Package Responsibilities

| Package | Purpose |
|---------|---------|
| `controller/` | HTTP entry points — request validation, response mapping only |
| `service/` | Business logic interfaces |
| `service/impl/` | Business logic implementations — orchestrates repositories, events, clients |
| `repository/` | Spring Data JPA interfaces — database access only |
| `entity/` | JPA-annotated domain entities — never exposed at API boundary |
| `dto/` | Request/response records — API boundary objects only |
| `mapper/` | Converts between `entity/` and `dto/` — keeps layers clean |
| `event/` | Domain event POJOs published to Kafka (e.g. `WorkflowCreatedEvent`) |
| `enums/` | Shared enumerations (e.g. `WorkflowStatus`, `StepStatus`) |
| `validation/` | Custom `ConstraintValidator` impls and business rule validators |
| `client/` | Feign/WebClient interfaces for calling other services |
| `config/` | Spring `@Configuration` beans (Kafka, Security, OpenAPI, etc.) |
| `constants/` | Shared string constants — event names, error codes, header names |
| `exception/` | Custom exceptions and `@ControllerAdvice` global handler |

## Dependency Direction Rules

Dependencies flow in one direction only — violations are bugs, not style issues:

```
controller → service → repository
service    → client, event publishers
mapper     → entity, dto
```

- `controller` depends on `service` interfaces — never on `impl` or `repository` directly
- `service/impl` may use `repository`, `client`, `event`, `mapper`, `validation`
- `repository` must not depend on `service` or `controller`
- `entity` must not depend on `dto`, `mapper`, or any other application layer
- `dto` must not depend on `entity`

## Testing Strategy

Tests mirror the main source tree under `test/java/`:

| Layer | Tool | Scope |
|-------|------|-------|
| Controller | JUnit 5 + MockMvc (`@WebMvcTest`) | HTTP status, validation, response shape |
| Service | JUnit 5 + Mockito | Business logic, all paths and error cases |
| Repository | Spring Data JPA + Testcontainers | Real PostgreSQL queries and Flyway migrations |
| Integration | Spring Boot Test + Testcontainers | Full stack — controller through DB |
| Property-based | jqwik | Universal properties across generated inputs |

- Use structured logging: include `workflowId` and `stepId` as key=value pairs in all log statements
- Unit tests mock all dependencies — no real DB, no real HTTP
- Integration tests use a real PostgreSQL container via Testcontainers
- Property-based tests run a minimum of 100 iterations per property

## Logging Strategy

- Use SLF4J with Logback throughout
- Log at `INFO` for successful operations, `ERROR` for failures
- Always include domain identifiers in log context: `workflowId={}`, `stepId={}`
- Stub/mock failures must be logged but must never propagate to the caller

## Conventions

- Each microservice lives in its own subdirectory under `services/`
- Services are independently deployable Spring Boot applications
- Base Java package: `com.aiworkflow.<serviceshortname>`
- Use `entity/` not `model/` — avoids ambiguity with DTOs and domain objects
- Use `service/impl/` for implementations — keeps interfaces and impls clearly separated
- Database migrations managed by Flyway under `resources/db/migration/` using `V<n>__<description>.sql`
- Domain events live in `event/` even when stubbed — ready for Kafka in Phase 5
- Infrastructure configs (Docker, Kafka, Gateway) live in `infra/` — never inside service directories
- Each service must have its own `README.md` documenting APIs, how to run, and DB config
- Build order follows the phase plan in `product.md` — do not start a new service until the current phase is complete
- Update this file as new services are added
