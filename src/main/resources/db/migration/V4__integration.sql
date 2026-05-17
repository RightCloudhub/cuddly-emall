-- V4__integration.sql — AskFlow integration: user outbox, loyalty, ticket-callback audit.

-- Outbox for user create/update sync to AskFlow's POST /api/v1/admin/users.
-- Worker (UserSyncWorker) drains in same shape as mall_kb_outbox.
CREATE TABLE mall_user_outbox (
    id              BIGSERIAL PRIMARY KEY,
    op              VARCHAR(16)  NOT NULL,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT user_outbox_op_chk     CHECK (op IN ('create', 'update')),
    CONSTRAINT user_outbox_status_chk CHECK (status IN ('pending', 'processed', 'dead'))
);
CREATE INDEX idx_user_outbox_status_created ON mall_user_outbox (status, created_at);

-- Loyalty points exposed to AskFlow via GET /api/v1/integration/loyalty/points.
-- Modeled minimally — PRD says "先只建模" (rules can be hard-coded). Seeded
-- lazily by the loyalty endpoint when a mapping exists but the row does not.
CREATE TABLE user_loyalty (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    points          INT          NOT NULL DEFAULT 0 CHECK (points >= 0),
    tier            VARCHAR(16)  NOT NULL DEFAULT 'BRONZE',
    expiring_soon   INT          NOT NULL DEFAULT 0 CHECK (expiring_soon >= 0),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT user_loyalty_tier_chk CHECK (tier IN ('BRONZE','SILVER','GOLD','PLATINUM'))
);

-- Idempotency audit for inbound ticket callbacks (AskFlow → mall).
-- AskFlow can retry; we record the first (ticket_id, status) we observe and
-- short-circuit subsequent identical deliveries.
CREATE TABLE mall_ticket_callbacks (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    askflow_user_id UUID,
    mall_user_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    title           VARCHAR(255),
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (ticket_id, status)
);
CREATE INDEX idx_ticket_callbacks_mall_user ON mall_ticket_callbacks (mall_user_id);

-- Index added late so KB worker scans are cheap once volume grows. The
-- (status, created_at) index was created in V2 — we reuse it.
