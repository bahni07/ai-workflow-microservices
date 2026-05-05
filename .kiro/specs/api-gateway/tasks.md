# Implementation Plan: API Gateway (Phase 2)

## Overview

Build two new standalone Spring Boot services (`eureka-server` and `api-gateway`) and update `workflow-service` to register as a Eureka client. All three changes are pure configuration and wiring — no business logic is added. Tasks are ordered so the registry comes up first, then the gateway, then the workflow-service update.

## Tasks

- [x] 1. Create `eureka-server` service scaffold
  - Create `services/eureka-server/` directory with `pom.xml`
  - Import Spring Cloud BOM (2023.0.x / Leyton) in `<dependencyManagement>`
  - Add dependencies: `spring-cloud-starter-netflix-eureka-server`, `spring-boot-starter-actuator`
  - Create `EurekaServerApplication.java` in `com.aiworkflow.eureka` with `@SpringBootApplication` and `@EnableEurekaServer`
  - Create `src/main/resources/application.yml` with port 8761, `registerWithEureka: false`, `fetchRegistry: false`, and `management.endpoints.web.exposure.include=health`
  - _Requirements: 1.1, 1.2, 1.3, 1.7, 1.8_

- [x] 2. Verify Eureka Server startup and health
  - [x] 2.1 Write integration test for Eureka Server health endpoint
    - Use `@SpringBootTest(webEnvironment = DEFINED_PORT)` to start the server on port 8761
    - Assert `GET /actuator/health` returns HTTP 200 with `{"status":"UP"}`
    - Assert `GET /actuator/health` returns 200 without any `Authorization` header (no auth required)
    - _Requirements: 1.6, 1.7_
  - [x] 2.2 Write integration test asserting Eureka Server does not self-register
    - Query `GET /eureka/apps/EUREKA-SERVER` and assert HTTP 404
    - _Requirements: 1.3_

- [x] 3. Create `api-gateway` service scaffold
  - Create `services/api-gateway/` directory with `pom.xml`
  - Import Spring Cloud BOM (2023.0.x) in `<dependencyManagement>`
  - Add dependencies: `spring-cloud-starter-gateway` (reactive), `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-loadbalancer`, `spring-boot-starter-actuator`
  - Do NOT add `spring-boot-starter-web` — gateway requires the reactive stack
  - Create `ApiGatewayApplication.java` in `com.aiworkflow.gateway` with `@SpringBootApplication` only (no extra annotations needed)
  - Create `src/main/resources/application.yml` with:
    - `server.port: 8090`
    - `spring.application.name: api-gateway`
    - Route: id `workflow-service`, uri `lb://workflow-service`, predicate `Path=/api/workflows/**` (no filters)
    - Eureka client pointing to `http://localhost:8761/eureka/`
    - `management.endpoints.web.exposure.include=health` with `show-details: always`
  - _Requirements: 2.1, 2.2, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.12, 3.1, 3.2_

- [x] 4. Verify Gateway startup, health, and routing
  - [x] 4.1 Write integration test for Gateway health endpoint
    - Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a mock/stub Eureka environment
    - Assert `GET /actuator/health` returns HTTP 200
    - Assert response body contains `discoveryComposite` component
    - Assert endpoint is accessible without authentication
    - _Requirements: 2.3, 2.4, 2.9_
  - [x] 4.2 Write integration test for workflow route — 503 when no instances registered
    - Start gateway with an empty Eureka registry (no workflow-service registered)
    - Assert `GET /api/workflows/anything` returns HTTP 503
    - _Requirements: 3.4_
  - [x] 4.3 Write property test: any matching path is routed and path is preserved
    - Use jqwik `@Property(tries = 100)` with WireMock stubbing a downstream workflow-service instance
    - Generate random path suffixes; assert WireMock received the request at `/api/workflows/<suffix>` unchanged
    - Tag: **Feature: api-gateway, Property 1: Any matching path is routed and path is preserved**
    - _Requirements: 3.1, 3.2_
  - [x] 4.4 Write property test: response passthrough is transparent
    - Use jqwik `@Property(tries = 100)` with WireMock
    - Generate random HTTP status codes (200–599) and response bodies; assert caller receives identical status and body
    - Include edge-case: 4xx and 5xx responses from downstream are propagated unchanged
    - Tag: **Feature: api-gateway, Property 2: Response passthrough is transparent**
    - _Requirements: 3.3, 3.5_

