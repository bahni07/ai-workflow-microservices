# Requirements Document

## Introduction

Phase 2 of the ai-workflow-microservices platform introduces a service discovery layer and a unified API gateway. This phase adds two new standalone Spring Boot services:

- **Eureka Server** (`services/eureka-server`) — a Netflix Eureka service registry that all microservices register with, enabling dynamic service discovery.
- **API Gateway** (`services/api-gateway`) — a Spring Cloud Gateway application that acts as the single entry point for all external traffic, routing requests to downstream services via load-balanced URIs resolved through Eureka.

Additionally, the existing `workflow-service` is updated to register itself as a Eureka client.

No authentication, no Kafka, and no new business logic are introduced in this phase.

## Glossary

- **Eureka_Server**: The Netflix Eureka service registry application running at `services/eureka-server` on port 8761.
- **Gateway**: The Spring Cloud Gateway application running at `services/api-gateway` on port 8090.
- **Workflow_Service**: The existing Phase 1 microservice running at `services/workflow-service` on port 8080.
- **Eureka_Client**: Any Spring Boot service configured with `spring-cloud-starter-netflix-eureka-client` that registers itself with the Eureka_Server.
- **Service_Registry**: The Eureka_Server's in-memory registry of all currently registered service instances.
- **Load_Balanced_URI**: A URI of the form `lb://<service-name>` that Spring Cloud LoadBalancer resolves to a live instance via the Service_Registry.
- **Route**: A Gateway configuration entry that maps an incoming request predicate (e.g. path prefix) to a downstream Load_Balanced_URI.
- **Health_Endpoint**: The Spring Boot Actuator `/actuator/health` endpoint that returns the current health status of a service.
- **Spring_Cloud_LoadBalancer**: The Spring Cloud client-side load balancer (not Ribbon) used to resolve `lb://` URIs to registered service instances.

---

## Requirements

### Requirement 1: Eureka Server Setup

**User Story:** As a platform operator, I want a central service registry, so that all microservices can discover each other dynamically without hardcoded addresses.

#### Acceptance Criteria

1. THE Eureka_Server SHALL start as a standalone Spring Boot application on port 8761.
2. THE Eureka_Server SHALL expose the Eureka dashboard UI at `http://localhost:8761`.
3. THE Eureka_Server SHALL NOT register itself as a Eureka client (i.e. `registerWithEureka: false` and `fetchRegistry: false`).
4. WHEN a Eureka_Client sends a registration request, THE Eureka_Server SHALL add the client's instance to the Service_Registry.
5. WHEN a registered Eureka_Client stops sending heartbeats for more than the configured eviction threshold, THE Eureka_Server SHALL remove that instance from the Service_Registry.
6. THE Eureka_Server SHALL expose a Health_Endpoint at `/actuator/health` returning HTTP 200 when the server is running.
7. THE Eureka_Server SHALL expose the Health_Endpoint without requiring authentication.
8. THE Eureka_Server SHALL be packaged as an independently executable JAR via `mvn package` with its own `pom.xml` under `services/eureka-server/`.
9. THE Eureka_Server SHALL log service registration and deregistration events at INFO level.

---

### Requirement 2: API Gateway Setup

**User Story:** As a platform operator, I want a single entry point for all API traffic, so that clients do not need to know the addresses of individual microservices.

#### Acceptance Criteria

1. THE Gateway SHALL start as a standalone Spring Boot application on port 8090.
2. THE Gateway SHALL register itself as a Eureka_Client with the Eureka_Server using the service name `api-gateway`.
3. THE Gateway SHALL expose a Health_Endpoint at `/actuator/health` returning HTTP 200 when the gateway is running.
4. WHEN the Gateway's Eureka_Client connection is healthy, THE Gateway's Health_Endpoint SHALL include a `discoveryComposite` component with status `UP`.
5. WHEN the Eureka_Server is unavailable at startup, THE Gateway SHALL still start and log a warning rather than failing.
6. THE Gateway SHALL use reactive (WebFlux-based) Spring Cloud Gateway — not the MVC-based variant.
7. THE Gateway SHALL use Spring_Cloud_LoadBalancer (not Ribbon) for resolving Load_Balanced_URIs.
8. THE Gateway SHALL define all routes using `application.yml` configuration (not Java DSL) in Phase 2.
9. THE Gateway SHALL expose the Health_Endpoint without requiring authentication.
10. THE Gateway SHALL be packaged as an independently executable JAR via `mvn package` with its own `pom.xml` under `services/api-gateway/`.
11. THE Gateway SHALL log routing events at INFO level.
12. THE Gateway SHALL rely on default timeout and retry behavior of Spring Cloud Gateway in Phase 2 (no custom timeout or retry configuration).

