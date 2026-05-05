# Requirements Document

## Introduction

The AI Agent Service is the Phase 4 microservice of the ai-workflow-microservices platform. It generates AI-powered next-step suggestions for workflows by calling an OpenAI-compatible Chat Completions API. The default provider is **Groq** (free tier, `https://api.groq.com/openai/v1/chat/completions`) which uses the same API format as OpenAI, making the provider fully configurable via `openai.base-url`. The service exposes a REST endpoint that accepts a workflow name and a list of existing step names, builds a contextual prompt, and returns a suggested next step. It is a stateless Spring Boot application with no database, registers with the Eureka Server for service discovery, and is accessible through the API Gateway at `/api/suggestions/**`. When the AI provider is unavailable or misconfigured, the service falls back to a deterministic mock suggestion so that the primary operation never fails. Kafka-based event consumption (`workflow.created`) is deferred to Phase 5 and is noted as a stub in this phase.

## Glossary

- **AI_Agent_Service**: The Spring Boot microservice defined in this document, running on port 8082. It is stateless and thread-safe across concurrent requests.
- **Suggestion_Controller**: The REST controller that exposes `POST /suggestions` and delegates to `AiSuggestionService`.
- **AiSuggestionService**: The component responsible for building a prompt from workflow context, sanitizing AI responses, and returning a suggestion string.
- **AiProviderClient**: The component responsible for making HTTP calls to an OpenAI-compatible Chat Completions API. Default provider is Groq (`https://api.groq.com/openai/v1/chat/completions`); configurable to OpenAI or any compatible endpoint via `openai.base-url`. Also referred to as `OpenAiClient` in code for naming consistency.
- **OpenAiProperties**: The `@ConfigurationProperties(prefix = "openai")` class binding `api-key`, `base-url`, `model`, `temperature`, `max-tokens`, `connect-timeout-ms`, and `read-timeout-ms` from configuration.
- **SuggestionRequest**: The DTO submitted to `POST /suggestions`, containing `workflowName` and `existingSteps`.
- **SuggestionResponse**: The DTO returned from `POST /suggestions`, containing `suggestion` and `source`.
- **SuggestionSource**: An enumeration of the origin of a suggestion: `AI` (returned by the AI provider) or `FALLBACK` (deterministic mock).
- **FallbackReason**: The categorized reason for taking the fallback path: `MISSING_API_KEY`, `TIMEOUT`, `API_ERROR`, or `EMPTY_RESPONSE`.
- **GlobalExceptionHandler**: The `@ControllerAdvice` component that maps domain exceptions to HTTP error responses.
- **Eureka_Client**: The Spring Cloud Netflix Eureka client embedded in the AI_Agent_Service that registers the service with the Eureka Server under the name `ai-agent-service`.
- **API_Gateway**: The Phase 2 Spring Cloud Gateway that routes `/api/suggestions/**` requests to the AI_Agent_Service.

---

## Requirements

### Requirement 1: Generate a Next-Step Suggestion

**User Story:** As a platform user, I want to request an AI-generated next-step suggestion for my workflow, so that I can benefit from intelligent guidance when planning workflow steps.

#### Acceptance Criteria

1. WHEN a `POST /suggestions` request is received with a valid `workflowName` and an `existingSteps` list, THE AI_Agent_Service SHALL return HTTP 200 with a `SuggestionResponse` containing a non-blank `suggestion` string and a `source` field.
2. WHEN the OpenAI API returns a valid completion response, THE AI_Agent_Service SHALL set `source` to `AI` in the `SuggestionResponse`.
3. IF the OpenAI API call fails for any reason (timeout, HTTP error, missing API key, malformed response), THEN THE AI_Agent_Service SHALL set `source` to `FALLBACK` and return a deterministic mock suggestion without propagating the error to the caller.
4. THE AI_Agent_Service SHALL return a non-blank `suggestion` for every valid request regardless of OpenAI API availability.
5. WHEN a `POST /suggestions` request is received with an empty `existingSteps` list, THE AI_Agent_Service SHALL treat it as a valid request and return HTTP 200 with a non-blank `suggestion`.
6. THE AI_Agent_Service SHALL include both `suggestion` and `source` fields in every `SuggestionResponse`.

---

### Requirement 2: Prompt Construction

**User Story:** As a platform engineer, I want the AI prompt to include full workflow context, so that OpenAI can generate relevant and coherent step suggestions.

#### Acceptance Criteria

1. WHEN building a prompt for OpenAI, THE AiSuggestionService SHALL include the `workflowName` in the prompt text.
2. WHEN building a prompt for OpenAI and `existingSteps` is non-empty, THE AiSuggestionService SHALL include all step names from `existingSteps` in the prompt text.
3. WHEN building a prompt for OpenAI and `existingSteps` is empty, THE AiSuggestionService SHALL construct a valid prompt that requests the first step for the given `workflowName`.
4. THE AiSuggestionService SHALL instruct OpenAI to return only the step name as a concise string, without additional explanation.

