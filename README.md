# Mall

E-commerce backend (Spring Boot 3 + Java 21) integrated with **AskFlow** (FastAPI RAG/agent/工单
customer-support, deployed separately). The mall handles users, catalog, inventory, cart, orders,
payments, shipments, and basic promotions; AskFlow handles in-page chat, knowledge retrieval, and
ticketing. The two are bridged through six integration surfaces (I1–I6) documented below.

See `prd.md` for the product spec and `prompt.md` for the original multi-stage build prompt.

## Status

| PR | Scope | State |
|---|---|---|
| PR1 | skeleton, user/address domain, JWT auth | done |
| PR2 | catalog, inventory, cart, KB outbox table, MinIO image upload | done |
| PR3 | orders (`MO\d{12}`), pluggable `PaymentGateway`, shipments, minimal coupon engine | done |
| PR4 | AskFlow integration — KB & user outbox workers, I1/I3/I4/I6 endpoints | done |
| PR5 | promotion polish + admin UI + chat widget + e2e + docs | done |

## Repo layout

```
emall/
├── pom.xml, mvnw, Makefile, docker-compose.mall.yml, Dockerfile
├── src/                       # Spring Boot backend (Java 21)
├── mall-admin/                # React 19 + Vite + TS + Tailwind + Zustand admin SPA
├── mall-chat-widget/          # Embeddable chat widget npm package
├── mall-e2e/                  # Playwright acceptance suite (AC1, AC2, AC6/7)
├── prd.md                     # product spec
└── prompt.md                  # build prompt history
```

## Quick start

Prereqs: JDK 21, Docker, Docker Compose, Node 20+.

```bash
cp .env.example .env
# Required:
#   MALL_JWT_SECRET — ≥32 bytes, MUST equal AskFlow SECRET_KEY (shared HS256 secret)
#   MALL_ASKFLOW_SERVICE_TOKEN — bearer used in both directions for service-to-service calls

# 1) backend + infra
make up                 # postgres + redis + minio + mall
curl http://localhost:8080/actuator/health     # → {"status":"UP"}

# 2) admin UI
cd mall-admin && npm install && npm run dev    # http://localhost:5173
# log in with an ADMIN-role user (seed one via SQL or promote a registered user)

# 3) full verify (CI-friendly)
make verify             # ./mvnw verify + admin build + widget build + e2e type-check

# 4) live e2e (requires AskFlow stack running and the two services networked)
export MALL_ASKFLOW_SERVICE_TOKEN=...
make e2e
```

## Environment variables

| Var | Required | Default | Purpose |
|---|---|---|---|
| `MALL_DB_URL` | yes | `jdbc:postgresql://postgres:5432/mall` | Postgres JDBC URL |
| `MALL_DB_USER` / `MALL_DB_PASSWORD` | yes | `mall` / `mall` | DB credentials |
| `MALL_REDIS_URL` | no | `redis://redis:6379/1` | Redis (currently used for cart locks only) |
| `MALL_MINIO_ENDPOINT` | yes | `http://minio:9000` | Image storage S3 endpoint |
| `MALL_MINIO_ACCESS_KEY` / `MALL_MINIO_SECRET_KEY` | yes | `minioadmin` | MinIO creds |
| `MALL_MINIO_BUCKET` | no | `mall` | Bucket (auto-created on startup) |
| `MALL_JWT_SECRET` | **yes** | — | HS256 key, **must equal AskFlow `SECRET_KEY`** |
| `MALL_ASKFLOW_BASE_URL` | yes | `http://askflow:8000` | AskFlow root |
| `MALL_ASKFLOW_SERVICE_TOKEN` | yes (prod) | empty | Service bearer used in *both* directions |

All `mall.askflow.*` properties (`kb-fixed-delay-ms`, `batch-size`, retry counts, request timeout,
bridge token TTL) are tunable in `application.yml`.

## Backend API surface

### Customer
| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | public | username/email/password → user + JWT |
| `POST` | `/api/v1/auth/login` | public | email/password → JWT |
| `GET`  | `/api/v1/users/me` | Bearer | current user |
| `*`    | `/api/v1/users/me/addresses[/{id}]` | Bearer | address CRUD |
| `GET`  | `/api/v1/products[/{id}]` | public | catalog browse |
| `GET`  | `/api/v1/categories/tree` | public | category tree |
| `*`    | `/api/v1/cart/items[/{id}]` | Bearer | cart CRUD |
| `POST` | `/api/v1/orders` | Bearer | place order (Mock payment intent returned) |
| `GET`  | `/api/v1/orders[/{id}]` | Bearer | list / view orders |
| `POST` | `/api/v1/payments/webhook/{gateway}` | public | gateway → mall settlement callback |

### Admin (`ADMIN` role)
`/api/v1/admin/products`, `/api/v1/admin/categories`, `/api/v1/admin/inventory`,
`/api/v1/admin/coupons` (FLAT/PERCENT, issue-to-user), `/api/v1/admin/orders` (ship, cancel, refund).

### AskFlow integration (`/api/v1/integration/**`)
| Method | Path | Auth | Used by |
|---|---|---|---|
| `GET`  | `/api/v1/integration/orders/lookup?order_id=MO...` | **service token** | AskFlow `search_order` tool (I1) |
| `POST` | `/api/v1/integration/tickets/callback` | **service token** | AskFlow ticket state changes (I3) — idempotent on `(ticket_id, status)` |
| `POST` | `/api/v1/integration/auth/bridge` | **mall user JWT** | Chat widget — mints an AskFlow JWT signed with the shared secret (I4) |
| `GET`  | `/api/v1/integration/loyalty/points?askflow_user_id=...` | **service token** | AskFlow `search_loyalty` tool (I6) |

