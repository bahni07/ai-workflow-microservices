# Requirements Document

## Introduction

Phase 5 introduces real Kafka-based event-driven communication across the ai-workflow-microservices platform. The SLF4J stub event publisher in `workflow-service` is replaced with a real Kafka producer. The `ai-agent-service` implements its stubbed `WorkflowEventConsumer` to auto-generate AI suggestions when a workflow is created. A new `notification-service` is introduced to consume all domain events and log them at INFO level. Docker Compose provides a local Kafka + Zookeeper environment. All services continue to register with Eureka and remain accessible via the API Gateway.

## Glossary

- **Kafka_Producer**: A Spring Kafka `KafkaTemplate`-backed component that publishes domain events to Kafka topics.
- **Kafka_Consumer**: A Spring Kafka `@KafkaListener`-annotated component that receives domain events from Kafka topics.
- **WorkflowEventConsumer**: The existing stub class in `ai-agent-service` that will be implemented to consume `workflow.created` events.
- **Notification_Service**: A new Spring Boot microservice (`notification-service`) that consumes all domain events and logs them at INFO level.
- **EventPublisher**: The existing interface in `workflow-service` that abstracts event publishing; currently backed by `StubEventPublisher`.
- **KafkaEventPublisher**: The new `EventPublisher` implementation in `workflow-service` that publishes events to Kafka topics using `KafkaTemplate`.
- **WorkflowCreatedEvent**: Domain event POJO published when a workflow is created; contains `workflowId` and `workflowName`.
- **StepCreatedEvent**: Domain event POJO published when a step is added to a workflow; contains `workflowId`, `stepId`, and `stepName`.
- **StepCompletedEvent**: Domain event POJO published when a step is marked completed; contains `workflowId` and `stepId`.
- **AiSuggestionGeneratedEvent**: Domain event POJO published by `ai-agent-service` when an AI suggestion is generated; contains `workflowId` and `suggestion`.
- **Dead_Letter_Topic**: A Kafka topic that receives messages that could not be processed after all retry attempts are exhausted.
- **Consumer_Group**: A named group of Kafka consumers that collectively consume messages from one or more topics; each message is delivered to exactly one consumer in the group.
- **Docker_Compose**: The local infrastructure definition file that starts Kafka, Zookeeper, and all microservices for development.

## Requirements

### Requirement 1: Kafka Infrastructure Setup

**User Story:** As a developer, I want a Docker Compose configuration that starts Kafka and Zookeeper locally, so that all services can produce and consume events during development.

#### Acceptance Criteria

1. THE Docker_Compose SHALL define a Zookeeper service and a Kafka broker service that start together.
2. WHEN the Docker_Compose stack is started, THE Kafka broker SHALL be accessible to all microservices on `localhost:9092`.
3. THE Docker_Compose SHALL define all four domain topics: `workflow.created`, `step.created`, `step.completed`, and `ai.suggestion.generated`.
4. WHEN a microservice starts, THE microservice SHALL connect to Kafka using the broker address configured in its `application.yml`.
5. WHERE Kafka is unavailable at startup, THE microservice SHALL log an error and continue starting up without blocking the main application thread.

---

### Requirement 2: Kafka Producer in workflow-service

**User Story:** As a developer, I want `workflow-service` to publish real Kafka events instead of SLF4J stubs, so that downstream services can react to workflow domain events.

#### Acceptance Criteria

1. THE workflow-service SHALL include a `KafkaEventPublisher` that implements the `EventPublisher` interface and replaces `StubEventPublisher` as the active Spring bean.
2. WHEN `createWorkflow` is called, THE Kafka_Producer SHALL publish a `WorkflowCreatedEvent` to the `workflow.created` topic containing the `workflowId` and `workflowName`.
3. WHEN `addStep` is called, THE Kafka_Producer SHALL publish a `StepCreatedEvent` to the `step.created` topic containing the `workflowId`, `stepId`, and `stepName`.
4. WHEN `completeStep` is called, THE Kafka_Producer SHALL publish a `StepCompletedEvent` to the `step.completed` topic containing the `workflowId` and `stepId`.
5. THE Kafka_Producer SHALL serialize all event payloads as JSON using Jackson.
6. IF a Kafka publish call throws an exception, THEN THE Kafka_Producer SHALL log the error at ERROR level and allow the primary database transaction to complete successfully.
7. THE workflow-service `pom.xml` SHALL declare `spring-kafka` as a compile-scope dependency.

