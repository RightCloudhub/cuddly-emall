# Mall System Integration with AskFlow

> Status: **planning → ready to start**
> Owner: droit
> Created: 2026-05-16

## Goal

设计并交付一套**完整的电商商城后端**（Spring Boot 3 + Java 21）并与既有的 AskFlow（FastAPI + RAG + Agent + 工单，私有部署）完成端到端对接。商城用户能在商城页内直接与 AI 客服对话、被 AI 准确回答商品/政策问题、查询自己的订单状态、必要时一键转工单或人工。

最终交付：
1. `prd.md` —— 本文档（商城需求 + 集成契约 + 验收）。
2. `prompt.md` —— 可直接喂给 Claude/Codex 的多阶段"建造商城代码"提示词，集成契约已写死。

## Decisions (locked)

| 议题 | 决策 |
|---|---|
| 范围 | **完整电商后端 + 全套 AskFlow 集成**（Option C） |
| 后端栈 | **Spring Boot 3 + Java 21 + Spring Data JPA + Spring Security + PostgreSQL + Redis + Flyway** |
| 前端栈 | **React 19 + Vite + TypeScript + Tailwind + Zustand**（与 AskFlow 前端同栈，便于嵌入客服窗口） |
| 用户体系 | **独立用户库 + 双向同步**：`mall.users` 与 `askflow.users` 各自维护，通过 outbox→webhook 同步注册/资料变更；`mall.user_id_mapping(mall_user_id, askflow_user_id)` 提供 ID 映射 |
| 商户模型 | **单商家**（与 AskFlow 单租户对齐），业务表不带 `shop_id`/`tenant_id` |
| 支付 | **可插拔 `PaymentGateway` 接口 + `MockPaymentGateway` 默认 + `StripePaymentGateway` 骨架（TODO）**。订单状态机：`created → pending_payment → paid → shipped → completed`，叉路 `cancelled / refunded` |
| 知识库同步 | **事务 outbox + worker**：商品/政策/FAQ 写操作落 `mall_kb_outbox`，Spring `@Scheduled` worker 调 AskFlow `POST /api/v1/embedding/upload` 与 `DELETE /api/v1/embedding/documents/{id}`；source 形如 `mall:product:{sku}` |
| Prompt 用途 | 给 Claude/Codex 用作"建造商城代码"的**多阶段提示词**；按 PR1/PR2/PR3/PR4 分段，每段可独立喂入 |

## Requirements

### M1 商城自身领域（必须）

- **用户**：注册/登录（邮箱+密码 → JWT，secret 与 AskFlow 共用）；收货地址 CRUD；会员等级/积分（先只建模，规则可硬编码）
- **商品 catalog**：`Product`（spu）+ `ProductVariant`（sku，含价、库存、属性 JSONB）；分类树（自引用）；商品图片（MinIO 复用 AskFlow 的 bucket，但前缀 `mall/`）
- **库存**：`Inventory(sku_id, available, reserved)`；下单时 `reserved += qty`、付款 `available -= qty, reserved -= qty`、取消 `reserved -= qty`；用 PG 行锁，不引入额外组件
- **购物车**：`Cart(user_id)` + `CartItem`；登录用户持久化，游客用前端 localStorage（MVP 不做合并）
- **订单**：`Order` + `OrderItem`；下单事务：扣减库存 reserved → 写 order → 写 payment_intent；订单号格式 `MO\d{12}`（**匹配 AskFlow 正则 `[A-Z]{2,4}\d{6,}`**，prefix `MO` = Mall Order）
- **支付**：`PaymentIntent` + `PaymentEvent`（事件溯源）；`PaymentGateway` 接口（`charge / refund / verifyWebhook`）；MVP 用 Mock（3s 后异步回调 `payment_succeeded`）
- **物流**：`Shipment(order_id, tracking_no, carrier, status)`；tracking 用 mock，状态机 `pending → shipped → delivered`
- **促销**（最小集）：满减 + 优惠券（`Coupon` + `UserCoupon`），结算时按规则扣减；只做这两类，复杂规则引擎不做

### M2 AskFlow 集成（必须）

- **I1 订单查询 webhook**  
  商城暴露 `GET /api/v1/integration/orders/lookup?order_id={id}`，Bearer 鉴权（与 AskFlow 共用 SECRET_KEY 签发的 service token，或独立的 `INTEGRATION_TOKEN`）。  
  响应：`{order_id, status, tracking, estimated_delivery, items:[{sku,title,qty}], total}`。  
  AskFlow `order_lookup_webhook_url` 指向此地址。

- **I2 知识库同步 outbox**  
  - 表 `mall_kb_outbox(id, op:'upsert'|'delete', resource_type, resource_id, payload jsonb, status, retry_count, created_at, processed_at)`。  
  - 写操作（商品 publish/update/delisting、政策发布、FAQ 编辑）在同事务内写一条 outbox。  
  - `KbSyncWorker` `@Scheduled(fixedDelay=10s)` 拉 100 条 `status='pending'` → 调 AskFlow API → 成功改 `processed`，失败 `retry_count++`，超 5 次进死信日志告警。  
  - 同步内容：商品（title/描述/规格/价/政策）拼成 markdown 上传，source=`mall:product:{sku}`；政策/FAQ 直接上传 markdown，source=`mall:policy:{slug}` / `mall:faq:{slug}`。

