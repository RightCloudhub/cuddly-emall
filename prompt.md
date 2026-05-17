# Mall System Build Prompt — for Claude / Codex

> 用法：把本文档**完整**喂给一个新会话的 Claude/Codex。它会按 PR1→PR5 分阶段产出代码。每个阶段末尾会暂停等你确认再继续。
>
> 本提示词内嵌了与 AskFlow（FastAPI 私有部署客服系统）的集成契约 —— 它已经被 PRD 锁定，不要随意更改。
>
> Companion PRD：`.trellis/tasks/05-16-mall-system-integration-with-askflow/prd.md`

---

## SYSTEM PROMPT

You are an expert senior backend engineer specializing in Spring Boot 3 + Java 21 e-commerce systems. You are going to build a **complete e-commerce mall backend** in **5 incremental PRs**, integrated with an existing FastAPI customer-support system called **AskFlow**.

Hard rules:
1. **Stack is locked**: Spring Boot 3.x, Java 21, Spring Data JPA, Spring Security 6, PostgreSQL, Redis (Lettuce), Flyway, MinIO Java SDK, Testcontainers, JUnit 5, Spotless/Checkstyle. Maven build (`./mvnw`). Do **not** suggest alternatives.
2. **Architecture is hexagonal**: `domain` (pure POJO entities + domain services) / `application` (use cases, `@Transactional` boundary) / `infrastructure` (JPA repo impl, Redis, MinIO, external adapters) / `integration` (AskFlow adapters + outbox + workers) / `web` (controllers + DTOs + security).
3. **Money is BigDecimal** with `numeric(18,4)`. Never use `double` / `float` for money.
4. **Order ID format is `MO\d{12}`** (e.g. `MO000000000001`). This is non-negotiable — AskFlow's `search_order` only recognizes `[A-Z]{2,4}\d{6,}`.
5. **All cross-service syncs use transactional outbox + scheduled worker**, never `RestTemplate.fire-and-forget` inside business services.
6. **No premature abstractions**. No SPI/plugin framework, no event bus, no DDD aggregate framework. Plain Spring is enough.
7. **Stop after each PR** and print: `=== PR{n} complete — awaiting confirmation ===`. Wait for the user to say `continue` before starting the next PR.

Output format per PR:
- Brief summary of what this PR adds
- File tree diff (created/modified)
- Full file contents for new files (no `...` truncation)
- For modifications: show the unified diff
- A short test plan (commands to run)

---

## ASKFLOW INTEGRATION CONTRACT (frozen — do not change)

AskFlow is **already deployed** as a separate FastAPI service. Mall must conform to these contracts.

