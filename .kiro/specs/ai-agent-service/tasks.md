# Implementation Plan: AI Agent Service (Phase 4)

## Overview

Build the `ai-agent-service` standalone Spring Boot microservice that generates AI-powered next-step suggestions for workflows. Tasks are ordered so the project scaffold and configuration come first, then the OpenAI client and properties, then the service layer with fallback logic, then the REST layer, and finally tests and gateway integration.

## Tasks

- [x] 1. Project scaffold
  - [x] 1.1 Create `services/ai-agent-service/pom.xml` with Spring Boot 3.2.5, Spring Web, Spring Validation, SpringDoc OpenAPI 2.5.0, Spring Cloud Eureka client (spring-cloud.version=2023.0.1), Spring Boot Actuator, jqwik 1.8.4, JUnit 5, and Mockito dependencies; add `-Duser.timezone=Asia/Kolkata` to spring-boot-maven-plugin configuration; no database or Flyway dependencies
  - [x] 1.2 Create main application class `AiAgentServiceApplication` under `com.aiworkflow.agent` with `@SpringBootApplication` and `@EnableConfigurationProperties`
  - [x] 1.3 Create `application.yml` with `server.port: 8082`, `spring.application.name: ai-agent-service`, Eureka client configuration (`defaultZone: http://localhost:8761/eureka/`), SpringDoc configuration, and `openai` properties block: `base-url: ${OPENAI_BASE_URL:https://api.groq.com/openai/v1/chat/completions}`, `api-key: ${GROQ_API_KEY:}`, `model: ${OPENAI_MODEL:llama-3.1-8b-instant}`, `temperature: 0.7`, `max-tokens: 100`, `connect-timeout-ms: 3000`, `read-timeout-ms: 5000`; `api-key` defaults to empty which triggers the fallback path
  - [x] 1.4 Validate `openai.base-url` and `openai.model` at startup using `@PostConstruct` in `OpenAiProperties` or a `ApplicationRunner` bean — log a WARNING if either is blank so misconfiguration is caught early rather than at first request
  - _Requirements: 6.1, 6.2, 8.1, 8.2_

- [x] 2. Configuration and DTOs
  - [x] 2.1 Create `OpenAiProperties` class in `config/` annotated with `@ConfigurationProperties(prefix = "openai")` binding `apiKey` (from `GROQ_API_KEY` env var), `baseUrl` (default: Groq endpoint), `model` (default: `llama-3.1-8b-instant`), `temperature`, and `maxTokens`
  - [x] 2.2 Create `SuggestionSource` enum in `model/` with values `AI` and `FALLBACK`; create `FallbackReason` enum with values `MISSING_API_KEY`, `TIMEOUT`, `API_ERROR`, `EMPTY_RESPONSE`
  - [x] 2.3 Create `SuggestionRequest` record in `dto/` with `@NotBlank @Size(max=255) String workflowName` and `@Size(max=50) List<String> existingSteps` (null-safe — treat null as empty list)
  - [x] 2.4 Create `SuggestionResponse` record in `dto/` with `String suggestion` and `SuggestionSource source`
  - [x] 2.5 Create internal OpenAI HTTP model records in `client/model/`: `ChatCompletionRequest` (model, messages, temperature, max_tokens), `ChatMessage` (role, content), `ChatCompletionResponse` (choices), and `Choice` (message); annotate with Jackson `@JsonProperty("max_tokens")` for the maxTokens field
  - _Requirements: 1.1, 1.6, 3.2, 3.3, 3.4, 5.1_

- [x] 3. Exception classes and GlobalExceptionHandler
  - [x] 3.1 Create `OpenAiUnavailableException` in `exception/` (unchecked) — thrown by `OpenAiClientImpl` when API key is blank or absent
  - [x] 3.2 Create `GlobalExceptionHandler` (`@RestControllerAdvice`) in `exception/` mapping `MethodArgumentNotValidException` → 400 with validation details, `HttpMessageNotReadableException` → 400, and catch-all `Exception` → 500 without exposing stack traces; return JSON error bodies consistent with other platform services
  - _Requirements: 9.1, 9.2, 9.3_

