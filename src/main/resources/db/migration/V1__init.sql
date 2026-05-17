-- V1__init.sql — mall: users, addresses, user_id_mapping

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient       VARCHAR(64)  NOT NULL,
    phone           VARCHAR(32)  NOT NULL,
    province        VARCHAR(64)  NOT NULL,
    city            VARCHAR(64)  NOT NULL,
    district        VARCHAR(64)  NOT NULL,
    detail          VARCHAR(255) NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_user ON addresses (user_id);

-- Only one default address per user
CREATE UNIQUE INDEX uq_addresses_default_per_user
    ON addresses (user_id) WHERE is_default;

CREATE TABLE user_id_mapping (
    mall_user_id        BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    askflow_user_id     UUID        NOT NULL UNIQUE,
    synced_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
