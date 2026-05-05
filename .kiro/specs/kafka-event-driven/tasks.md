# Implementation Plan: kafka-event-driven (Phase 5)

## Overview

Implement real Kafka event-driven communication across the platform: replace the SLF4J stub in `workflow-service` with a real Kafka producer, implement the `WorkflowEventConsumer` stub in `ai-agent-service`, and create the new `notification-service`. All tasks use Java 17, Spring Boot 3.2.5, Spring Kafka, and jqwik 1.8.4.

## Tasks

- [x] 1. Add Kafka dependencies and shared event POJOs to workflow-service
  - Add `spring-kafka` compile dependency to `workflow-service/pom.xml`
  - Add `spring-kafka-test` and `testcontainers/kafka` test dependencies to `workflow-service/pom.xml`
  - Replace the existing `event/` stubs with proper Java records: `WorkflowCreatedEvent(UUID workflowId, String workflowName)`, `StepCreatedEvent(UUID workflowId, UUID stepId, String stepName)`, `StepCompletedEvent(UUID workflowId, UUID stepId)`
  - _Requirements: 2.7, 5.1, 5.4, 5.5, 5.6_

- [x] 2. Implement KafkaEventPublisher in workflow-service
  - [x] 2.1 Create `KafkaProducerConfig` in `config/` with `ProducerFactory<String, Object>` using `JsonSerializer` for values, `StringSerializer` for keys, `acks=all`, `retries=3`; expose a `KafkaTemplate<String, Object>` bean
  - [x] 2.2 Create `KafkaEventPublisher` in `event/` implementing `EventPublisher`; inject `KafkaTemplate<String, Object>`; for each publish method, call `kafkaTemplate.send(topic, workflowId.toString(), event)` and attach a `.whenComplete()` callback that logs failures at ERROR level without re-throwing
  - [x] 2.3 Annotate `KafkaEventPublisher` with `@Primary` (or `@ConditionalOnProperty(name="kafka.enabled", havingValue="true", matchIfMissing=true)`) so it replaces `StubEventPublisher` as the active bean; annotate `StubEventPublisher` with `@ConditionalOnProperty(name="kafka.enabled", havingValue="false")` for test use
  - [x] 2.4 Add Kafka producer config to `workflow-service/src/main/resources/application.yml`: `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`, `spring.kafka.producer.acks: all`, `spring.kafka.producer.retries: 3`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 6.1, 6.2, 6.7, 7.7_

  - [x] 2.5 Write property test `KafkaEventPublisherPropertyTest` in `workflow-service` test package
    - **Property 2: Producer failure does not propagate to caller** — for any UUID workflowId/stepId and any String workflowName/stepName, when `KafkaTemplate.send` is mocked to throw a `RuntimeException`, each of the three publish methods should return normally without throwing
    - **Property 7: Message key equals workflowId** — for any event published, capture the `ProducerRecord` sent to `KafkaTemplate` and assert `record.key().equals(workflowId.toString())`
    - **Validates: Requirements 2.6, 7.1, 7.7, 8.1**

  - [x] 2.6 Write property test `EventSerializationPropertyTest` in `workflow-service` test package
    - **Property 1: Event serialization round-trip** — for any generated `WorkflowCreatedEvent`, `StepCreatedEvent`, `StepCompletedEvent`, serialize to JSON with `ObjectMapper` then deserialize back and assert equality
    - **Property 6: Null fields serialize as JSON null** — for any event record with a null String field, assert the serialized JSON string contains `"fieldName":null` (key present, value null)
    - **Validates: Requirements 2.5, 5.4, 5.5, 5.6, 5.8, 8.2**

- [x] 3. Checkpoint — workflow-service Kafka producer
  - Ensure all tests pass (`mvn test` in `workflow-service`), ask the user if questions arise.