- [x] 4. OpenAI client
  - [x] 4.1 Create `OpenAiClient` interface in `client/` with `String complete(String prompt)`
  - [x] 4.2 Implement `OpenAiClientImpl` in `client/impl/`: inject `OpenAiProperties` and a `RestTemplate` bean; if `apiKey` is blank throw `OpenAiUnavailableException(FallbackReason.MISSING_API_KEY)` without making HTTP call; build `ChatCompletionRequest` with configured model, temperature, maxTokens, and a single user `ChatMessage` containing the prompt; POST to `openAiProperties.getBaseUrl()` with `Authorization: Bearer <apiKey>` header; on `ResourceAccessException` retry once before propagating; extract and return `choices[0].message.content` from the response
  - [x] 4.3 Create `RestTemplateConfig` in `config/` providing a `RestTemplate` `@Bean` configured with connection timeout (`openai.connect-timeout-ms`, default 3000ms) and read timeout (`openai.read-timeout-ms`, default 5000ms) using `SimpleClientHttpRequestFactory` — not the default no-timeout instance
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.10, 3.11_

- [x] 5. AI suggestion service
  - [x] 5.1 Create `AiSuggestionService` interface in `service/` with `SuggestionResponse suggest(SuggestionRequest request)`
  - [x] 5.2 Implement `AiSuggestionServiceImpl` in `service/impl/`: inject `OpenAiClient`; implement `buildPrompt(String workflowName, List<String> existingSteps)` that uses the last 10 steps and includes `workflowName` and step names as data values (not instructions) to mitigate prompt injection; defensively validate input sizes before building prompt (workflowName ≤ 255, steps ≤ 50); implement `fallbackSuggestion(SuggestionRequest request)` returning `"Step <N+1> for '<workflowName>'"` where N is `existingSteps.size()`; in `suggest`: call `buildPrompt`, call `OpenAiClient.complete`, trim + take first line + truncate to 200 chars — if non-blank return `SuggestionResponse(result, SuggestionSource.AI)`; catch any exception or blank result, determine `FallbackReason`, log WARNING with reason + exception message, return `SuggestionResponse(fallbackSuggestion(request), SuggestionSource.FALLBACK)`; log at INFO: workflowName, step count, source; log token usage at DEBUG if available; never log API keys or prompt content; ensure no shared mutable state (all fields final, injected via constructor)
  - [x] 5.3 Add Kafka stub class `WorkflowEventConsumer` in `kafka/` with a comment block documenting the planned Phase 5 `@KafkaListener` for `workflow.created` events — no implementation required
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 4.1, 4.2, 4.3, 4.4, 4.5, 5.6, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 12.1, 12.2_

- [x] 6. Controller layer
  - [x] 6.1 Create `SuggestionController` in `controller/` with `POST /suggestions` accepting `@Valid @RequestBody SuggestionRequest`, delegating to `AiSuggestionService.suggest`, and returning `ResponseEntity<SuggestionResponse>` with HTTP 200; treat null `existingSteps` as empty list before passing to service
  - [x] 6.2 Add SpringDoc `@Operation` and `@ApiResponse` annotations documenting HTTP 200 (success), HTTP 400 (validation failure), and HTTP 500 (unexpected error)
  - _Requirements: 1.1, 1.5, 5.1, 5.2, 5.3, 8.3, 8.4_

