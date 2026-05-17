# mall-e2e

End-to-end acceptance suite using Playwright. Each spec corresponds to one acceptance criterion
from `prd.md`.

| Spec | AC | Covers |
|---|---|---|
| `ac1-user-sync.spec.ts` | AC1 | register mall user → `user_id_mapping` populated → bridge works |
| `ac2-order-lookup.spec.ts` | AC2 | place an order → AskFlow's order lookup webhook resolves it |
| `ac6-ac7-bridge-loyalty.spec.ts` | AC6/AC7 | bridge mints AskFlow JWT; loyalty endpoint returns row |

AC3/AC4 (KB upload/delete) and AC5 (ticket backflow) are covered by Java integration tests
(`KbSyncWorkerIntegrationTest`, `IntegrationEndpointsIntegrationTest`) — running them as Playwright
specs would require an AskFlow stack with embedded vector store, which is out of scope for the mall
repo's e2e suite. They re-enable as black-box specs once a co-deployed integration env exists.

## Run

```bash
# Requires the mall + askflow stack running and seeded.
export MALL_BASE_URL=http://localhost:8080
export ASKFLOW_BASE_URL=http://localhost:8000
export MALL_ASKFLOW_SERVICE_TOKEN=...   # same value as in mall .env
# Optional, used by ac2:
export MALL_ADMIN_EMAIL=...
export MALL_ADMIN_PASSWORD=...

npm install
npx playwright install --with-deps chromium
npm test
```

## Notes

- The suite relies on the `UserSyncWorker`'s 5s fixedDelay; `waitForCondition` polls up to 30s.
- AC2 depends on at least one published product in seed. The CI smoke seeds one via the admin API
  during the docker-compose bring-up; without it the spec auto-skips.