---

### Requirement 3: AI Provider Integration

**User Story:** As a platform engineer, I want the service to call a configurable OpenAI-compatible Chat Completions API, so that I can use free providers like Groq in development and switch to OpenAI in production without code changes.

#### Acceptance Criteria

1. WHEN calling the AI provider, THE OpenAiClient SHALL send a `POST` request to the URL configured via `openai.base-url` with the `Authorization: Bearer <api-key>` header.
2. THE default value of `openai.base-url` SHALL be `https://api.groq.com/openai/v1/chat/completions` (Groq free tier).
3. THE OpenAiClient SHALL use the model configured via `openai.model` (default: `llama-3.1-8b-instant` for Groq).
4. THE OpenAiClient SHALL use the temperature configured via `openai.temperature` (default: `0.7`).
5. THE OpenAiClient SHALL use the max-tokens limit configured via `openai.max-tokens` (default: `100`).
6. THE OpenAiClient SHALL use `RestTemplate` for HTTP communication.
7. THE OpenAiProperties SHALL bind `openai.api-key` from the `GROQ_API_KEY` environment variable (with `OPENAI_API_KEY` as a fallback alias).
8. IF the `openai.api-key` is blank or absent, THEN THE OpenAiClient SHALL not attempt the HTTP call and SHALL signal failure so that the fallback path is taken.
9. TO switch to OpenAI, an operator SHALL only need to set `openai.base-url=https://api.openai.com/v1/chat/completions`, `openai.model=gpt-4o-mini`, and `openai.api-key` — no code changes required.
10. THE OpenAiClient SHALL enforce a configurable HTTP timeout (connection timeout: 3 seconds, read timeout: 5 seconds by default, both configurable via `openai.connect-timeout-ms` and `openai.read-timeout-ms`). IF the timeout is exceeded, THE OpenAiClient SHALL propagate the exception so that the fallback path is taken.
11. THE OpenAiClient SHALL extract the suggestion from `choices[0].message.content` in the AI provider response.
12. THE OpenAiClient SHOULD retry a failed request once for transient failures (e.g., network errors) before propagating the exception. The retry SHALL occur within the overall timeout budget and SHALL NOT be applied to `MISSING_API_KEY` failures.
13. Rate limiting for AI provider API usage is NOT implemented in Phase 4 and MAY be introduced in future phases.

---

### Requirement 4: Fallback Behaviour

**User Story:** As a platform operator, I want the AI Agent Service to remain operational when the AI provider is unavailable, so that workflow suggestions are always returned without service disruption.

#### Acceptance Criteria

1. WHEN the AI provider call fails for any reason (timeout, HTTP error, missing API key, malformed response), THE AiSuggestionService SHALL return a deterministic fallback suggestion.
2. THE fallback suggestion SHALL follow the format: `"Step <N+1> for '<workflowName>'"` where N is `existingSteps.size()`.
3. THE fallback suggestion SHALL be non-blank for any valid `SuggestionRequest`.
4. WHEN the fallback path is taken, THE AI_Agent_Service SHALL set `source` to `FALLBACK` in the `SuggestionResponse`.
5. A failure in THE OpenAiClient SHALL NOT cause `POST /suggestions` to return an HTTP error status — the endpoint SHALL always return HTTP 200 for valid requests.
6. Circuit breaker patterns (e.g., Resilience4j) are NOT implemented in Phase 4 and MAY be added in future phases.

---

### Requirement 5: Input Validation and Size Limits

**User Story:** As an API consumer, I want the service to validate my request, so that I receive clear feedback when I submit an invalid payload and the service is protected from excessively large inputs.

#### Acceptance Criteria

1. IF a `POST /suggestions` request is received with a blank or missing `workflowName`, THEN THE AI_Agent_Service SHALL return HTTP 400 with a descriptive validation error message.
2. IF a `POST /suggestions` request is received with a `workflowName` exceeding 255 characters, THEN THE AI_Agent_Service SHALL return HTTP 400.
3. IF a `POST /suggestions` request is received with a null `existingSteps` field, THEN THE AI_Agent_Service SHALL treat it as an empty list and return HTTP 200.
4. IF a `POST /suggestions` request is received with more than 50 steps in `existingSteps`, THEN THE AI_Agent_Service SHALL return HTTP 400 to prevent excessively large prompts.
5. THE AI_Agent_Service SHALL accept `existingSteps` entries that are empty strings without returning an error — step name validation is the responsibility of the caller.
6. THE AiSuggestionService SHALL include at most the last 10 steps from `existingSteps` in the prompt to control token usage, even when the full list is within the 50-step limit.

---

### Requirement 6: Service Discovery Registration

**User Story:** As a platform operator, I want the AI Agent Service to register with the Eureka Server, so that the API Gateway can discover and route suggestion traffic to it dynamically.

#### Acceptance Criteria

