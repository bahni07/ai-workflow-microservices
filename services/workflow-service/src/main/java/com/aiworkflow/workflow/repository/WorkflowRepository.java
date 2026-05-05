package com.aiworkflow.workflow.repository;

import com.aiworkflow.workflow.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
}
