package com.aiworkflow.workflow.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void workflowNotFoundException_returns404() {
        WorkflowNotFoundException ex = new WorkflowNotFoundException(UUID.randomUUID());

        ResponseEntity<Map<String, Object>> response = handler.handleWorkflowNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Workflow not found");
    }

    @Test
    void methodArgumentNotValidException_returns400WithDetails() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "name", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Validation failed");
        assertThat(response.getBody()).containsKey("details");
        @SuppressWarnings("unchecked")
        List<String> details = (List<String>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> d.contains("name"));
    }

    @Test
    void httpMessageNotReadableException_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "bad json", new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<Map<String, Object>> response = handler.handleMalformedJson(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Malformed request body");
    }

    @Test
    void genericException_returns500() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Internal server error");
    }
}
