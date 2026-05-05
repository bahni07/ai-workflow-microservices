# Implementation Plan: Production Readiness

## Overview

This plan implements Phase 7 in four workstreams: Dockerfiles, Docker Compose, structured logging, and README documentation. Each workstream builds incrementally, with testing sub-tasks validating correctness properties from the design document.

## Tasks

- [x] 1. Create Dockerfiles for all services
  - [x] 1.1 Create Dockerfile for workflow-service
    - Multi-stage build: `maven:3.9-eclipse-temurin-17` build stage, `eclipse-temurin:17-jre-alpine` runtime stage
    - Copy pom.xml first, run `mvn dependency:go-offline`, then copy src and run `mvn package -DskipTests`
    - Create non-root user `appuser`, EXPOSE 8080
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [x] 1.2 Create Dockerfile for user-service
    - Same template as workflow-service, EXPOSE 8081
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [x] 1.3 Create Dockerfile for ai-agent-service
    - Same template, EXPOSE 8082
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [x] 1.4 Create Dockerfile for notification-service
    - Same template, EXPOSE 8084
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [x] 1.5 Create Dockerfile for eureka-server
    - Same template, EXPOSE 8761
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [x] 1.6 Create Dockerfile for api-gateway
    - Same template, EXPOSE 8090
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [x] 1.7 Write property test for Dockerfile correctness
    - **Property 1: Dockerfile security and port correctness**
    - **Validates: Requirements 1.4, 1.5**

- [x] 2. Update Docker Compose configuration
  - [x] 2.1 Add PostgreSQL services to docker-compose.yml
    - Add `postgres-workflow` with database `workflowdb`, named volume `pgdata-workflow`
    - Add `postgres-user` with database `userdb`, named volume `pgdata-user`, host port 5433
    - Add health checks using `pg_isready`
    - _Requirements: 2.2, 2.3, 2.7, 2.9_
  - [x] 2.2 Add custom network and update infrastructure services
    - Define `aiworkflow-net` custom bridge network
    - Add health checks for eureka-server (HTTP) and kafka (broker API versions)
    - Add `restart: unless-stopped` to zookeeper, kafka, eureka-server
    - Attach all services to `aiworkflow-net`
    - _Requirements: 2.9, 2.10, 2.8_
  - [x] 2.3 Update application service definitions
    - Replace `image:` with `build:` context for all six services
    - Add `depends_on` with `condition: service_healthy` for each service's infrastructure dependencies
    - Set environment variables with container hostnames (e.g., `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-workflow:5432/workflowdb`)
    - Add `restart: unless-stopped` to all application services
    - Verify all ports match configured server ports
    - _Requirements: 2.1, 2.4, 2.5, 2.6, 2.8_
  - [x] 2.4 Add `.env` file for Docker Compose variables
    - Create `infra/docker/.env` with all configurable variables (DB passwords, JWT_SECRET, GROQ_API_KEY, LOG_LEVEL)
    - Reference `.env` in docker-compose.yml via `env_file` or variable substitution
    - Add `.env` to `.gitignore` and provide `.env.example` with placeholder values
    - _Requirements: 2.4_
  - [x] 2.5 Add environment variable handling for secrets
    - Ensure JWT_SECRET, DB passwords, GROQ_API_KEY are never hardcoded in docker-compose.yml
    - All secrets referenced via `${VARIABLE}` syntax with defaults only for non-sensitive values
    - Document usage in `.env.example`
    - _Requirements: 2.4_
  - [x] 2.6 Write property tests for Docker Compose configuration
    - **Property 2: Docker Compose uses build context for all services**
    - **Property 3: Docker Compose dependency wiring**
    - **Property 4: Docker Compose service runtime configuration**
    - **Validates: Requirements 2.1, 2.4, 2.5, 2.6, 2.8**