---

### Requirement 3: Workflow Service Route

**User Story:** As an API consumer, I want to call workflow APIs through the gateway, so that I only need to know one address regardless of how many workflow-service instances exist.

#### Acceptance Criteria

1. WHEN a request arrives at the Gateway with a path matching `/api/workflows/**`, THE Gateway SHALL forward the request to the Workflow_Service using the Load_Balanced_URI `lb://workflow-service`.
2. THE Gateway SHALL preserve the full request path (including the `/api/workflows/` prefix) when forwarding to the Workflow_Service — no path rewriting or prefix stripping SHALL occur.
3. WHEN the Workflow_Service returns a response, THE Gateway SHALL return that response to the caller with the original HTTP status code and body unchanged.
4. WHEN no live Workflow_Service instance is registered in the Service_Registry, THE Gateway SHALL return HTTP 503 to the caller.
5. WHEN the Workflow_Service returns an error response (4xx or 5xx), THE Gateway SHALL propagate that response to the caller without modification.

---

### Requirement 4: Workflow Service Eureka Client Registration

**User Story:** As a platform operator, I want the workflow-service to register with Eureka, so that the gateway can discover and route to it dynamically.

#### Acceptance Criteria

1. THE Workflow_Service SHALL register itself with the Eureka_Server using the service name `workflow-service`.
2. WHEN the Workflow_Service starts, THE Workflow_Service SHALL send a registration request to the Eureka_Server at `http://localhost:8761/eureka`.
3. WHILE the Workflow_Service is running, THE Workflow_Service SHALL send periodic heartbeats to the Eureka_Server to maintain its registration.
4. WHEN the Workflow_Service shuts down gracefully, THE Workflow_Service SHALL deregister itself from the Eureka_Server.
5. IF the Eureka_Server is unavailable when the Workflow_Service starts, THEN THE Workflow_Service SHALL start successfully and retry Eureka registration in the background.
6. THE Workflow_Service SHALL expose instance metadata including `instanceId` and `port` in its Eureka registration for observability.
7. THE Workflow_Service SHALL log Eureka registration and deregistration events at INFO level.

---

### Requirement 5: Service Naming and Port Convention

**User Story:** As a developer, I want consistent service naming and port assignments, so that configuration is predictable and collision-free across all local development environments.

#### Acceptance Criteria

1. THE Eureka_Server SHALL run on port 8761 in local development.
2. THE Gateway SHALL run on port 8090 in local development.
3. THE Workflow_Service SHALL run on port 8080 in local development.
4. ALL Eureka_Clients SHALL register using lowercase, hyphen-separated service names (e.g. `workflow-service`, `api-gateway`).
5. THE Eureka_Server SHALL expose Actuator endpoints via `management.endpoints.web.exposure.include=health`.
6. THE Gateway SHALL expose Actuator endpoints via `management.endpoints.web.exposure.include=health`.

---

### Requirement 6: Independent Deployability and Resilience

**User Story:** As a developer, I want each Phase 2 service to be independently buildable and runnable, so that I can start services in any order during development without hard failures.

#### Acceptance Criteria

1. IF the Eureka_Server is not running when the Gateway starts, THEN THE Gateway SHALL start successfully and log a warning rather than throwing a startup exception.
2. IF the Eureka_Server is not running when the Workflow_Service starts, THEN THE Workflow_Service SHALL start successfully and log a warning rather than throwing a startup exception.
3. WHEN the Eureka_Server becomes available after a delayed start, THE Gateway SHALL automatically re-establish its Eureka_Client connection without requiring a restart.
4. WHEN the Eureka_Server becomes available after a delayed start, THE Workflow_Service SHALL automatically complete its Eureka registration without requiring a restart.