1. THE AI_Agent_Service SHALL register itself with the Eureka Server using the service name `ai-agent-service`.
2. WHEN the AI_Agent_Service starts, THE AI_Agent_Service SHALL send a registration request to the Eureka Server at `http://localhost:8761/eureka`.
3. WHILE the AI_Agent_Service is running, THE AI_Agent_Service SHALL send periodic heartbeats to the Eureka Server to maintain its registration.
4. IF the Eureka Server is unavailable when the AI_Agent_Service starts, THEN THE AI_Agent_Service SHALL start successfully and retry Eureka registration in the background without throwing a startup exception.

---

### Requirement 7: API Gateway Route Integration

**User Story:** As an API consumer, I want to call the suggestion API through the gateway, so that I only need to know one address for all platform services.

#### Acceptance Criteria

1. THE API_Gateway SHALL route requests with path `/api/suggestions/**` to the AI_Agent_Service using the load-balanced URI `lb://ai-agent-service`.
2. THE API_Gateway SHALL strip the `/api` prefix before forwarding to the AI_Agent_Service, so that the AI_Agent_Service receives requests at `/suggestions/**`.
3. WHEN the AI_Agent_Service returns a response, THE API_Gateway SHALL return that response to the caller with the original HTTP status code and body unchanged.

---

### Requirement 8: API Contract and Documentation

**User Story:** As a developer integrating with the platform, I want a documented OpenAPI spec for the AI Agent Service, so that I can understand and consume the suggestion API.

#### Acceptance Criteria

1. THE AI_Agent_Service SHALL expose an OpenAPI 3 specification at `/v3/api-docs`.
2. THE AI_Agent_Service SHALL expose a Swagger UI at `/swagger-ui.html`.
3. THE AI_Agent_Service SHALL document all request and response DTOs with field descriptions and validation constraints.
4. ALL API responses SHALL use DTOs — no internal implementation classes SHALL be returned directly from any endpoint.

---

### Requirement 9: Error Handling

**User Story:** As an API consumer, I want consistent and descriptive error responses, so that I can handle failures programmatically without ambiguity.

#### Acceptance Criteria

1. WHEN a `MethodArgumentNotValidException` is raised (Bean Validation failure), THE GlobalExceptionHandler SHALL return HTTP 400 with a JSON error body listing the validation errors.
2. WHEN an `HttpMessageNotReadableException` is raised (malformed JSON body), THE GlobalExceptionHandler SHALL return HTTP 400 with a JSON error body.
3. WHEN any unhandled exception is raised, THE GlobalExceptionHandler SHALL return HTTP 500 with a generic JSON error body and SHALL NOT expose internal stack traces or implementation details.

---

### Requirement 11: Response Sanitization and Logging

**User Story:** As a platform engineer, I want AI responses to be sanitized and all suggestion activity to be logged, so that the service produces clean output and is debuggable in production.

#### Acceptance Criteria

1. WHEN the AI provider returns a response, THE AiSuggestionService SHALL trim leading and trailing whitespace from the suggestion string before returning it.
2. THE AiSuggestionService SHALL return only the first line of the AI response if the response contains multiple lines, ensuring a concise single-step string.
3. THE AiSuggestionService SHALL truncate the sanitized suggestion to a maximum of 200 characters to prevent excessively long AI output.
4. IF the trimmed AI response is blank, THE AiSuggestionService SHALL treat it as a failure and use the fallback path with reason `EMPTY_RESPONSE`.
5. WHEN processing a suggestion request, THE AI_Agent_Service SHALL log at INFO level: the `workflowName`, the number of existing steps, and the `source` of the response (`AI` or `FALLBACK`).
6. THE AI_Agent_Service SHALL NOT log the full prompt text, API keys, or raw AI response content — only metadata SHALL be logged.
7. WHEN the fallback path is taken due to an AI provider failure, THE AI_Agent_Service SHALL log a WARNING including the `FallbackReason` category (`MISSING_API_KEY`, `TIMEOUT`, `API_ERROR`, or `EMPTY_RESPONSE`) and the exception message.
8. THE AiSuggestionService SHALL treat all user-supplied input (`workflowName`, `existingSteps`) as untrusted and construct prompts defensively — input values SHALL be included as data, not as instructions, to mitigate prompt injection risks.
9. THE AI_Agent_Service SHALL be stateless and thread-safe — no shared mutable state SHALL exist across concurrent requests.
10. IF the AI provider returns usage metadata (e.g., `usage.total_tokens`), THE AI_Agent_Service SHOULD log it at DEBUG level for monitoring purposes.

---

### Requirement 12: Kafka Integration Stub (Phase 5 Preparation)

**User Story:** As a platform engineer, I want the AI Agent Service to note the planned Kafka integration, so that Phase 5 implementation has a clear extension point.

#### Acceptance Criteria

1. THE AI_Agent_Service SHALL include a stub or comment in the codebase indicating the planned consumption of `workflow.created` events from Kafka in Phase 5.
2. The Kafka consumer implementation is NOT required in Phase 4 — the stub serves as a documented extension point only.
