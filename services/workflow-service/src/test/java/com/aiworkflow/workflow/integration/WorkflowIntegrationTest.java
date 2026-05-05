package com.aiworkflow.workflow.integration;

import com.aiworkflow.workflow.dto.AddStepRequest;
import com.aiworkflow.workflow.dto.AddStepResponse;
import com.aiworkflow.workflow.dto.CreateWorkflowRequest;
import com.aiworkflow.workflow.dto.WorkflowResponse;
import com.aiworkflow.workflow.enums.StepStatus;
import com.aiworkflow.workflow.enums.WorkflowStatus;
import com.aiworkflow.workflow.repository.StepRepository;
import com.aiworkflow.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    StepRepository stepRepository;

    @Autowired
    WorkflowRepository workflowRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    TestRestTemplate restTemplate = new TestRestTemplate();

    String baseUrl() {
        return "http://localhost:" + port + "/workflows";
    }

    @BeforeEach
    void cleanUp() {
        stepRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    // ── 13.1 Full create-workflow flow ────────────────────────────────────────

    @Test
    void createWorkflow_returnsCreatedWithCorrectFields() {
        CreateWorkflowRequest request = new CreateWorkflowRequest("My Integration Workflow");

        ResponseEntity<WorkflowResponse> response =
                restTemplate.postForEntity(baseUrl(), request, WorkflowResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        WorkflowResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("My Integration Workflow");
        assertThat(body.status()).isEqualTo(WorkflowStatus.CREATED);
        assertThat(body.steps()).isEmpty();
    }

    // ── 13.2 Full add-step flow ───────────────────────────────────────────────

    @Test
    void addStep_returnsCreatedStepAndTransitionsWorkflowToInProgress() {
        // Create workflow
        CreateWorkflowRequest createReq = new CreateWorkflowRequest("Step Flow Workflow");
        WorkflowResponse created = restTemplate
                .postForEntity(baseUrl(), createReq, WorkflowResponse.class)
                .getBody();
        assertThat(created).isNotNull();
        UUID workflowId = created.id();

        // Add a step
        AddStepRequest stepReq = new AddStepRequest("First Step");
        ResponseEntity<AddStepResponse> stepResponse = restTemplate.postForEntity(
                baseUrl() + "/" + workflowId + "/steps",
                stepReq,
                AddStepResponse.class);

        assertThat(stepResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AddStepResponse stepBody = stepResponse.getBody();
        assertThat(stepBody).isNotNull();
        assertThat(stepBody.step()).isNotNull();
        assertThat(stepBody.step().status()).isEqualTo(StepStatus.PENDING);
        assertThat(stepBody.suggestedNextStep()).isNotBlank();

        // GET workflow — should be IN_PROGRESS with 1 step
        ResponseEntity<WorkflowResponse> getResponse = restTemplate.getForEntity(
                baseUrl() + "/" + workflowId, WorkflowResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WorkflowResponse workflow = getResponse.getBody();
        assertThat(workflow).isNotNull();
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.IN_PROGRESS);
        assertThat(workflow.steps()).hasSize(1);
    }

    // ── 13.3 Full complete-step flow ──────────────────────────────────────────

    @Test
    void completeStep_workflowRemainsInProgressUntilAllStepsDone() {
        // Create workflow
        UUID workflowId = restTemplate
                .postForEntity(baseUrl(), new CreateWorkflowRequest("Complete Flow"), WorkflowResponse.class)
                .getBody().id();

        // Add 2 steps
        UUID step1Id = restTemplate
                .postForEntity(baseUrl() + "/" + workflowId + "/steps",
                        new AddStepRequest("Step One"), AddStepResponse.class)
                .getBody().step().id();

        UUID step2Id = restTemplate
                .postForEntity(baseUrl() + "/" + workflowId + "/steps",
                        new AddStepRequest("Step Two"), AddStepResponse.class)
                .getBody().step().id();

        // Complete first step — workflow should still be IN_PROGRESS
        restTemplate.postForEntity(
                baseUrl() + "/" + workflowId + "/steps/" + step1Id + "/complete",
                null, WorkflowResponse.class);

        WorkflowResponse afterFirst = restTemplate
                .getForEntity(baseUrl() + "/" + workflowId, WorkflowResponse.class)
                .getBody();
        assertThat(afterFirst).isNotNull();
        assertThat(afterFirst.status()).isEqualTo(WorkflowStatus.IN_PROGRESS);

        // Complete second step — workflow should now be COMPLETED
        restTemplate.postForEntity(
                baseUrl() + "/" + workflowId + "/steps/" + step2Id + "/complete",
                null, WorkflowResponse.class);

        WorkflowResponse afterSecond = restTemplate
                .getForEntity(baseUrl() + "/" + workflowId, WorkflowResponse.class)
                .getBody();
        assertThat(afterSecond).isNotNull();
        assertThat(afterSecond.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    // ── 13.4 Flyway migrations apply cleanly ──────────────────────────────────

    @Test
    void flywayMigrations_applyCleanlyOnStartup() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);

        assertThat(count).isGreaterThanOrEqualTo(2);
    }
}