- [x] 7. Unit tests
  - [x] 7.1 Write unit tests for `SuggestionController` using `@WebMvcTest` / `MockMvc` — valid request returns 200 with suggestion and source, blank workflowName returns 400, malformed JSON returns 400, null existingSteps treated as empty list
  - [x] 7.2 Write unit tests for `AiSuggestionServiceImpl` — mock `OpenAiClient`; verify source=AI when client returns a value, source=FALLBACK when client throws any exception, fallback suggestion formula (`"Step <N+1> for '<workflowName>'"`) output, prompt uses last 10 steps only when list exceeds 10, prompt contains workflowName, prompt contains all step names when list is ≤10, prompt is non-blank when list is empty, blank AI response triggers EMPTY_RESPONSE fallback, multi-line AI response is trimmed to first line, response truncated to 200 chars, FallbackReason is correctly categorized per exception type (`MISSING_API_KEY` for `OpenAiUnavailableException`, `TIMEOUT` for `ResourceAccessException` with timeout, `API_ERROR` for HTTP errors, `EMPTY_RESPONSE` for blank content)
  - [x] 7.3 Write unit tests for `OpenAiClientImpl` — mock `RestTemplate`; verify correct URL (`baseUrl`) is called, `Authorization: Bearer <apiKey>` header format is exactly correct, request body contains configured model/temperature/max_tokens, blank API key throws `OpenAiUnavailableException(MISSING_API_KEY)` without making HTTP call, `choices[0].message.content` is extracted correctly, `ResourceAccessException` triggers one retry
  - [x] 7.4 Write unit tests for `GlobalExceptionHandler` — verify 400 for validation failure, 400 for malformed JSON, 500 for unhandled exception, no stack trace in response body
  - [x] 7.5 Write integration smoke test verifying `GET /actuator/health` returns HTTP 200 with `status: UP` when the service starts
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 3.1, 3.2, 3.3, 3.4, 3.7, 5.1, 9.1, 9.2, 9.3_

- [x] 8. Property-based tests (jqwik)
  - [x] 8.1 Write property test for Property 1 — for any valid SuggestionRequest (non-blank workflowName, any existingSteps list), response has non-blank suggestion and non-null source
    - **Property 1: Suggestion is always non-blank**
    - **Validates: Requirements 1.1, 1.4, 1.6**
  - [x] 8.2 Write property test for Property 2 — for any valid SuggestionRequest with OpenAiClient configured to throw, response is HTTP 200 with source=FALLBACK and non-blank suggestion
    - **Property 2: Fallback is used when OpenAI is unavailable**
    - **Validates: Requirements 1.3, 4.1, 4.2, 4.3, 4.4**
  - [x] 8.3 Write property test for Property 3 — for any valid SuggestionRequest with OpenAiClient returning a non-blank string, response has source=AI and suggestion equal to the returned string
    - **Property 3: AI source is used when OpenAI responds**
    - **Validates: Requirements 1.2**
  - [x] 8.4 Write property test for Property 4 — for any non-blank workflowName and any list of step names, the prompt built by AiSuggestionServiceImpl contains the workflowName and all step names
    - **Property 4: Prompt contains full workflow context**
    - **Validates: Requirements 2.1, 2.2, 2.3**
  - [x] 8.5 Write property test for Property 5 — for any blank string (empty or whitespace-only) as workflowName, POST /suggestions returns HTTP 400
    - **Property 5: Validation rejects blank workflowName**
    - **Validates: Requirements 5.1**
  - [x] 8.6 Write property test for Property 6 — for any non-blank workflowName with empty existingSteps list, POST /suggestions returns HTTP 200 with non-blank suggestion
    - **Property 6: Empty steps list is valid**
    - **Validates: Requirements 1.5, 5.2**
  - [x] 8.7 Write property test for Property 7 — for any valid SuggestionRequest, response always contains both suggestion and source fields with source being exactly AI or FALLBACK
    - **Property 7: Response structure invariant**
    - **Validates: Requirements 1.6, 4.3**

- [x] 9. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. API Gateway route update
  - [x] 10.1 Add route to `services/api-gateway/src/main/resources/application.yml` — id `ai-agent-service`, uri `lb://ai-agent-service`, predicate `Path=/api/suggestions/**`, filter `StripPrefix=1`
  - _Requirements: 7.1, 7.2, 7.3_