- **I3 工单回流**  
  - 用户在商城客服窗口说"我要退货 MO123456789012" → AskFlow 路由 `ticket` 建工单（user_id 通过 user_id_mapping 换成 askflow user_id）。  
  - 商城后台 `/admin/tickets` 通过调 AskFlow `GET /api/v1/admin/tickets?user_id=...` 拉自己用户的工单列表（service token 鉴权）。  
  - 工单状态变更（resolved）AskFlow 通过 webhook 反推商城 `POST /api/v1/integration/tickets/callback`，商城据此推送站内信。

- **I4 客服聊天嵌入**  
  - 商城前端 npm 安装一个轻量 `@mall/chat-widget`（基于 AskFlow `web/src` 现有 chat 组件抽取）。  
  - widget 接收当前 mall JWT → 用 user_id_mapping 换 askflow JWT（商城后端提供 `POST /api/v1/integration/auth/bridge` 端点）→ 用 askflow JWT 连 `/api/v1/chat/ws`，5s 内发 auth 帧。

- **I5 用户同步 outbox**  
  - 注册/资料变更走 `mall_user_outbox` → AskFlow `POST /api/v1/admin/users`（需要 AskFlow 扩一个内部端点，**记入跨项目改动清单**）。  
  - 同步成功后写入 `user_id_mapping`。

- **I6 新意图（"积分查询"作为示例）**  
  - 商城暴露 `GET /api/v1/integration/loyalty/points?user_id=...`。  
  - 通过 AskFlow admin API 注册 intent `loyalty_query` → route_target=`tool`；AskFlow 这边需要新增 `search_loyalty` 工具（**记入跨项目改动清单**）。

### Cross-project changes to AskFlow

PRD 中需要的 AskFlow 改动汇总（在商城实施过程中以 patch 提到 AskFlow 仓库，不打包进 prompt）：
- `POST /api/v1/admin/users`（内部端点，service token 鉴权，写入 askflow.users）
- `POST /api/v1/integration/tickets/{id}/callback-url`（工单回调地址注册）或直接在工单 update 后投递 webhook
- 新增 `search_loyalty` 工具 + intent 注册指引

## Acceptance Criteria

- [ ] **AC1**：商城注册一个新用户 →（≤30s）AskFlow 也能看到该用户 →（≤30s）`user_id_mapping` 落表
- [ ] **AC2**：商城下单 `MO000000000001` → 立即在 AskFlow 客服里问"我的订单 MO000000000001"，能返回真实订单状态（不是 mock fallback）
- [ ] **AC3**：商城上架一款商品 →（≤60s）AskFlow 客服里问该商品任意属性 → RAG 能召回相应 chunk（source=`mall:product:{sku}`）
- [ ] **AC4**：下架商品 →（≤60s）AskFlow RAG 不再召回（chunk 已删）
- [ ] **AC5**：用户在商城聊天框说"我要退货 MO000000000001" → AskFlow 建工单 → 商城后台 `/admin/tickets` 能看到，user_id 是商城用户
- [ ] **AC6**：商城页面挂 chat widget，用户已登录 mall → 无二次登录直接对话
- [ ] **AC7**：用户问"我有多少积分" → AskFlow 路由 `loyalty_query` → 调 mall → 返回真实积分
- [ ] **AC8**：商城后端单测覆盖率 ≥70%（订单/支付/库存核心服务 ≥85%）；集成测试覆盖 I1–I6 全 6 个对接面
- [ ] **AC9**：`docker compose up` 一键起完整栈（mall + askflow + 共享 PG/Redis/Chroma/MinIO）

## Definition of Done

- Spring Boot 项目结构清晰（domain / web / integration / infrastructure 分层）
- Flyway 全部 migration、`docker-compose.mall.yml`、README、`.env.example` 齐全
- Lint：`./mvnw spotless:check` + `./mvnw verify` 全绿
- 集成测试 testcontainers 起真实 PG + Redis + 一个 stub AskFlow
- `prompt.md` 自洽：新会话不读其他上下文，喂入即可逐 PR 生成代码

## Technical Approach

### 项目结构（参考 hexagonal / clean architecture）

```
mall/
├── pom.xml
├── docker-compose.mall.yml
├── src/main/java/com/example/mall/
│   ├── MallApplication.java
│   ├── domain/                     # 纯领域：实体、值对象、领域服务
│   │   ├── user/  catalog/  inventory/  cart/  order/  payment/  shipment/  promotion/
│   ├── application/                # use case：应用服务、事务边界
│   ├── infrastructure/             # JPA repo、Redis、MinIO、Mock 支付、Flyway
│   │   ├── persistence/  payment/  storage/  messaging/
│   ├── integration/                # AskFlow 对接：webhook、outbox、worker、bridge
│   │   ├── askflow/  outbox/  webhook/
│   └── web/                        # REST controller、DTO、Spring Security 配置
└── src/main/resources/db/migration/  # Flyway V1__init.sql, V2__products.sql, ...
```

