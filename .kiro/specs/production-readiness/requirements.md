# Requirements Document

## Introduction

Phase 7 "Production Readiness" prepares the ai-workflow-microservices platform for local containerized deployment. This phase adds Dockerfiles for every service, updates Docker Compose for build-from-source with PostgreSQL databases, introduces structured JSON logging via Logback across all services, and replaces the empty README with comprehensive project documentation including architecture diagrams, API reference, and quick-start guides. No new business features are introduced — this is purely operational and documentation work.

## Glossary

- **Dockerfile**: A text file containing instructions to build a Docker container image for a service.
- **Multi_Stage_Build**: A Docker build pattern that uses separate stages for compilation and runtime, producing smaller final images.
- **Docker_Compose**: A tool for defining and running multi-container Docker applications using a YAML configuration file.
- **Logback**: The default logging framework for Spring Boot applications.
- **LogstashEncoder**: A Logback encoder from the `logstash-logback-encoder` library that outputs log events as structured JSON.
- **Structured_Logging**: A logging approach where log output is formatted as machine-parseable JSON rather than plain text.
- **Service**: One of the six Spring Boot applications in the platform (workflow-service, user-service, ai-agent-service, notification-service, eureka-server, api-gateway).
- **README**: The root-level `README.md` file that documents the project for developers.
- **Mermaid_Diagram**: A text-based diagramming syntax that renders as visual diagrams in Markdown viewers.
- **Health_Check**: An HTTP endpoint (`/actuator/health`) that reports whether a service is ready to accept traffic.

## Requirements

### Requirement 1: Dockerfile Creation

**User Story:** As a developer, I want each service to have a multi-stage Dockerfile, so that I can build and run any service as a Docker container without pre-installing Maven locally.

#### Acceptance Criteria

1. THE Build_System SHALL provide a Dockerfile in each of the six service directories (workflow-service, user-service, ai-agent-service, notification-service, eureka-server, api-gateway).
2. WHEN a Dockerfile is used to build a service image, THE Multi_Stage_Build SHALL use a Maven build stage with a JDK 17 base image to compile the application and produce a JAR file.
3. WHEN a Dockerfile is used to build a service image, THE Multi_Stage_Build SHALL use an Eclipse Temurin JRE 17 base image for the runtime stage that copies only the built JAR.
4. WHEN a service container starts, THE Service SHALL run as a non-root user inside the container.
5. WHEN a service container starts, THE Service SHALL expose the correct port matching the service's configured server port.
6. WHEN a Dockerfile build stage compiles the source, THE Multi_Stage_Build SHALL skip tests during the Maven build to keep image builds fast.
7. WHEN a Dockerfile build stage runs, THE Multi_Stage_Build SHALL copy the `pom.xml` and download dependencies before copying source code, to leverage Docker layer caching for faster rebuilds.
8. THE Multi_Stage_Build SHALL produce a final image containing only the JRE and the built JAR, with no Maven tooling, source code, or build artifacts.

### Requirement 2: Docker Compose Update

**User Story:** As a developer, I want Docker Compose to build services from source and provide all infrastructure, so that I can start the entire platform with a single command.

#### Acceptance Criteria

1. WHEN Docker Compose is invoked, THE Docker_Compose SHALL build each service image from its Dockerfile using the `build` context instead of referencing pre-built images.
2. WHEN Docker Compose is invoked, THE Docker_Compose SHALL start a PostgreSQL container for workflow-service with a database named `workflowdb`.
3. WHEN Docker Compose is invoked, THE Docker_Compose SHALL start a PostgreSQL container for user-service with a database named `userdb`.
4. WHEN Docker Compose is invoked, THE Docker_Compose SHALL configure each service with environment variables that point to the correct container hostnames for PostgreSQL, Kafka, and Eureka.
5. WHEN Docker Compose is invoked, THE Docker_Compose SHALL define service dependencies using `depends_on` with health check conditions so that infrastructure services (PostgreSQL, Kafka, Eureka) are healthy before application services start.
6. WHEN Docker Compose is invoked, THE Docker_Compose SHALL expose all service ports to the host matching their configured server ports (8080, 8081, 8082, 8084, 8761, 8090).
7. WHEN Docker Compose is invoked, THE Docker_Compose SHALL configure PostgreSQL containers with named Docker volumes to persist database data across container restarts.
8. WHEN Docker Compose is invoked, THE Docker_Compose SHALL configure `restart: unless-stopped` for all service containers.
9. WHEN Docker Compose is invoked, THE Docker_Compose SHALL define health checks for PostgreSQL (using `pg_isready`), Eureka (using HTTP health endpoint), and Kafka (using broker readiness check).
10. WHEN Docker Compose is invoked, THE Docker_Compose SHALL define a custom bridge network for inter-service communication.

### Requirement 3: Structured JSON Logging

**User Story:** As a developer, I want all services to output structured JSON logs in Docker and human-readable logs locally, so that logs are machine-parseable in containers and readable during development.

#### Acceptance Criteria

1. THE Build_System SHALL add the `logstash-logback-encoder` dependency to each service's `pom.xml`.
2. THE Logback SHALL be configured via a `logback-spring.xml` file in each service's `src/main/resources` directory.
3. WHEN a service runs with the default profile, THE Logback SHALL output human-readable plain-text logs to the console.
4. WHEN a service runs with the `docker` profile, THE Logback SHALL output structured JSON logs to the console using LogstashEncoder.
5. WHEN a structured JSON log entry is produced, THE LogstashEncoder SHALL include the fields: timestamp, level, logger_name, message, service_name, and thread_name.
6. WHEN a log entry includes an exception, THE LogstashEncoder SHALL include the full stack trace in the JSON output.
7. THE Logback SHALL support configuring the root log level via an environment variable (`LOG_LEVEL`) with a default of `INFO`.

### Requirement 4: README Documentation

**User Story:** As a developer, I want a comprehensive README, so that I can understand the architecture, set up the project, and use the APIs without reading source code.

#### Acceptance Criteria

1. THE README SHALL contain an architecture overview section with a Mermaid diagram showing all services, databases, Kafka, and their connections.
2. THE README SHALL contain a service table listing each service's name, description, port, and technology stack.
3. THE README SHALL contain a prerequisites section listing Java 17, Maven 3.8+, Docker, Docker Compose, and PostgreSQL as required tools.
4. THE README SHALL contain a quick-start section with step-by-step instructions for running services locally without Docker.
5. THE README SHALL contain a quick-start section with step-by-step instructions for running the full platform via Docker Compose.
6. THE README SHALL contain an API endpoints section documenting all REST endpoints with HTTP method, path, and description for workflow-service, user-service, and ai-agent-service.
7. THE README SHALL contain an event model section documenting all Kafka topics, their producers, consumers, and event payload descriptions.
8. THE README SHALL contain an environment variables reference section listing all configurable environment variables, their default values, and descriptions.
