# Implementation Tasks: Workflow Service

## Tasks

- [x] 1. Project scaffold
  - [x] 1.1 Create `services/workflow-service/pom.xml` with Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Validation, SpringDoc OpenAPI, Flyway, PostgreSQL driver, jqwik, Testcontainers, and Mockito dependencies
  - [x] 1.2 Create main application class `WorkflowServiceApplication` under `com.aiworkflow.workflow`
  - [x] 1.3 Create `application.yml` with datasource, JPA, Flyway, and SpringDoc configuration

- [x] 2. Domain model and database migrations
  - [x] 2.1 Create `WorkflowStatus` enum (`CREATED`, `IN_PROGRESS`, `COMPLETED`) in `enums/`
  - [x] 2.2 Create `StepStatus` enum (`PENDING`, `IN_PROGRESS`, `COMPLETED`) in `enums/`
  - [x] 2.3 Create `Workflow` JPA entity in `entity/` with `id`, `name`, `status`, `steps` collection, and `@Version` field for optimistic locking
  - [x] 2.4 Create `Step` JPA entity in `entity/` with `id`, `workflow`, `name`, `status`, and `@Version` field for optimistic locking
  - [x] 2.5 Create Flyway migration `V1__create_workflows.sql`
  - [x] 2.6 Create Flyway migration `V2__create_steps.sql` with index on `steps.workflow_id`

- [x] 3. Repository layer
  - [x] 3.1 Create `WorkflowRepository` extending `JpaRepository<Workflow, UUID>`
  - [x] 3.2 Create `StepRepository` extending `JpaRepository<Step, UUID>` with `findByWorkflowId` and `countByWorkflowIdAndStatusNot`

- [x] 4. DTOs and Mapper
  - [x] 4.1 Create `CreateWorkflowRequest` record with `@NotBlank @Size(max = 255) String name`
  - [x] 4.2 Create `AddStepRequest` record with `@NotBlank @Size(max = 255) String name`
  - [x] 4.3 Create `StepResponse` record (`id`, `workflowId`, `name`, `status`)
  - [x] 4.4 Create `WorkflowResponse` record (`id`, `name`, `status`, `List<StepResponse>`)
  - [x] 4.5 Create `AddStepResponse` record (`StepResponse step`, `String suggestedNextStep`)
  - [x] 4.6 Create `WorkflowMapper` in `mapper/` to convert `Workflow` ↔ `WorkflowResponse` and `Step` ↔ `StepResponse`

- [x] 5. AI Suggester
  - [x] 5.1 Create `AISuggester` interface
  - [x] 5.2 Create `MockAISuggester` implementation returning `"Suggested next step for '<workflowName>' (step <N+1>)"`

- [x] 6. Event Publisher (stubbed)
  - [x] 6.1 Create `EventPublisher` interface with `publishWorkflowCreated`, `publishStepCreated`, and `publishStepCompleted`
  - [x] 6.2 Create `StubEventPublisher` implementation that logs via SLF4J using event names from `EventNames` constants and swallows all exceptions

- [x] 7. Notification Logger
  - [x] 7.1 Create `NotificationLogger` component with `logWorkflowCreated`, `logStepAdded`, `logStepCompleted`, and `logError` methods using structured key=value logging (`workflowId={}`, `stepId={}`)

- [x] 8. Constants and exception classes
  - [x] 8.1 Create `EventNames` constants class in `constants/` with `WORKFLOW_CREATED = "workflow.created"`, `STEP_CREATED = "step.created"`, and `STEP_COMPLETED = "step.completed"`
  - [x] 8.2 Create `WorkflowNotFoundException` and `StepNotFoundException` in `exception/`
  - [x] 8.3 Create `GlobalExceptionHandler` (`@ControllerAdvice`) mapping all exceptions to correct HTTP statuses and JSON error bodies

