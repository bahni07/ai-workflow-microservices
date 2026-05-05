CREATE TABLE workflows (
    id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(255) NOT NULL,
    status  VARCHAR(50)  NOT NULL,
    version BIGINT       NOT NULL DEFAULT 0
);
