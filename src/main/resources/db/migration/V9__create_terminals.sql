-- Purpose: machine identities for self-service terminals such as ATMs.
--
-- A terminal authenticates with its own OAuth2 client-credentials token; the
-- (issuer, subject) pair of that token identifies it, exactly as for users. A
-- terminal belongs to a branch and can be disabled, for example when it is
-- taken out of service or suspected of being tampered with.

CREATE TABLE terminals (
    id                VARCHAR(50)  PRIMARY KEY,
    identity_issuer   VARCHAR(512) NOT NULL,
    identity_subject  VARCHAR(255) NOT NULL,
    terminal_type     VARCHAR(20)  NOT NULL,
    branch_code       VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    registered_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_terminals_external_identity UNIQUE (identity_issuer, identity_subject),
    CONSTRAINT ck_terminals_type CHECK (terminal_type IN ('ATM')),
    CONSTRAINT ck_terminals_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