- [x] 3. Checkpoint — Validate Docker configuration correctness
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Add structured JSON logging to all services
  - [x] 4.1 Add logstash-logback-encoder dependency to all pom.xml files
    - Add `net.logstash.logback:logstash-logback-encoder:7.4` to each of the six service pom.xml files
    - _Requirements: 3.1_
  - [x] 4.2 Create logback-spring.xml for workflow-service
    - Default profile: ConsoleAppender with PatternLayout (plain text)
    - Docker profile: ConsoleAppender with LogstashEncoder (JSON)
    - Root log level from `${LOG_LEVEL:-INFO}`
    - Custom field `service_name` from `spring.application.name`
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 4.3 Create logback-spring.xml for user-service
    - Same template as workflow-service
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 4.4 Create logback-spring.xml for ai-agent-service
    - Same template as workflow-service
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 4.5 Create logback-spring.xml for notification-service
    - Same template as workflow-service
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 4.6 Create logback-spring.xml for eureka-server
    - Same template as workflow-service
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 4.7 Create logback-spring.xml for api-gateway
    - Same template as workflow-service
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 4.8 Write property tests for logging configuration
    - **Property 5: Logging dependency present in all services**
    - **Property 6: Logback configuration completeness**
    - **Property 7: Structured JSON log output contains required fields**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**
  - [x] 4.9 Add correlationId support in logging
    - Add MDC-based `correlationId` (requestId) to log context for all HTTP requests
    - Create a servlet filter that generates/extracts a correlation ID from `X-Correlation-Id` header and puts it in MDC
    - Include `correlationId` field in LogstashEncoder JSON output
    - _Requirements: 3.5_

- [x] 5. Checkpoint — Validate logging configuration correctness
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Write comprehensive README
  - [x] 6.1 Write README with architecture overview and service table
    - Architecture section with Mermaid diagram showing all services, databases, Kafka, and connections
    - Service table with name, description, port, and technology stack for all six services
    - _Requirements: 4.1, 4.2_
  - [x] 6.2 Write prerequisites and quick-start sections
    - Prerequisites: Java 17, Maven 3.8+, Docker Desktop, PostgreSQL
    - Local development quick-start: step-by-step for running without Docker
    - Docker Compose quick-start: single-command startup instructions
    - _Requirements: 4.3, 4.4, 4.5_
  - [x] 6.3 Write API endpoints and event model sections
    - API endpoints tables for workflow-service (POST /workflows, GET /workflows/{id}, POST /workflows/{id}/steps, POST /workflows/{id}/steps/{stepId}/complete), user-service (POST /auth/register, POST /auth/login), ai-agent-service (POST /suggestions)
    - Event model table: workflow.created, step.created, step.completed, ai.suggestion.generated with producers and consumers
    - _Requirements: 4.6, 4.7_
  - [x] 6.4 Write environment variables reference section
    - Table listing all configurable env vars with default values and descriptions
    - Cover: SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, KAFKA_BOOTSTRAP_SERVERS, EUREKA_CLIENT_SERVICEURL_DEFAULTZONE, JWT_SECRET, JWT_EXPIRATION_MS, GROQ_API_KEY, OPENAI_MODEL, LOG_LEVEL, SPRING_PROFILES_ACTIVE
    - _Requirements: 4.8_
  - [x] 6.5 Write property tests for README completeness
    - **Property 8: README documents all API endpoints**
    - **Property 9: README documents all Kafka topics**
    - **Validates: Requirements 4.6, 4.7**

- [x] 7. Final checkpoint — End-to-end Docker validation
  - Run `docker compose up --build` from `infra/docker/`
  - Verify all services start successfully (no crash loops)
  - Verify `/actuator/health` returns 200 for all services
  - Verify API Gateway routes work (POST workflow, POST register, POST suggestions)
  - Verify DB + Kafka connectivity (create a workflow, observe notification-service logs)
  - Verify PostgreSQL data persists across `docker compose down` / `docker compose up`
  - Verify services shut down gracefully on `docker compose down` (no forced kills)
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- All Dockerfiles follow the same template — only the EXPOSE port differs
- All logback-spring.xml files follow the same template — the service_name comes from Spring's `spring.application.name`