### 关键技术决策

- **事务 outbox**：所有跨进程同步（KB / user / 工单回流）一律走 outbox + worker；不允许业务 service 直接 fire-and-forget 调 AskFlow
- **幂等键**：订单创建头部 `Idempotency-Key`、支付 webhook 用 gateway 的 event_id
- **库存并发**：单条 SKU 用 PG `SELECT ... FOR UPDATE`，不引 Redis 锁，电商规模下够；若后期热点 SKU 再加 Redis 预扣
- **金钱**：一律 `BigDecimal` + `MathContext.DECIMAL64`，存库 `numeric(18,4)`，不用 double
- **JWT**：mall 与 askflow 共用 `SECRET_KEY`，algorithm HS256；`POST /integration/auth/bridge` 拿 mall JWT 反查 mapping 后用同 secret 签 askflow JWT
- **配置**：所有集成端点 URL / token 走 `application.yml` + `MALL_ASKFLOW_BASE_URL` / `MALL_ASKFLOW_SERVICE_TOKEN` 环境变量

## Decision (ADR-lite)

**Context**：AskFlow 已是单租户私有部署的 FastAPI 系统，要给它配一个商城后端，并保证 RAG/Agent/工单 真正服务于商城场景。

**Decision**：
- 后端用 Spring Boot 而非 FastAPI（业务方偏好成熟电商生态）
- 用户独立 + outbox 同步而非共库或 OAuth（解耦但保最终一致）
- 单商家而非 marketplace（与 AskFlow 单租户语义对齐）
- 可插拔支付 + Mock 默认（不让支付集成阻塞主流程）
- 事务 outbox 而非实时调用（保证 KB 不漂移）

**Consequences**：
- 加：双栈运维、双 JPA/SQLAlchemy schema、需要 AskFlow 扩 3 个内部端点
- 减：用户/订单/促销 不受 FastAPI 性能与生态限制；支付/KB 同步可独立演进

## Implementation Plan (small PRs)

| PR | 内容 | 验收 |
|---|---|---|
| **PR1 商城骨架 + 用户域 + JWT** | Maven 项目、Flyway、users 表、注册/登录、Spring Security JWT 配置、健康检查、docker-compose.mall.yml | 单测 ≥70%，能起服务，可注册登录 |
| **PR2 商品 + 库存 + 购物车** | catalog/inventory/cart 三个领域、CRUD API、库存行锁、MinIO 图片上传 | 上架/购物车/库存扣减 用例通过 |
| **PR3 订单 + 支付 + 物流** | order/payment/shipment + PaymentGateway 接口 + MockPaymentGateway，订单号 `MO\d{12}` | 下单→Mock 支付→发货 happy path 通过 |
| **PR4 AskFlow 集成 I1–I6** | outbox 框架 + KbSyncWorker + UserSyncWorker + 订单查询 webhook + 工单回流 webhook + chat widget 嵌入 + auth bridge | AC1–AC7 全过 |
| **PR5 促销 + 后台 + 完善** | Coupon/满减、商城后台 admin（React）、集成测试 testcontainers、文档 | AC8/AC9 |

## Out of Scope

- 真实物流 API（顺丰/中通）—— 用 mock tracking
- 移动端原生 App
- 多语言（先中文）
- 多商户 marketplace
- 真实支付通道（Stripe/微信/支付宝）—— 仅留骨架
- 复杂促销规则引擎（满 X 减 Y / 第二件半价 之外的组合）
- 多仓库/分仓发货
- 退换货完整工作流（PR4 仅做工单回流，不做实际退款流程）

## Technical Notes

集成契约引用（AskFlow 源码位置）：
- 订单 webhook 协议：`src/askflow/agent/tools.py:67-123`
- order_id 正则：`src/askflow/agent/tools.py:22` —— `\b[A-Z]{2,4}\d{6,}\b`
- 嵌入上传：`src/askflow/embedding/router.py` + `service.py`
- 工单模型：`src/askflow/models/ticket.py`
- WS 鉴权协议：`src/askflow/chat/router.py` + `chat/service.py::process_user_message`
- intent 配置 CRUD：`src/askflow/admin/service.py`
- AskFlow 配置项：`src/askflow/config.py`（`order_lookup_webhook_url` / `order_lookup_auth_header` / `secret_key` / `app_env`）

环境变量约定（mall 侧）：
- `MALL_DB_URL` / `MALL_DB_USER` / `MALL_DB_PASSWORD`
- `MALL_REDIS_URL`
- `MALL_MINIO_*`
- `MALL_JWT_SECRET`（必须与 AskFlow `SECRET_KEY` 一致）
- `MALL_ASKFLOW_BASE_URL`（默认 `http://askflow:8000`）
- `MALL_ASKFLOW_SERVICE_TOKEN`（admin/agent role 的 long-lived JWT）

## Research References

（本次 brainstorm 未启 sub-agent 研究，所有决策基于 AskFlow 源码直接观察 + 通用电商架构惯例。后续 PR 实施前如需深入某点，由 trellis-research 单点产出。）
