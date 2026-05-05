package com.aiworkflow.user.integration;

import com.aiworkflow.user.dto.AuthResponse;
import com.aiworkflow.user.dto.LoginRequest;
import com.aiworkflow.user.dto.RegisterRequest;
import com.aiworkflow.user.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;

    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    TestRestTemplate restTemplate = new TestRestTemplate();

    String baseUrl() { return "http://localhost:" + port + "/auth"; }

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_returnsCreatedWithToken() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "Secret1!");

        ResponseEntity<AuthResponse> response =
                restTemplate.postForEntity(baseUrl() + "/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.userId()).isNotNull();
        assertThat(body.username()).isEqualTo("alice");
        assertThat(body.token()).isNotBlank();
    }

    @Test
    void register_duplicateUsername_returns409() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "Secret1!");
        restTemplate.postForEntity(baseUrl() + "/register", request, AuthResponse.class);

        RegisterRequest duplicate = new RegisterRequest("alice", "other@example.com", "Secret1!");
        ResponseEntity<Object> response =
                restTemplate.postForEntity(baseUrl() + "/register", duplicate, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void login_validCredentials_returnsToken() {
        RegisterRequest reg = new RegisterRequest("bob", "bob@example.com", "Password9!");
        restTemplate.postForEntity(baseUrl() + "/register", reg, AuthResponse.class);

        LoginRequest login = new LoginRequest("bob", "Password9!");
        ResponseEntity<AuthResponse> response =
                restTemplate.postForEntity(baseUrl() + "/login", login, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    void login_wrongPassword_returns401() {
        RegisterRequest reg = new RegisterRequest("carol", "carol@example.com", "Password9!");
        restTemplate.postForEntity(baseUrl() + "/register", reg, AuthResponse.class);

        LoginRequest login = new LoginRequest("carol", "wrongpassword");
        ResponseEntity<Object> response =
                restTemplate.postForEntity(baseUrl() + "/login", login, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void flywayMigrations_applyCleanlyOnStartup() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
