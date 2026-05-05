package com.aiworkflow.workflow.eureka;

import com.aiworkflow.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Requirements 4.1 and 4.6:
 * - Workflow_Service registers with service name "workflow-service"
 * - Workflow_Service exposes instanceId and port in its Eureka registration metadata
 *
 * Uses DEFINED_PORT so that server.port=8080 resolves correctly in the instanceId expression.
 * Eureka registration is pointed at a non-existent server so it fails silently (Requirement 4.5).
 * DataSource and WorkflowService are mocked to avoid needing a real database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
        // Point at a non-existent Eureka server — registration will fail silently (Req 4.5)
        "eureka.client.serviceUrl.defaultZone=http://localhost:19999/eureka/",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/dummy",
        "spring.datasource.username=dummy",
        "spring.datasource.password=dummy",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class EurekaRegistrationTest {

    // Mock the DataSource so Spring Boot doesn't try to connect to a real DB
    @MockBean
    DataSource dataSource;

    // Mock WorkflowService so WorkflowServiceImpl (which needs repositories) is never created
    @MockBean
    WorkflowService workflowService;

    @Autowired
    private EurekaInstanceConfigBean eurekaInstanceConfig;

    /**
     * Validates: Requirement 4.1
     * The Workflow_Service SHALL register itself with the Eureka_Server
     * using the service name "workflow-service".
     */
    @Test
    void eurekaAppNameIsWorkflowService() {
        assertThat(eurekaInstanceConfig.getAppname())
                .isEqualToIgnoringCase("workflow-service");
    }

    /**
     * Validates: Requirement 4.6
     * The Workflow_Service SHALL expose instance metadata including instanceId and port.
     * instanceId is configured as ${spring.application.name}:${server.port} → "workflow-service:8080"
     */
    @Test
    void eurekaInstanceIdContainsServiceNameAndPort() {
        String instanceId = eurekaInstanceConfig.getInstanceId();
        assertThat(instanceId).contains("workflow-service");
        assertThat(instanceId).contains("8080");
    }

    /**
     * Validates: Requirement 4.6
     * The Workflow_Service SHALL expose port metadata in its Eureka registration.
     */
    @Test
    void eurekaInstancePortIsConfigured() {
        assertThat(eurekaInstanceConfig.getNonSecurePort()).isEqualTo(8080);
    }
}