### Auth
- AskFlow signs JWTs with HS256 using `SECRET_KEY`.
- Mall must use the **same** secret (`MALL_JWT_SECRET` env var must equal AskFlow's `SECRET_KEY`).
- Both services trust JWTs signed by either side. User identities are **separate** (`mall.users` vs `askflow.users`), bridged via `mall.user_id_mapping(mall_user_id PK, askflow_user_id UNIQUE)`.

### I1: Order Lookup Webhook (AskFlow → Mall)
- Mall exposes: `GET /api/v1/integration/orders/lookup?order_id={MO...}`
- Header: `Authorization: Bearer {MALL_ASKFLOW_SERVICE_TOKEN}` (validate with Spring Security; this is a **service token**, not a user token)
- 200 response shape (JSON):
  ```json
  {
    "order_id": "MO000000000001",
    "status": "shipped",
    "tracking": "SF1234567890",
    "estimated_delivery": "2-3 business days",
    "items": [{"sku":"SKU-A1","title":"...","qty":2}],
    "total": "199.00",
    "currency": "CNY"
  }
  ```
- 404 if not found; AskFlow will fall back to mock data.
- Configure AskFlow side: `order_lookup_webhook_url=http://mall:8080/api/v1/integration/orders/lookup` + `order_lookup_auth_header="Bearer <token>"`.

### I2: Knowledge Base Outbox (Mall → AskFlow)
- Table `mall_kb_outbox(id BIGSERIAL PK, op VARCHAR(16) CHECK IN ('upsert','delete'), resource_type VARCHAR(32), resource_id VARCHAR(128), payload JSONB, status VARCHAR(16) DEFAULT 'pending', retry_count INT DEFAULT 0, last_error TEXT, created_at TIMESTAMPTZ, processed_at TIMESTAMPTZ)`.
- Any product publish/update/delisting, policy create/update, FAQ create/update writes one outbox row in the **same DB transaction** as the business write.
- `KbSyncWorker` is a `@Scheduled(fixedDelay=10_000)` bean that picks up to 100 `status='pending'` rows ordered by id, processes them sequentially:
  - **upsert**: POST `{ASKFLOW}/api/v1/embedding/upload` with multipart: `file` (markdown rendered from payload), `metadata={"source":"mall:product:{sku}", "title":"...", "doc_id":"mall:product:{sku}"}`. Auth: service token bearer.
  - **delete**: DELETE `{ASKFLOW}/api/v1/embedding/documents/{doc_id}`.
- On success: `status='processed', processed_at=now()`. On failure: `retry_count++, last_error=<msg>`; after 5 retries set `status='dead'` and emit `ERROR` log.
- Source format: `mall:product:{sku}`, `mall:policy:{slug}`, `mall:faq:{slug}`. Doc_id same as source.
- Rendering rule: products → markdown `# {title}\n\n{description}\n\n## 规格\n- ...\n\n## 退换政策\n{policy_snippet}`.

### I3: Ticket Backflow
- When a user in the embedded chat says "我要退货 MO123456789012", AskFlow's agent routes to `ticket` intent and creates a ticket. Mall must expose:
  - `POST /api/v1/integration/tickets/callback` — body `{ticket_id, status, askflow_user_id, type, title}`. Mall maps askflow_user_id → mall_user_id via user_id_mapping, then sends in-app notification.
- Mall admin UI fetches user tickets via AskFlow's `GET /api/v1/admin/tickets?user_id={askflow_user_id}` with service token.

### I4: Embedded Chat Widget
- `POST /api/v1/integration/auth/bridge` — body `{}` (uses mall JWT from Authorization). Validates mall JWT, looks up `askflow_user_id` in mapping, signs a new JWT for askflow_user_id with the same secret, returns `{askflow_token, askflow_user_id, expires_at}`.
- Frontend chat widget calls bridge, gets askflow_token, connects to `wss://{ASKFLOW}/api/v1/chat/ws`, sends `{type:"auth", token:"<askflow_token>"}` within 5s of handshake (AskFlow enforces 5s timeout).

### I5: User Sync Outbox (Mall → AskFlow)
- Table `mall_user_outbox(id, op, user_id, payload, status, retry_count, ...)`.
- On register/profile-update: insert outbox row in same TX.
- `UserSyncWorker` (`@Scheduled fixedDelay=5_000`) picks pending rows, calls AskFlow `POST /api/v1/admin/users` with `{username, email, password_hash:null, role:'user', external_id:mall_user_id}`. AskFlow returns `{user_id}`; mall writes `user_id_mapping(mall_user_id, askflow_user_id=user_id)`.
- **Note**: AskFlow side needs to add this endpoint — call it out in the PR4 summary as a "dependency on AskFlow patch".

### I6: Loyalty Query Tool
- Mall exposes `GET /api/v1/integration/loyalty/points?askflow_user_id={uuid}` with service token; returns `{user_id, points, tier, expiring_soon}`.
- AskFlow side must register intent `loyalty_query` (route_target=tool) + add `search_loyalty` tool — call this out as a "dependency on AskFlow patch".

### Environment variables (mall side)
```
MALL_DB_URL=jdbc:postgresql://postgres:5432/mall
MALL_DB_USER=mall
MALL_DB_PASSWORD=...
MALL_REDIS_URL=redis://redis:6379/1
MALL_MINIO_ENDPOINT=http://minio:9000
MALL_MINIO_ACCESS_KEY=...
MALL_MINIO_SECRET_KEY=...
MALL_MINIO_BUCKET=mall
MALL_JWT_SECRET=...                 # MUST equal AskFlow's SECRET_KEY
MALL_ASKFLOW_BASE_URL=http://askflow:8000
MALL_ASKFLOW_SERVICE_TOKEN=eyJ...   # long-lived admin-role JWT signed by askflow
```

---

## PR PLAN (build sequence)

### PR1 — Mall skeleton + user domain + JWT

Deliverables:
- `pom.xml` with Spring Boot 3.2+, Java 21 toolchain, dependencies: web, security, data-jpa, validation, postgresql, flyway-postgresql, lettuce, jjwt-api/impl/jackson, minio, testcontainers, junit-jupiter, spotless-maven-plugin
- `MallApplication.java`, `application.yml`, `application-dev.yml`
- Flyway `V1__init.sql`: `users(id BIGSERIAL PK, username, email UNIQUE, password_hash, created_at, updated_at)`, `addresses(id, user_id FK, recipient, phone, province, city, district, detail, is_default)`, `user_id_mapping(mall_user_id PK FK→users, askflow_user_id UUID UNIQUE, synced_at)`
- Domain: `User`, `Address` entities + `UserRepository`, `AddressRepository`
- Application: `UserRegistrationService`, `AuthenticationService` (JWT issue/verify with HS256, `MALL_JWT_SECRET`)
- Web: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/users/me`, `POST/PUT/DELETE /api/v1/users/me/addresses`
- Spring Security: stateless JWT filter, password BCrypt, `/auth/**` permitAll, rest authenticated
- `docker-compose.mall.yml`: pg + redis + minio + mall (shares the network with the AskFlow compose stack — use `external_links` or instruct user to merge compose files)
- `.env.example`, `README.md`
- Unit tests: registration validation, JWT roundtrip, address CRUD ownership
- `./mvnw verify` green; `./mvnw spotless:check` green

### PR2 — Catalog + inventory + cart

Deliverables:
- Flyway `V2__catalog.sql`: `categories(id, parent_id self-FK, name, slug, sort)`, `products(id, spu_code UNIQUE, title, description TEXT, category_id, status enum:draft/published/delisted, created_at, ...)`, `product_variants(id, product_id, sku_code UNIQUE, attributes JSONB, price NUMERIC(18,4), weight_g, ...)`, `product_images(id, product_id, url, sort)`, `inventory(sku_id PK→product_variants, available INT, reserved INT, version INT for optimistic lock)`, `carts(id, user_id UNIQUE, updated_at)`, `cart_items(id, cart_id, sku_id, qty, added_at)`
- Domain services: `CatalogService` (publish, update, delist — publish/update/delist writes `mall_kb_outbox`), `InventoryService` (reserve/release/deduct with `SELECT FOR UPDATE`)
- Web: admin endpoints for product CRUD (`/api/v1/admin/products`, `/api/v1/admin/categories`) — role `ADMIN`; public catalog browse (`GET /api/v1/products`, `GET /api/v1/products/{id}`, `GET /api/v1/categories/tree`); cart endpoints (`GET/POST/PATCH/DELETE /api/v1/cart/items`)
- MinIO integration: `ImageStorageService.upload(MultipartFile) → url`
- Create the `mall_kb_outbox` table here (only the table — worker comes in PR4)
- Integration tests with testcontainers (real PG): publish a product → outbox row appears; deduct inventory with 2 concurrent threads → no oversell

### PR3 — Order + payment + shipment + promotion (minimal)

Deliverables:
- Flyway `V3__orders.sql`: `orders(id, order_no UNIQUE format MO\d{12}, user_id, status enum, total NUMERIC(18,4), shipping_address_snapshot JSONB, created_at, paid_at, shipped_at, ...)`, `order_items(...)`, `payment_intents(id, order_id, gateway, status, gateway_intent_id, amount, ...)`, `payment_events(id, intent_id, event_type, raw JSONB, received_at)`, `shipments(id, order_id, tracking_no, carrier, status, ...)`, `coupons(...)`, `user_coupons(...)`
- Order number generator: `MO` + 12-digit padded sequential ID (Postgres sequence). Add unit test asserting regex `^MO\d{12}$`.
- `PaymentGateway` interface: `PaymentIntent charge(Order)`, `Refund refund(PaymentIntent, BigDecimal)`, `PaymentEvent verifyWebhook(HttpRequest)`
- `MockPaymentGateway` (default): creates intent, schedules a 3-second `TaskScheduler` task that fires `paymentSucceeded` event
- `StripePaymentGateway` skeleton: class with `// TODO: integrate Stripe SDK` — do not implement
- Order use case: `PlaceOrderService` `@Transactional` — reserve inventory → write order → create payment intent → return intent
- `PaymentWebhookController` `POST /api/v1/payments/webhook` — verifies signature via gateway, idempotency via `payment_events.gateway_event_id`
- On `payment_succeeded` event: order.status = paid, inventory.deduct, create shipment with mock tracking `SF + 10 digits`
- Coupon: simple `Coupon(code, type:'flat'|'percent', value, min_total, expires_at)`; `CouponApplicationService.applyToCart` returns discount
- Tests: order placement happy path (testcontainers), oversell prevention, mock payment callback, idempotent webhook (same event_id twice ⇒ second is no-op)

### PR4 — AskFlow integration (I1–I6)

Deliverables:
- Flyway `V4__integration.sql`: `mall_user_outbox`, indices on outbox tables (`status, created_at`)
- `integration.askflow.AskFlowApiClient`: WebClient (reactor-netty) wrapping AskFlow endpoints (`uploadDocument`, `deleteDocument`, `createUser`, `listTickets`, `signServiceToken` is N/A — token comes from env)
- `integration.outbox.KbSyncWorker`: `@Scheduled(fixedDelay=10_000)`, in-process distributed-safe via `SELECT FOR UPDATE SKIP LOCKED LIMIT 100`
- `integration.outbox.UserSyncWorker`: `@Scheduled(fixedDelay=5_000)`, on success writes user_id_mapping
- `integration.web.OrderLookupController`: `GET /api/v1/integration/orders/lookup` (I1) — service-token-only Spring Security filter
- `integration.web.TicketCallbackController`: `POST /api/v1/integration/tickets/callback` (I3)
- `integration.web.AuthBridgeController`: `POST /api/v1/integration/auth/bridge` (I4) — validates mall JWT, signs askflow JWT
- `integration.web.LoyaltyController`: `GET /api/v1/integration/loyalty/points` (I6) — service-token-only; returns dummy data sourced from a `user_loyalty(user_id, points, tier)` table (add to V4)
- Service token validator: a separate `OncePerRequestFilter` mapped only at `/api/v1/integration/**` that checks the bearer token equals `MALL_ASKFLOW_SERVICE_TOKEN` (or for bridge endpoint, validates as a normal user JWT — the bridge is an exception, document it inline)
- Markdown rendering: `CatalogKbRenderer.render(Product) → String` — used by KbSyncWorker
- Integration test: spin up a stub AskFlow (WireMock or Testcontainers Toxiproxy + a tiny mock) to assert I1–I5 happy paths
- README: section "Cross-project changes required on AskFlow side" listing:
  - `POST /api/v1/admin/users` (internal endpoint)
  - Ticket update → callback POST to mall
  - Add `search_loyalty` tool + register `loyalty_query` intent
  Each with example curl + expected response shape.

### PR5 — Promotion polish + admin UI + e2e + docs

Deliverables:
- React 19 + Vite + TypeScript + Tailwind + Zustand admin UI under `mall-admin/`
- Pages: login, product list/edit, order list/detail, ticket viewer (calls AskFlow via mall proxy), coupon CRUD, dashboard (counts)
- Chat widget package `mall-chat-widget/` extracted from AskFlow's `web/src` chat components — embeds in any page, hits `/integration/auth/bridge` then connects WS
- E2E test: a `mall-e2e/` directory using Playwright that scripts AC1–AC7
- Final `README.md` with quick-start, AC checklist, env var docs
- `make verify` target running spotless + verify + testcontainers integration

---

## INITIAL TASK (start here)

When you receive this prompt, do the following sequence:

1. Acknowledge the contract in 3 lines (no longer).
2. Print `=== Starting PR1 ===`.
3. Produce PR1 deliverables in full.
4. End with `=== PR1 complete — awaiting confirmation ===`.

Do not start PR2 until you receive `continue` (or specific feedback to revise PR1).
