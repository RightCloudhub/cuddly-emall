-- V3__orders.sql — orders, payments, shipments, promotions.
--
-- Order numbers are produced by the application as MO + 12-digit zero-padded
-- value drawn from order_no_seq. The CHECK constraint is a safety net that
-- matches the regex AskFlow's `search_order` tool expects.

CREATE SEQUENCE order_no_seq START 1 INCREMENT 1 NO CYCLE;
CREATE SEQUENCE shipment_tracking_seq START 1000000001 INCREMENT 1 NO CYCLE;

CREATE TABLE coupons (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32)   NOT NULL UNIQUE,
    type            VARCHAR(16)   NOT NULL,
    value           NUMERIC(18,4) NOT NULL CHECK (value > 0),
    min_total       NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (min_total >= 0),
    expires_at      TIMESTAMPTZ,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT coupons_type_chk CHECK (type IN ('FLAT', 'PERCENT'))
);

CREATE TABLE orders (
    id                          BIGSERIAL PRIMARY KEY,
    order_no                    VARCHAR(16)   NOT NULL UNIQUE,
    user_id                     BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status                      VARCHAR(24)   NOT NULL DEFAULT 'PENDING_PAYMENT',
    subtotal                    NUMERIC(18,4) NOT NULL CHECK (subtotal >= 0),
    discount                    NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    total                       NUMERIC(18,4) NOT NULL CHECK (total >= 0),
    currency                    VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    coupon_id                   BIGINT REFERENCES coupons(id) ON DELETE SET NULL,
    shipping_address_snapshot   JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    paid_at                     TIMESTAMPTZ,
    shipped_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT orders_status_chk CHECK (status IN (
        'PENDING_PAYMENT','PAID','SHIPPED','COMPLETED','CANCELLED','REFUNDED')),
    -- Postgres advanced regex supports \d; keep an explicit char class for portability.
    CONSTRAINT orders_order_no_chk CHECK (order_no ~ '^MO[0-9]{12}$')
);
CREATE INDEX idx_orders_user        ON orders (user_id);
CREATE INDEX idx_orders_status      ON orders (status);
CREATE INDEX idx_orders_created_at  ON orders (created_at);

CREATE TABLE order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku_id          BIGINT        NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    sku_code        VARCHAR(64)   NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    unit_price      NUMERIC(18,4) NOT NULL CHECK (unit_price >= 0),
    qty             INT           NOT NULL CHECK (qty > 0),
    line_total      NUMERIC(18,4) NOT NULL CHECK (line_total >= 0)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE payment_intents (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    gateway             VARCHAR(32)   NOT NULL,
    status              VARCHAR(24)   NOT NULL DEFAULT 'REQUIRES_ACTION',
    gateway_intent_id   VARCHAR(128)  UNIQUE,
    amount              NUMERIC(18,4) NOT NULL CHECK (amount >= 0),
    currency            VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT payment_intent_status_chk CHECK (status IN (
        'REQUIRES_ACTION','PROCESSING','SUCCEEDED','FAILED','CANCELLED'))
);
CREATE INDEX idx_payment_intents_order ON payment_intents (order_id);

CREATE TABLE payment_events (
    id                  BIGSERIAL PRIMARY KEY,
    intent_id           BIGINT        NOT NULL REFERENCES payment_intents(id) ON DELETE CASCADE,
    event_type          VARCHAR(64)   NOT NULL,
    gateway_event_id    VARCHAR(128)  NOT NULL UNIQUE,
    raw                 JSONB         NOT NULL DEFAULT '{}'::jsonb,
    received_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_events_intent ON payment_events (intent_id);

CREATE TABLE shipments (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    tracking_no         VARCHAR(64)   NOT NULL UNIQUE,
    carrier             VARCHAR(32)   NOT NULL DEFAULT 'SF',
    status              VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    estimated_delivery  VARCHAR(64)   NOT NULL DEFAULT '2-3 business days',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    shipped_at          TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    CONSTRAINT shipment_status_chk CHECK (status IN (
        'PENDING','SHIPPED','DELIVERED','CANCELLED'))
);
CREATE INDEX idx_shipments_order ON shipments (order_id);

CREATE TABLE user_coupons (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    coupon_id       BIGINT        NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    used_at         TIMESTAMPTZ,
    order_id        BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    issued_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (user_id, coupon_id)
);
CREATE INDEX idx_user_coupons_user ON user_coupons (user_id);
