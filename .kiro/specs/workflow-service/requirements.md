# Requirements Document

## Introduction

The Workflow Service is the core microservice of the ai-workflow-microservices platform. It manages the lifecycle of AI-driven workflows and their constituent steps. In Phase 1 (MVP), the service exposes a REST API for creating and retrieving workflows and steps, completing steps, provides a mock AI suggestion for the next step, and stubs event-driven processing via Kafka. The service is independently deployable, backed by PostgreSQL, and follows the platform's layered architecture (Controller → Service → Repository).

## Glossary

- **Workflow**: A named, stateful sequence of steps representing an AI-driven process. Has an `id`, `name`, and `status`.
- **Step**: A discrete unit of work within a Workflow. Has an `id`, `workflowId`, `name`, and `status`.
- **Workflow_Service**: The Spring Boot microservice defined in this document.
- **AI_Suggester**: The component responsible for suggesting the next step name for a workflow (mocked in Phase 1).
- **Event_Publisher**: The component responsible for publishing domain events to Kafka (stubbed in Phase 1). Uses event names in dot-notation: `workflow.created`, `step.created`, `step.completed`.
- **Notification_Logger**: The component responsible for logging workflow processing results using structured key=value log format.
- **WorkflowStatus**: An enumeration of valid workflow states: `CREATED`, `IN_PROGRESS`, `COMPLETED`.
- **StepStatus**: An enumeration of valid step states: `PENDING`, `IN_PROGRESS`, `COMPLETED`.

---

## Requirements

### Requirement 1: Create a Workflow

**User Story:** As a platform user, I want to create a new workflow, so that I can define and track an AI-driven process.

#### Acceptance Criteria

1. WHEN a `POST /workflows` request is received with a valid name, THE Workflow_Service SHALL create a new Workflow with status `CREATED` and return it with HTTP 201.
2. IF a `POST /workflows` request is received with a missing or blank name, THEN THE Workflow_Service SHALL return HTTP 400 with a descriptive validation error message.
3. IF a `POST /workflows` request is received with a name exceeding 255 characters, THEN THE Workflow_Service SHALL return HTTP 400.
4. THE Workflow_Service SHALL assign a unique identifier to each created Workflow.
5. WHEN a Workflow is created, THE Workflow_Service SHALL persist it to the database immediately.
6. ALL responses SHALL use DTOs and SHALL NOT expose internal entity structure.

---

### Requirement 2: Retrieve a Workflow

**User Story:** As a platform user, I want to retrieve a workflow by its ID, so that I can inspect its current state and steps.

#### Acceptance Criteria

1. WHEN a `GET /workflows/{id}` request is received for an existing Workflow, THE Workflow_Service SHALL return the Workflow with its associated Steps and HTTP 200.
2. IF a `GET /workflows/{id}` request is received for a non-existent Workflow ID, THEN THE Workflow_Service SHALL return HTTP 404 with a descriptive error message.
3. THE Workflow_Service SHALL include all Steps belonging to the Workflow in the response.
4. FOR MVP, all Steps are returned without pagination. Pagination may be introduced in a future phase.

---

### Requirement 3: Add a Step to a Workflow

**User Story:** As a platform user, I want to add a step to an existing workflow, so that I can build up the sequence of actions in my process.

#### Acceptance Criteria

1. WHEN a `POST /workflows/{id}/steps` request is received with a valid step name for an existing Workflow, THE Workflow_Service SHALL create a new Step with status `PENDING` and return it with HTTP 201.
2. IF a `POST /workflows/{id}/steps` request is received for a non-existent Workflow ID, THEN THE Workflow_Service SHALL return HTTP 404 with a descriptive error message.
3. IF a `POST /workflows/{id}/steps` request is received with a missing or blank step name, THEN THE Workflow_Service SHALL return HTTP 400 with a descriptive validation error message.
4. IF a `POST /workflows/{id}/steps` request is received with a step name exceeding 255 characters, THEN THE Workflow_Service SHALL return HTTP 400.
5. WHEN a Step is added, THE Workflow_Service SHALL update the parent Workflow status to `IN_PROGRESS` if it was previously `CREATED`.
6. THE Step creation and Workflow status update SHALL occur within a single database transaction.
7. WHEN a Step is created, THE Workflow_Service SHALL persist it to the database immediately.

