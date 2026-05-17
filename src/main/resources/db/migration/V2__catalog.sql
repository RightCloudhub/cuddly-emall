-- V2__catalog.sql — catalog, inventory, cart, KB outbox skeleton.

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES categories(id) ON DELETE RESTRICT,
    name        VARCHAR(128) NOT NULL,
    slug        VARCHAR(128) NOT NULL UNIQUE,
    sort        INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_categories_parent ON categories (parent_id);

CREATE TABLE products (
    id           BIGSERIAL PRIMARY KEY,
    spu_code     VARCHAR(64)  NOT NULL UNIQUE,
    title        VARCHAR(255) NOT NULL,
    description  TEXT         NOT NULL DEFAULT '',
    category_id  BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | PUBLISHED | DELISTED
    policy_snippet TEXT       NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT products_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED', 'DELISTED'))
);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status   ON products (status);

CREATE TABLE product_variants (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku_code    VARCHAR(64)  NOT NULL UNIQUE,
    attributes  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    price       NUMERIC(18,4) NOT NULL CHECK (price >= 0),
    weight_g    INT          NOT NULL DEFAULT 0 CHECK (weight_g >= 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_variants_product ON product_variants (product_id);

CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url         VARCHAR(1024) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_images_product ON product_images (product_id);

CREATE TABLE inventory (
    sku_id      BIGINT PRIMARY KEY REFERENCES product_variants(id) ON DELETE CASCADE,
    available   INT NOT NULL DEFAULT 0 CHECK (available >= 0),
    reserved    INT NOT NULL DEFAULT 0 CHECK (reserved  >= 0),
    version     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id          BIGSERIAL PRIMARY KEY,
    cart_id     BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    sku_id      BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    qty         INT    NOT NULL CHECK (qty > 0),
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cart_id, sku_id)
);
CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);

-- KB outbox: rows written in the same TX as catalog mutations.
-- Worker (PR4) drains via SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE mall_kb_outbox (
    id              BIGSERIAL PRIMARY KEY,
    op              VARCHAR(16)  NOT NULL,
    resource_type   VARCHAR(32)  NOT NULL,
    resource_id     VARCHAR(128) NOT NULL,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT kb_outbox_op_chk     CHECK (op IN ('upsert', 'delete')),
    CONSTRAINT kb_outbox_status_chk CHECK (status IN ('pending', 'processed', 'dead'))
);
CREATE INDEX idx_kb_outbox_status_created ON mall_kb_outbox (status, created_at);