---

### Requirement 3: WorkflowEventConsumer in ai-agent-service

**User Story:** As a developer, I want `ai-agent-service` to automatically generate AI suggestions when a workflow is created, so that suggestions are available without a manual REST call.

#### Acceptance Criteria

1. THE WorkflowEventConsumer SHALL be annotated with `@KafkaListener` subscribing to the `workflow.created` topic with consumer group `ai-agent-service`.
2. WHEN a `WorkflowCreatedEvent` is received, THE WorkflowEventConsumer SHALL invoke `AiSuggestionService.suggest` with the `workflowId` and `workflowName` from the event.
3. WHEN `AiSuggestionService.suggest` returns a result, THE WorkflowEventConsumer SHALL publish an `AiSuggestionGeneratedEvent` to the `ai.suggestion.generated` topic containing the `workflowId` and the suggestion text.
4. WHEN `AiSuggestionService.suggest` throws an exception, THE WorkflowEventConsumer SHALL log the error at ERROR level and not re-throw the exception, so that the Kafka offset is committed and the message is not reprocessed indefinitely.
5. THE ai-agent-service `pom.xml` SHALL declare `spring-kafka` as a compile-scope dependency.
6. THE WorkflowEventConsumer SHALL deserialize incoming messages from JSON using Jackson.

---

### Requirement 4: notification-service

**User Story:** As a developer, I want a `notification-service` that consumes all domain events and logs them, so that I have a centralized audit trail of all platform activity.

#### Acceptance Criteria

1. THE Notification_Service SHALL be a new Spring Boot application with base package `com.aiworkflow.notification` running on port `8084`.
2. THE Notification_Service SHALL register with Eureka on startup.
3. THE Notification_Service SHALL expose a `/actuator/health` endpoint.
4. THE Notification_Service SHALL contain a `NotificationEventConsumer` that subscribes to all four topics: `workflow.created`, `step.created`, `step.completed`, and `ai.suggestion.generated`, using consumer group `notification-service`.
5. WHEN a `WorkflowCreatedEvent` is received, THE Notification_Service SHALL log at INFO level: `event=workflow.created workflowId={} workflowName={}`.
6. WHEN a `StepCreatedEvent` is received, THE Notification_Service SHALL log at INFO level: `event=step.created workflowId={} stepId={} stepName={}`.
7. WHEN a `StepCompletedEvent` is received, THE Notification_Service SHALL log at INFO level: `event=step.completed workflowId={} stepId={}`.
8. WHEN an `AiSuggestionGeneratedEvent` is received, THE Notification_Service SHALL log at INFO level: `event=ai.suggestion.generated workflowId={} suggestion={}`.
9. IF a consumed message cannot be deserialized, THEN THE Notification_Service SHALL log the error at ERROR level and commit the offset so that the poison message does not block the consumer.
10. THE Notification_Service `pom.xml` SHALL declare `spring-kafka` as a compile-scope dependency.

---

### Requirement 5: Shared Event POJOs

**User Story:** As a developer, I want well-defined event POJO classes shared across services, so that producers and consumers agree on the message schema.

#### Acceptance Criteria

