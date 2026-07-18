# Project Handoff

## Current handoff

### Date

2026-07-18 21:01 JST (Asia/Tokyo)

### Current status

- Formal development phases 0 through 14B are complete.
- Phase 14B implements the React / MUI business application, in-memory Basic authentication, role guards, expense/review/audit-log workflows, responsive layout, frontend tests, and Playwright E2E.
- Local Docker Compose now starts PostgreSQL and Spring Boot automatically with health checks while retaining `java-dev` as an on-demand tools profile.
- Logout now requires confirmation; cancel preserves the current session and route, while confirm clears authentication and returns to `/login`.
- The closure commit includes the implementation, documentation alignment, and final verification described below. It has not been pushed, and remote CI remains intentionally absent.

### Completed

- Added MUI theme, responsive application layout, common page/status/error/loading/empty/confirmation components, lazy business routes, and role-aware navigation.
- Added Login and memory-only HTTP Basic credential handling. Password and Authorization values are not stored in browser storage, cookies, URLs, logs, or analytics.
- Added expense list/detail/create/edit/delete/submit, review list/detail/approve/return, and ADMIN audit-log pages with server pagination, URL-synchronized search, validation, action permissions, and success/error feedback.
- Added Vitest / Testing Library / MSW coverage, including the complete role/status/ownership action matrix and logout confirmation behavior.
- Added Playwright Chromium workflow for USER create/edit/submit, APPROVER approve/return, USER return-reason verification, ADMIN audit-log search, and confirmed logout between roles.
- Added automatic `backend` Compose service startup after PostgreSQL health, backend `/actuator/health`, and a separate `tools` profile for `java-dev`.
- Updated README, requirements, basic design, unit test specification, evidence, phase plan, frontend/UI design, and Phase 0 local environment documentation to match the implemented state.

### In progress / uncommitted changes

- None after the closure commit.
- No push, PR, AWS resource change, or remote CI run was performed.

### Verification

- Backend: full forced regression passed; 42 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESSFUL` in 51 seconds.
- Frontend: `pnpm lint` and `pnpm typecheck` passed.
- Frontend: Vitest / Testing Library / MSW passed; 3 files, 35 tests, 0 failures.
- Frontend: Vite production build passed; 1069 modules transformed.
- Playwright: Chromium real-DB workflow passed; 1 scenario, test 24.5 seconds / run 29.3 seconds.
- Compose: `docker compose up -d --build --wait` started PostgreSQL then Spring Boot; both became healthy and `/actuator/health` returned `UP`.
- `git diff --check` passed.
- E2E data was reviewed: `E2E%` applications increased from 12 to 14 and related audit logs from 41 to 48; the latest pair ended in `APPROVED` and `RETURNED`.
- The first backend regression attempt hit a Gradle cache lock because `backend` was still running. After stopping it, the same command passed; no backend test failed.

### Next steps

1. Review the local closure commit and push it only when explicitly requested.
2. Define requirements and scope before starting another phase.
3. Candidate future phases are production authentication, receipt upload/download, or AWS IaC/deployment.

### Blockers / important notes

- There is no blocker.
- HTTP Basic credentials must remain in JavaScript memory only; do not store the password or Authorization value in localStorage, sessionStorage, cookies, URLs, logs, or analytics. Reload requires login again.
- The user intentionally removed remote CI. Keep `.github/workflows/ci.yml` ignored and local-only unless explicitly instructed otherwise.
- No AWS resources, S3 file API, IaC, deployment pipeline, JWT, or OIDC were implemented; these remain explicitly out of the completed Phase 0–14B scope.
- The persistent local PostgreSQL volume contains 14 E2E applications and 48 audit logs associated with `E2E%` applications. Do not delete them without explicit approval.

### Resume commands

```bash
git status --short --branch
git log -1 --oneline
docker compose up -d --build --wait
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm e2e
cd ..
docker compose stop backend
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon --console=plain test --rerun-tasks
docker compose stop
```

## History

- 2026-07-18 21:01 JST: Completed Phase 14B and project closure locally with the full React/MUI application, logout confirmation, automatic Compose backend startup, 42 passing backend tests, 35 passing frontend tests, a passing real-DB Playwright workflow, aligned documentation, and a local commit. No push or remote CI/AWS action was performed.
- 2026-07-16 16:08 JST: Saved the end-of-day handoff after completing uncommitted Phase 14A with MUI UI design/wireframes, Review API, integer/maximum amount validation, 42 passing backend tests, and a verified React/MUI frontend foundation. Phase 14B shared infrastructure, pages, and frontend tests are next.
- 2026-07-15 15:07 JST: Restored the CI workflow as an ignored local-only file and completed the uncommitted Phase 13 AWS architecture design. Phase 14 React frontend design is next; no AWS resources, commit, or push were created.
- 2026-07-14 16:47 JST: Completed and verified the production Dockerfile and Phase 12 documentation. The pushed CI workflow was later ignored and deleted; current code/documentation consistency must be resolved next.
- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
