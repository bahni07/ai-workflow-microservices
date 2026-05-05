CREATE TABLE steps (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID         NOT NULL REFERENCES workflows(id),
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_steps_workflow_id ON steps(workflow_id);