1. THE workflow-service SHALL define `WorkflowCreatedEvent`, `StepCreatedEvent`, and `StepCompletedEvent` as Java records in the `event/` package with all required fields.
2. THE ai-agent-service SHALL define `WorkflowCreatedEvent` (for deserialization) and `AiSuggestionGeneratedEvent` (for serialization) as Java records in the `kafka/` package.
3. THE Notification_Service SHALL define local copies of all four event records (`WorkflowCreatedEvent`, `StepCreatedEvent`, `StepCompletedEvent`, `AiSuggestionGeneratedEvent`) in its `event/` package.
4. THE WorkflowCreatedEvent SHALL contain fields: `workflowId` (UUID) and `workflowName` (String).
5. THE StepCreatedEvent SHALL contain fields: `workflowId` (UUID), `stepId` (UUID), and `stepName` (String).
6. THE StepCompletedEvent SHALL contain fields: `workflowId` (UUID) and `stepId` (UUID).
7. THE AiSuggestionGeneratedEvent SHALL contain fields: `workflowId` (UUID) and `suggestion` (String).
8. IF an event record field is null during serialization, THEN THE Kafka_Producer SHALL include the field as a JSON `null` value rather than omitting it.

---

### Requirement 6: Kafka Configuration

**User Story:** As a developer, I want each service to have correct Kafka producer and consumer configuration, so that messages are reliably delivered and correctly deserialized.

#### Acceptance Criteria

1. THE Kafka_Producer in workflow-service SHALL be configured with `acks=all` to ensure the broker acknowledges writes from all in-sync replicas before confirming delivery.
2. THE Kafka_Producer in workflow-service SHALL be configured with `retries=3` to retry transient send failures.
3. THE Kafka_Consumer in ai-agent-service SHALL be configured with `auto-offset-reset=earliest` so that it processes events published before the consumer started.
4. THE Kafka_Consumer in notification-service SHALL be configured with `auto-offset-reset=earliest`.
5. THE Kafka_Producer in ai-agent-service SHALL be configured with `acks=all` and `retries=3`.
6. WHEN a Kafka consumer encounters a deserialization error, THE consumer SHALL route the failed message to a Dead_Letter_Topic named `<original-topic>.DLT` rather than retrying indefinitely.
7. THE `application.yml` of each service SHALL externalize the Kafka broker address via the property `spring.kafka.bootstrap-servers` with a default of `localhost:9092`.

---

### Requirement 7: Error Handling and Resilience

**User Story:** As a developer, I want Kafka failures to be isolated from primary business operations, so that a Kafka outage does not cause REST API failures.

#### Acceptance Criteria

1. WHEN a Kafka producer send fails after all retries, THE Kafka_Producer SHALL log the failure at ERROR level including the topic name and event type, and SHALL return normally to the caller.
2. WHEN a Kafka consumer throws an unhandled exception during message processing, THE consumer SHALL log the error at ERROR level including the topic and partition, and SHALL commit the offset to prevent infinite reprocessing.
3. WHILE Kafka is unavailable, THE workflow-service REST API SHALL continue to accept and process requests, persisting data to PostgreSQL normally.
4. WHILE Kafka is unavailable, THE ai-agent-service REST API SHALL continue to serve suggestion requests normally.
5. IF a consumed event payload is missing a required field, THEN THE consumer SHALL log a warning at WARN level with the raw message content and skip processing.
6. THE system SHALL operate under at-least-once delivery semantics; all Kafka_Consumer implementations SHALL be idempotent so that processing the same event more than once does not produce inconsistent state.
7. THE Kafka_Producer SHALL use `workflowId` as the Kafka message key for all events so that events belonging to the same workflow are routed to the same partition and processed in order.
8. WHEN adding new fields to an event record, THE event schema SHALL remain backward-compatible; existing field names and types SHALL NOT be changed or removed.

---

### Requirement 8: Delivery Semantics and Ordering

**User Story:** As a developer, I want clear guarantees about event ordering and delivery, so that I can reason about consumer behavior correctly.

#### Acceptance Criteria

1. THE system SHALL guarantee event ordering per `workflowId` within a single Kafka partition; global ordering across all workflows is not guaranteed.
2. THE Kafka_Producer SHALL serialize all event payloads as JSON using Jackson with `camelCase` field naming.
3. WHEN a Kafka consumer processes a message, THE consumer processing logic SHALL be thread-safe to support concurrent processing across partitions.
4. THE Notification_Service consumer SHALL NOT retry failed messages; failed messages SHALL be routed to the Dead_Letter_Topic.
