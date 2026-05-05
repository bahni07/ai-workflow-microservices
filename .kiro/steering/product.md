# Product

**ai-workflow-microservices** is a microservices-based platform for building and executing AI-driven workflows.

## System Overview

The system enables users to create workflows composed of steps. An AI component analyzes workflows and suggests next actions. The system follows a microservices architecture with event-driven communication via asynchronous messaging.

---

## Build Phases

| Phase | Scope | Services | Status |
|-------|-------|----------|--------|
| Phase 1 | Foundation (MVP) | `workflow-service` | Done |
| Phase 2 | Microservice Infrastructure | `api-gateway`, Eureka | Done |
| Phase 3 | Authentication | `user-service` (JWT) | Done |
| Phase 4 | AI Integration | `ai-agent-service` | Done |
| Phase 5 | Event-Driven | Kafka integration across services | Done |
| Phase 6 | Notifications | `notification-service` | Done (built in Phase 5) |
| Phase 7 | Production | Docker, structured logging, README + diagrams | Not Started |

---

## Services

### `workflow-service` — Phase 1 (In Progress)
- Manage workflows and steps
- Maintain workflow state (`CREATED`, `IN_PROGRESS`, `COMPLETED`)
- Publish domain events (`workflow.created`, `step.created`) — stubbed in MVP
- Invoke AI suggestion module (internal mock in MVP)
- Emit events for downstream notification handling

### `api-gateway` — Phase 2 (Not Started)
- Single entry point, routing via Spring Cloud Gateway
- Service discovery integration with Eureka

### `user-service` — Phase 3 (Not Started)
- User registration and login
- JWT-based authentication

### `ai-agent-service` — Phase 4 (Not Started)
- Generate next-step suggestions for workflows
- Integrate with OpenAI API
- Consume `workflow.created` events from Kafka (Phase 5)

### `notification-service` — Phase 6 (Not Started)
- Consume Kafka events and log results

---

## MVP Definition

1. User creates a workflow
2. Workflow Service stores the workflow and its steps in PostgreSQL
3. Workflow Service publishes a `workflow.created` event (stubbed — logs via SLF4J)
4. Workflow Service invokes AI module (internal mock) to suggest the next step
5. Notification module logs the result (SLF4J at INFO level)

---

## Workflow State Model

State transitions are managed exclusively within `workflow-service`:

- `CREATED` — initial state when a workflow is created
- `IN_PROGRESS` — transitions to this state when the first step is added
- `COMPLETED` — transitions to this state when all steps are finished

---

## Event Model (Planned — Phase 5)

| Event | Producer | Consumers |
|-------|----------|-----------|
| `workflow.created` | `workflow-service` | `ai-agent-service`, `notification-service` |
| `step.created` | `workflow-service` | `notification-service` |
| `step.completed` | `workflow-service` | `notification-service` |
| `ai.suggestion.generated` | `ai-agent-service` | `notification-service` |

---

## Messaging Rationale

Kafka is used to enable asynchronous, decoupled communication between services. It allows independent scaling of producers and consumers and supports event replay if needed. In MVP (Phase 1), event publishing is stubbed via SLF4J logging; real Kafka brokers are introduced in Phase 5.

---

## Data Ownership

Each service owns its own database and does not directly access another service's database. Services communicate exclusively via REST APIs or asynchronous events.

---

## Failure Handling (Planned)

- In MVP, all failures are logged via SLF4J; the primary operation is never blocked by a stub/mock failure
- Failed Kafka events can be retried by consumers (Phase 5)
- Future enhancement: dead-letter queue (DLQ) for unprocessable messages

---

## Future Enhancements

- Retry mechanisms for failed workflow steps
- Parallel step execution within a workflow
- Agent configuration management (model name, temperature, token limits)
- UI for workflow visualization and monitoring