- [x] 9. Service layer
  - [x] 9.1 Create `WorkflowService` interface in `service/`
  - [x] 9.2 Implement `WorkflowServiceImpl` in `service/impl/` — `createWorkflow`: persist, publish `workflow.created` event, log notification; annotate with `@Transactional`
  - [x] 9.3 Implement `WorkflowServiceImpl` — `getWorkflow`: fetch with steps, throw `WorkflowNotFoundException` if absent
  - [x] 9.4 Implement `WorkflowServiceImpl` — `addStep`: validate workflow exists, persist step, transition status to `IN_PROGRESS` if `CREATED`, call `AISuggester`, publish `step.created` event, log notification; annotate with `@Transactional`
  - [x] 9.5 Implement `WorkflowServiceImpl` — `completeStep`: validate step belongs to workflow, update step status to `COMPLETED`, check if all steps completed and update workflow status to `COMPLETED` if so, publish `step.completed` event, log notification; annotate with `@Transactional`
  - [x] 9.6 Add `WorkflowValidator` in `validation/` to encapsulate workflow/step existence checks and ownership validation; use from service layer

- [x] 10. Controller layer
  - [x] 10.1 Create `WorkflowController` with `POST /workflows`, `GET /workflows/{id}`, `POST /workflows/{id}/steps`
  - [x] 10.2 Add `POST /workflows/{workflowId}/steps/{stepId}/complete` endpoint
  - [x] 10.3 Add SpringDoc `@Operation` and `@ApiResponse` annotations to all endpoints

- [x] 11. Unit tests
  - [x] 11.1 Write unit tests for `WorkflowServiceImpl` — `createWorkflow`, `getWorkflow`, `addStep` (mock all dependencies, cover all paths and error cases)
  - [x] 11.2 Write unit tests for `WorkflowServiceImpl` — `completeStep` (step not found, workflow auto-completion when all steps done)
  - [x] 11.3 Write unit tests for `WorkflowController` using `@WebMvcTest` / `MockMvc` (all endpoints, validation, error responses)
  - [x] 11.4 Write unit tests for `MockAISuggester` (verify deterministic output formula)
  - [x] 11.5 Write unit tests for `StubEventPublisher` (verify no exception is thrown on internal error)
  - [x] 11.6 Write unit tests for `WorkflowMapper` (entity → DTO, step list mapping)
  - [x] 11.7 Write unit tests for `GlobalExceptionHandler` (404, 400, 500 scenarios)

- [x] 12. Property-based tests (jqwik)
  - [x] 12.1 Write property test for Property 1 — create then get returns matching workflow in CREATED state
  - [x] 12.2 Write property test for Property 2 — blank name returns 400 and count unchanged
  - [x] 12.3 Write property test for Property 3 — add step transitions workflow to IN_PROGRESS with PENDING step
  - [x] 12.4 Write property test for Property 4 — add-step response always contains non-blank suggestedNextStep
  - [x] 12.5 Write property test for Property 5 — random UUID returns 404 for get and add-step
  - [x] 12.6 Write property test for Property 6 — event publisher error does not fail the request
  - [x] 12.7 Write property test for Property 7 — completing all steps transitions workflow status to COMPLETED

- [x] 13. Integration tests (Testcontainers)
  - [x] 13.1 Write integration test for full create-workflow flow against real PostgreSQL
  - [x] 13.2 Write integration test for full add-step flow (including IN_PROGRESS status transition)
  - [x] 13.3 Write integration test for full complete-step flow (including COMPLETED status transition when all steps done)
  - [x] 13.4 Write integration test verifying Flyway migrations apply cleanly on startup

- [ ] 14. Manual verification
  - [x] 14.1 Start service locally with `mvn spring-boot:run` against a running PostgreSQL instance
  - [x] 14.2 Test all APIs via Swagger UI at `/swagger-ui.html` — create workflow, add steps, complete steps
  - [x] 14.3 Verify structured logs contain `workflowId` and `stepId` key=value pairs
  - [x] 14.4 Verify DB entries in `workflows` and `steps` tables reflect correct status transitions