- [x] 11. Final checkpoint — Ensure all tests pass
  - Run `mvn test` across `ai-agent-service` and `api-gateway`; ensure all tests pass, ask the user if questions arise.

- [x] 12. Manual verification
  - [x] 12.1 Start `eureka-server`, `api-gateway`, and `ai-agent-service` (in that order); verify `ai-agent-service` appears in the Eureka dashboard at `http://localhost:8761`
  - [x] 12.2 Verify `GET http://localhost:8082/actuator/health` returns HTTP 200 with `status: UP`
  - [x] 12.3 Test `POST /suggestions` via Swagger UI at `http://localhost:8082/swagger-ui.html` with a valid request body — verify HTTP 200 and a non-blank suggestion in the response
  - [x] 12.4 Test `POST /suggestions` with a blank `workflowName` via Swagger UI — verify HTTP 400 is returned
  - [x] 12.5 Test `POST /suggestions` with `GROQ_API_KEY` unset — verify HTTP 200 is returned with `source: FALLBACK`
  - [x] 12.6 Set `GROQ_API_KEY` to your Groq API key (free from console.groq.com) and call `POST /suggestions` — verify HTTP 200 with `source: AI` and a real AI-generated suggestion
  - [x] 12.7 Call through the gateway using PowerShell:
    ```powershell
    Invoke-RestMethod -Uri "http://localhost:8090/api/suggestions" `
      -Method POST `
      -ContentType "application/json" `
      -Body '{"workflowName":"Onboarding","existingSteps":["Create account"]}'
    ```
    Verify HTTP 200 and a non-blank suggestion — confirms gateway routing and `StripPrefix=1` are working correctly

## Notes

- No database or Flyway dependencies — `ai-agent-service` is fully stateless and thread-safe; all component fields must be final and injected via constructor
- Default provider is **Groq** (free tier) — get a free API key at https://console.groq.com
- `openai.api-key` MUST NOT be hardcoded — provide via `GROQ_API_KEY` env var; empty value triggers the fallback path for local dev without a key
- Startup validation: log WARNING if `openai.base-url` or `openai.model` is blank — catches misconfiguration before first request
- To switch to OpenAI: set `OPENAI_BASE_URL=https://api.openai.com/v1/chat/completions`, `OPENAI_MODEL=gpt-4o-mini`, `GROQ_API_KEY=<openai-key>` — no code changes needed
- To use Ollama locally (zero cost, offline): set `OPENAI_BASE_URL=http://localhost:11434/v1/chat/completions`, `OPENAI_MODEL=llama3.2`, leave `GROQ_API_KEY` empty (Ollama doesn't require auth)
- `RestTemplate` MUST be configured as a `@Bean` with explicit timeouts via `SimpleClientHttpRequestFactory` — never use the default no-timeout instance
- Retry: 1 retry on `ResourceAccessException` only; no retry on `MISSING_API_KEY` or HTTP 4xx/5xx errors
- Response sanitization pipeline: trim → first line → truncate to 200 chars → blank check → fallback if blank
- Prompt injection: always include user input as data values, never as instructions
- `FallbackReason` enum enables structured WARNING logs — use it consistently in `AiSuggestionServiceImpl`
- Authorization header must be exactly `Authorization: Bearer <apiKey>` — verify format in unit tests
- The `StripPrefix=1` filter on the gateway route removes the `/api` prefix so `ai-agent-service` receives `/suggestions` not `/api/suggestions`
- No Spring Security in Phase 4 — all endpoints are open; authentication is enforced at the API Gateway level
- jqwik property tests should use `@Property(tries = 100)` minimum
- The Kafka stub in `WorkflowEventConsumer` is intentionally empty — it exists only as a documented extension point for Phase 5
- Jackson field name `max_tokens` must be mapped using `@JsonProperty("max_tokens")` on the `ChatCompletionRequest` record
