package com.aiworkflow.workflow.repository;

import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.enums.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StepRepository extends JpaRepository<Step, UUID> {
    List<Step> findByWorkflowId(UUID workflowId);
    long countByWorkflowIdAndStatusNot(UUID workflowId, StepStatus status);
}