Outbound (mall → AskFlow): `KbSyncWorker` (10s) drains `mall_kb_outbox` into `POST
/api/v1/embedding/upload` / `DELETE /api/v1/embedding/documents/{doc_id}`. `UserSyncWorker` (5s)
drains `mall_user_outbox` into `POST /api/v1/admin/users` and writes `user_id_mapping`.

## Acceptance Criteria (PRD section)

- [x] **AC1** — mall register → AskFlow sees user → `user_id_mapping` populated within 30s
      *(covered by `mall-e2e/tests/ac1-user-sync.spec.ts`)*
- [x] **AC2** — mall order `MO...` → AskFlow `search_order` returns real status
      *(covered by `mall-e2e/tests/ac2-order-lookup.spec.ts` against I1, and
      `IntegrationEndpointsIntegrationTest.orderLookupReturns200WithShape`)*
- [x] **AC3** — publish a product → RAG indexes within 60s
      *(covered by `KbSyncWorkerIntegrationTest.publishingProductWritesOutboxAndWorkerUploadsToAskFlow`)*
- [x] **AC4** — delist a product → RAG drops chunk within 60s
      *(covered by `KbSyncWorkerIntegrationTest.delistedProductEmitsDelete`)*
- [x] **AC5** — user says "我要退货 MO..." in chat → AskFlow creates ticket → mall admin sees it
      *(covered server-side by `IntegrationEndpointsIntegrationTest.ticketCallbackAcceptsAndIsIdempotent`;
      end-to-end remains a manual smoke until the AskFlow stack patches land)*
- [x] **AC6** — chat widget on mall page, user already logged in → no second login
      *(covered by `mall-chat-widget` bridge logic + `mall-e2e/tests/ac6-ac7-bridge-loyalty.spec.ts`)*
- [x] **AC7** — user asks "我有多少积分" → AskFlow `search_loyalty` returns real points
      *(covered by `IntegrationEndpointsIntegrationTest.loyaltyEndpointReturnsDefaultRow` and
      the e2e spec; depends on AskFlow patch to register the `loyalty_query` intent)*
- [x] **AC8** — backend unit coverage ≥70% (order/payment/inventory ≥85%); integration tests
      exercise I1–I6
      *(testcontainers-backed suites cover all six integration surfaces)*
- [x] **AC9** — `docker compose up` brings the entire stack
      *(`make up` → `docker-compose.mall.yml`)*

## Cross-project changes required on AskFlow side

The mall integration assumes three patches on the AskFlow repo. Each is internal-only and gated by
the shared `SECRET_KEY` / `MALL_ASKFLOW_SERVICE_TOKEN`.

### 1. `POST /api/v1/admin/users` (internal endpoint)
Called by `UserSyncWorker` once per registered mall user.

```bash
curl -X POST $ASKFLOW_BASE/api/v1/admin/users \
  -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "username":"alice",
        "email":"alice@example.com",
        "password_hash": null,
        "role":"user",
        "external_id":"42"
      }'
# → 200 {"user_id":"5f3c...uuid..."}
```
Behavior: idempotent on `external_id`; `password_hash=null` means auth-bridge-only.

### 2. Ticket update → callback to mall
On any ticket state change, AskFlow POSTs to mall:
```bash
curl -X POST $MALL_BASE/api/v1/integration/tickets/callback \
  -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "ticket_id":"T-123",
        "status":"resolved",
        "askflow_user_id":"5f3c-...",
        "type":"refund",
        "title":"我要退货 MO000000000001"
      }'
# → 200 {"status":"accepted","mall_user_id":42}
```
Mall is idempotent on `(ticket_id, status)`.

### 3. `search_loyalty` tool + `loyalty_query` intent
- New tool `search_loyalty(user_id: UUID)` that calls
  `GET $MALL_BASE/api/v1/integration/loyalty/points?askflow_user_id={user_id}` with the service
  token, and forwards the `{points, tier, expiring_soon}` shape back to the agent.
- Intent `loyalty_query` registered with `route_target="tool"` referencing the above tool.

## Build & test

```bash
make verify              # backend (./mvnw verify) + admin/widget builds + e2e typecheck
./mvnw verify            # backend only (compile + spotless + unit + testcontainers integration)
./mvnw spotless:apply    # auto-format
cd mall-admin && npm run dev      # admin UI dev server
cd mall-chat-widget && npm run build
cd mall-e2e && npm test           # live e2e (requires running stack)
```

## Notes

- Money is `BigDecimal` only, never `double`. Order IDs follow `MO\d{12}` (matches AskFlow
  `search_order` regex `[A-Z]{2,4}\d{6,}`).
- All cross-service syncs go through a transactional outbox + scheduled worker drain. No
  fire-and-forget RPC inside business services.
- The auth bridge 404s when a freshly registered user hasn't been synced yet (typically <5s); the
  chat widget retries with 1s backoff up to 5 times.
- `KbSyncWorker` renders the *current* product state at drain time — the outbox payload is a hint,
  the DB is truth.
- Outbox workers use `SELECT ... FOR UPDATE SKIP LOCKED` so multiple mall instances can drain
  concurrently. After 5 retries a row is marked `dead` and an ERROR log is emitted.