- [x] 4. Implement WorkflowEventConsumer in ai-agent-service
  - [x] 4.1 Add `spring-kafka` compile dependency and `spring-kafka-test` test dependency to `ai-agent-service/pom.xml`
  - [x] 4.2 Create event records in `kafka/` package: `WorkflowCreatedEvent(UUID workflowId, String workflowName)` and `AiSuggestionGeneratedEvent(UUID workflowId, String suggestion)`
  - [x] 4.3 Create `KafkaConsumerConfig` in `config/` with `ConsumerFactory` using `JsonDeserializer<WorkflowCreatedEvent>`, `auto-offset-reset=earliest`, and `ErrorHandlingDeserializer` wrapping the JSON deserializer; create `DefaultErrorHandler` bean with `DeadLetterPublishingRecoverer` and `FixedBackOff(1000L, 3)`
  - [x] 4.4 Create `KafkaProducerConfig` in `config/` (for publishing `AiSuggestionGeneratedEvent`) with `JsonSerializer`, `acks=all`, `retries=3`; expose `KafkaTemplate<String, Object>` bean
  - [x] 4.5 Implement `WorkflowEventConsumer` in `kafka/`: annotate with `@Component`; inject `AiSuggestionService` and `KafkaTemplate<String, Object>`; implement `@KafkaListener(topics = "workflow.created", groupId = "ai-agent-service") public void onWorkflowCreated(WorkflowCreatedEvent event)` — call `suggest`, publish `AiSuggestionGeneratedEvent` to `ai.suggestion.generated` with key `event.workflowId().toString()`, catch all exceptions and log at ERROR without re-throwing
  - [x] 4.6 Add Kafka config to `ai-agent-service/src/main/resources/application.yml`: `spring.kafka.bootstrap-servers`, consumer `group-id`, `auto-offset-reset: earliest`, producer `acks: all`, `retries: 3`
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.2, 5.7, 6.3, 6.5, 6.6, 6.7, 7.7_

  - [x] 4.7 Write property test `WorkflowEventConsumerPropertyTest` in `ai-agent-service` test package
    - **Property 3: Consumer idempotency** — for any generated `WorkflowCreatedEvent`, calling `onWorkflowCreated` once or N times should result in `AiSuggestionService.suggest` being called each time with the same `workflowId` and `workflowName` (mock the service, verify argument capture)
    - **Property 4: Consumer exception isolation** — for any generated `WorkflowCreatedEvent` where `AiSuggestionService.suggest` is mocked to throw any `RuntimeException`, `onWorkflowCreated` should return normally without throwing
    - **Validates: Requirements 3.2, 3.3, 3.4, 7.2, 7.6**

  - [x] 4.8 Write property test `AiSuggestionEventSerializationPropertyTest` in `ai-agent-service` test package
    - **Property 1 (ai-agent-service): AiSuggestionGeneratedEvent round-trip** — for any generated `AiSuggestionGeneratedEvent`, serialize to JSON then deserialize back and assert equality
    - **Validates: Requirements 5.7, 8.2**

- [x] 5. Checkpoint — ai-agent-service Kafka consumer
  - Ensure all tests pass (`mvn test` in `ai-agent-service`), ask the user if questions arise.

- [x] 6. Create notification-service
  - [x] 6.1 Create `services/notification-service/` directory with `pom.xml` (Spring Boot 3.2.5 parent, `spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-cloud-starter-netflix-eureka-client`, `spring-kafka`, `springdoc-openapi-starter-webmvc-ui`, `jqwik` test, `spring-kafka-test` test); Java 17; artifact `notification-service`
  - [x] 6.2 Create `NotificationServiceApplication` in `src/main/java/com/aiworkflow/notification/` with `@SpringBootApplication` and `@EnableDiscoveryClient`
  - [x] 6.3 Create event records in `event/` package: `WorkflowCreatedEvent`, `StepCreatedEvent`, `StepCompletedEvent`, `AiSuggestionGeneratedEvent` — matching the schemas defined in Requirements 5.4–5.7
  - [x] 6.4 Create `KafkaConsumerConfig` in `config/` with `String` value deserializer (raw JSON string), `auto-offset-reset=earliest`, consumer group `notification-service`, `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` and `FixedBackOff(1000L, 3)`
  - [x] 6.5 Create `NotificationEventConsumer` in `consumer/`: inject `ObjectMapper`; annotate with `@KafkaListener(topics = {"workflow.created", "step.created", "step.completed", "ai.suggestion.generated"}, groupId = "notification-service")`; accept `ConsumerRecord<String, String>`; switch on `record.topic()` to deserialize and log the correct fields at INFO level; on deserialization failure log at ERROR and return normally
  - [x] 6.6 Create `application.yml` for `notification-service`: `server.port: 8084`, `spring.application.name: notification-service`, `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`, Eureka config pointing to `localhost:8761`
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 5.3, 6.4, 6.7_

  - [x] 6.7 Write property test `NotificationEventConsumerPropertyTest` in `notification-service` test package
    - **Property 5: Notification log format completeness** — for any generated `WorkflowCreatedEvent` (random UUID workflowId, random non-blank workflowName), capture the SLF4J log output (use a `ListAppender` or mock logger) and assert the output contains both `workflowId.toString()` and `workflowName`; repeat for `StepCreatedEvent`, `StepCompletedEvent`, and `AiSuggestionGeneratedEvent` with their respective required fields
    - **Validates: Requirements 4.5, 4.6, 4.7, 4.8**

  - [x] 6.8 Write property test `NotificationEventSerializationPropertyTest` in `notification-service` test package
    - **Property 1 (notification-service): All event round-trips** — for any generated instance of each of the four event records, serialize to JSON then deserialize back and assert equality
    - **Validates: Requirements 5.3, 5.4, 5.5, 5.6, 5.7, 8.2**