- [x] 5. Checkpoint — Ensure all tests pass
  - Run `mvn test` in both `services/eureka-server` and `services/api-gateway`
  - Ensure all tests pass; ask the user if questions arise.

- [x] 6. Update `workflow-service` with Eureka client registration
  - Add Spring Cloud BOM import to `services/workflow-service/pom.xml` `<dependencyManagement>`
  - Add `spring-cloud-starter-netflix-eureka-client` dependency to `pom.xml`
  - Add to `services/workflow-service/src/main/resources/application.yml`:
    - `spring.application.name: workflow-service`
    - `eureka.client.serviceUrl.defaultZone: http://localhost:8761/eureka/`
    - `eureka.instance.instanceId: ${spring.application.name}:${server.port}`
    - `eureka.instance.preferIpAddress: false`
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 7. Verify workflow-service Eureka registration
  - [x] 7.1 Write integration test for workflow-service Eureka registration metadata
    - Start workflow-service with Testcontainers PostgreSQL and a real (or embedded) Eureka server
    - Query Eureka registry and assert an entry exists for `workflow-service` with correct `instanceId` and `port`
    - _Requirements: 4.1, 4.6_
  - [x] 7.2 Write integration test: workflow-service starts successfully when Eureka is unavailable
    - Start workflow-service without a running Eureka server
    - Assert the application context loads and `GET /actuator/health` returns HTTP 200
    - _Requirements: 4.5, 6.2_
  - [x] 7.3 Write property test: registration round trip
    - Use jqwik `@Property(tries = 100)` with a mock Eureka registry
    - For any generated service name and port, registering an instance and querying the registry returns an entry with matching `appName`, `instanceId`, and `port`
    - Tag: **Feature: api-gateway, Property 3: Registration round trip**
    - _Requirements: 1.4, 4.1, 4.6_

- [x] 8. Final checkpoint — Ensure all tests pass
  - Run `mvn test` across all three services (`eureka-server`, `api-gateway`, `workflow-service`)
  - Ensure all tests pass; ask the user if questions arise.

- [ ] 9. Manual end-to-end verification
  - [x] 9.1 Start `eureka-server`, then `workflow-service`, then `api-gateway` (in that order)
  - [x] 9.2 Open Eureka dashboard at `http://localhost:8761` and verify both `workflow-service` and `api-gateway` appear as registered instances
  - [x] 9.3 Call `curl http://localhost:8090/api/workflows` and verify the request is routed to `workflow-service` (expect a valid JSON response, not a 503)
  - [x] 9.4 Stop `workflow-service` and call `curl http://localhost:8090/api/workflows` again — verify the gateway returns HTTP 503
  - [ ] 9.5 Start a second instance of `workflow-service` on a different port (e.g. 8081) and verify the Eureka dashboard shows two instances; send several requests through the gateway and confirm both instances receive traffic (round-robin load balancing)

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Ensure ports 8761, 8080, and 8090 are free before starting services locally
- WireMock (`com.github.tomakehurst:wiremock-jre8` or `wiremock-standalone`) is the recommended tool for stubbing downstream HTTP in gateway tests
- The Spring Cloud BOM version `2023.0.x` (Leyton) is required for Spring Boot 3.2.x compatibility — do not use older release trains
- `spring-boot-starter-web` must NOT appear in `api-gateway/pom.xml` — it conflicts with the reactive gateway stack
- Eureka self-protection mode may interfere with integration tests; set `eureka.server.enable-self-preservation=false` in test profiles
- Property tests for the gateway require a mechanism to inject a mock Eureka registry so `lb://workflow-service` resolves to the WireMock server — use `@MockBean` on the `ReactorLoadBalancer` or configure a static `ServiceInstanceListSupplier`
- Route `id` values in `application.yml` should describe the routing purpose (e.g. `workflow-route`) rather than repeating the service name