---

### Requirement 4: Complete a Step

**User Story:** As a platform user, I want to mark a step as completed, so that workflow progress can be tracked accurately.

#### Acceptance Criteria

1. WHEN a `POST /workflows/{workflowId}/steps/{stepId}/complete` request is received for an existing Step, THE Workflow_Service SHALL update the Step status to `COMPLETED` and return HTTP 200.
2. IF the Step does not exist or does not belong to the specified Workflow, THEN THE Workflow_Service SHALL return HTTP 404.
3. WHEN all Steps in a Workflow are `COMPLETED`, THE Workflow_Service SHALL update the Workflow status to `COMPLETED`.
4. THE Step status update and Workflow status check-and-update SHALL occur within a single database transaction.
5. WHEN a Step is completed, THE Event_Publisher SHALL publish a `step.completed` event.

---

### Requirement 5: AI Step Suggestion

**User Story:** As a platform user, I want the service to suggest the next step for my workflow, so that I can benefit from AI-driven guidance even in the MVP phase.

#### Acceptance Criteria

1. WHEN a Step is added to a Workflow, THE AI_Suggester SHALL generate a suggested next step name based on the Workflow name, existing step names, and current step count.
2. THE Workflow_Service SHALL include the suggested next step name in the response when a Step is added.
3. WHERE the AI backend is unavailable or not yet integrated, THE AI_Suggester SHALL return a deterministic mock suggestion derived from the Workflow name and step count.
4. A failure in THE AI_Suggester SHALL NOT prevent the Step from being persisted or the HTTP 201 response from being returned.

---

### Requirement 6: Event Publishing (Stubbed)

**User Story:** As a platform operator, I want workflow events to be published to Kafka, so that downstream services can react to workflow state changes.

#### Acceptance Criteria

1. WHEN a Workflow is created, THE Event_Publisher SHALL publish a `workflow.created` event.
2. WHEN a Step is added to a Workflow, THE Event_Publisher SHALL publish a `step.created` event.
3. WHEN a Step is completed, THE Event_Publisher SHALL publish a `step.completed` event.
4. WHERE the Kafka broker is unavailable or not yet configured, THE Event_Publisher SHALL log the event details and continue processing without throwing an exception.
5. A failure in THE Event_Publisher SHALL NOT affect the primary operation response.

---

### Requirement 7: Notification Logging

**User Story:** As a platform operator, I want workflow processing results to be logged, so that I can audit and trace workflow activity.

#### Acceptance Criteria

1. WHEN a Workflow is created, THE Notification_Logger SHALL log a structured message containing `workflowId` and `workflowName` as key=value pairs.
2. WHEN a Step is added, THE Notification_Logger SHALL log a structured message containing `stepId`, `stepName`, and `workflowId` as key=value pairs.
3. WHEN a Step is completed, THE Notification_Logger SHALL log a structured message containing `stepId` and `workflowId` as key=value pairs.
4. THE Notification_Logger SHALL log at INFO level for successful operations and ERROR level for failures.

---

### Requirement 8: Data Persistence and Migration

**User Story:** As a platform engineer, I want the database schema to be managed by Flyway, so that schema changes are versioned and reproducible.

#### Acceptance Criteria

1. THE Workflow_Service SHALL manage all database schema changes through Flyway migration scripts.
2. WHEN the Workflow_Service starts, THE Workflow_Service SHALL apply any pending Flyway migrations automatically.
3. THE Workflow_Service SHALL store Workflow and Step data in a PostgreSQL database.

---

### Requirement 9: API Contract and Documentation

**User Story:** As a developer integrating with the platform, I want a documented OpenAPI spec, so that I can understand and consume the Workflow Service API.

#### Acceptance Criteria

1. THE Workflow_Service SHALL expose an OpenAPI 3 specification at `/v3/api-docs`.
2. THE Workflow_Service SHALL expose a Swagger UI at `/swagger-ui.html`.
3. THE Workflow_Service SHALL document all request and response DTOs with field descriptions.
4. ALL API responses SHALL use DTOs — internal entity classes SHALL NOT be returned directly from any endpoint.