- [x] 7. Checkpoint — notification-service
  - Ensure all tests pass (`mvn test` in `notification-service`), ask the user if questions arise.

- [x] 8. Docker Compose infrastructure
  - [x] 8.1 Create `infra/docker/docker-compose.yml` with: `zookeeper` service (`confluentinc/cp-zookeeper:7.6.0`, port 2181), `kafka` service (`confluentinc/cp-kafka:7.6.0`, port 9092, `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`, `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092`), and service entries for `workflow-service` (8080), `ai-agent-service` (8083), `notification-service` (8084), `eureka-server` (8761), `api-gateway` (8888) with `KAFKA_BOOTSTRAP_SERVERS: kafka:9092` env var
  - [x] 8.2 Add `KAFKA_BOOTSTRAP_SERVERS` environment variable override to each service's Docker Compose entry so the in-container broker address (`kafka:9092`) overrides the default `localhost:9092`
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 9. Final checkpoint — full integration
  - Ensure all unit and property tests pass across `workflow-service`, `ai-agent-service`, and `notification-service` (`mvn test` in each), ask the user if questions arise.

- [x] 10. Manual verification
  - [x] 10.1 Start the full stack using Docker Compose: `docker compose -f infra/docker/docker-compose.yml up zookeeper kafka` — verify Kafka is accessible on `localhost:9092`
  - [x] 10.2 Start `eureka-server`, then `workflow-service`, `ai-agent-service`, and `notification-service` (in that order); verify all three appear in the Eureka dashboard at `http://localhost:8761`
  - [x] 10.3 Verify `GET http://localhost:8080/actuator/health`, `GET http://localhost:8082/actuator/health`, and `GET http://localhost:8084/actuator/health` all return HTTP 200 with `status: UP`
  - [x] 10.4 Create a workflow via `POST http://localhost:8080/workflows` and observe `notification-service` logs — verify a line matching `event=workflow.created workflowId=<id> workflowName=<name>` appears within a few seconds
  - [x] 10.5 Observe `ai-agent-service` logs after the same workflow creation — verify a line showing `Suggestion:` was logged, confirming `WorkflowEventConsumer` consumed the event and called `AiSuggestionService`
  - [x] 10.6 Observe `notification-service` logs for the `ai.suggestion.generated` event — verify a line matching `event=ai.suggestion.generated workflowId=<id> suggestion=<text>` appears
  - [x] 10.7 Add a step to the workflow via `POST http://localhost:8080/workflows/{id}/steps` and verify `notification-service` logs `event=step.created workflowId=<id> stepId=<id> stepName=<name>`
  - [x] 10.8 Complete the step via `PUT http://localhost:8080/workflows/{id}/steps/{stepId}/complete` and verify `notification-service` logs `event=step.completed workflowId=<id> stepId=<id>`
  - [x] 10.9 Stop `workflow-service` while Kafka is running, then restart it — verify it reconnects and resumes publishing events without errors
  - [x] 10.10 Call through the gateway using PowerShell to create a workflow and confirm end-to-end routing:
    ```powershell
    Invoke-RestMethod -Uri "http://localhost:8888/api/workflows" `
      -Method POST `
      -ContentType "application/json" `
      -Body '{"name":"Onboarding Workflow"}'
    ```
    Verify HTTP 201 and that `notification-service` logs the `workflow.created` event

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- `StubEventPublisher` is retained (not deleted) and activated via `kafka.enabled=false` in test `application.yml` files — this avoids needing a real broker for existing `workflow-service` unit tests
- Property tests use jqwik 1.8.4 with `@Property(tries = 100)` minimum; tag each with `@Tag("Feature: kafka-event-driven, Property N: ...")`
- Integration tests using `@EmbeddedKafka` go in `integration/` and are excluded from the default Surefire run (consistent with existing services)
- The `notification-service` uses a raw `String` consumer (not typed `JsonDeserializer`) to safely handle multiple event schemas on a single `@KafkaListener`
