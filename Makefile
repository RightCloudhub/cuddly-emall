# Mall — top-level dev targets.
#
# `make verify`          runs the full Maven verify (compile + tests + spotless) and the
# JavaScript builds (admin UI, chat widget) plus a Playwright dry-run.
# `make verify-backend`  only the Java suite.
# `make verify-frontend` only the JS/TS pieces.
# `make up` / `make down` start/stop the docker-compose stack.

SHELL := /usr/bin/env bash

.PHONY: verify verify-backend verify-frontend up down logs admin widget e2e clean

verify: verify-backend verify-frontend

verify-backend:
	./mvnw -q verify

verify-frontend: admin widget
	# E2E suite is type-checked but not executed here (requires a live stack).
	cd mall-e2e && (test -d node_modules || npm install) && npx tsc -p tsconfig.json --noEmit

admin:
	cd mall-admin && (test -d node_modules || npm install) && npm run build

widget:
	cd mall-chat-widget && (test -d node_modules || npm install) && npm run build

e2e:
	cd mall-e2e && (test -d node_modules || npm install) && npx playwright install --with-deps chromium && npm test

up:
	docker compose -f docker-compose.mall.yml up -d

down:
	docker compose -f docker-compose.mall.yml down

logs:
	docker compose -f docker-compose.mall.yml logs -f mall

clean:
	./mvnw -q clean
	rm -rf mall-admin/dist mall-admin/node_modules
	rm -rf mall-chat-widget/dist mall-chat-widget/node_modules
	rm -rf mall-e2e/node_modules mall-e2e/test-results mall-e2e/playwright-report
