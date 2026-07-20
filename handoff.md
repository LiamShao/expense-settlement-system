# Project Handoff

## Current handoff

### Date

2026-07-20 20:06 JST (Asia/Tokyo)

### Current status

- Formal development phases 0 through 15 are complete locally.
- Phase 15 replaces HTTP Basic with Spring Security and Spring Session JDBC, PostgreSQL-backed session state, secure cookie policy, CSRF, session rotation/revocation, account lock, and frontend reload restore/server logout.
- ADR-001 records why Spring Session JDBC was selected instead of HTTP Basic or JWT, including trade-offs and revisit conditions.
- All Phase 15 source, test, migration, OpenAPI, documentation, and ADR changes are uncommitted.
- Local `main` and `origin/main` both remain at pre-Phase-15 commit `80a1061`.
- PostgreSQL, backend, and tools containers are stopped. No push, PR, remote CI, or AWS action was performed.

### Completed

- Added Spring Session JDBC dependency and Flyway V4 tables for `spring_session` / attributes plus `failed_login_attempts`, `locked_until`, and `last_login_at`.
- Added CSRF token, login, current-user, and idempotent logout contracts; disabled HTTP Basic/form login; added 30-minute idle timeout and login-time session ID rotation.
- Added five-failure/15-minute account lock, success reset, current account validation, JSON CSRF errors, and credential erasure before session persistence.
- Set session cookie default to `HttpOnly`, `SameSite=Lax`, `Secure=true`; added `application-local.yml` as the only default `Secure=false` profile for loopback HTTP.
- Migrated frontend authentication to same-origin cookie sessions, memory-only CSRF, startup `/api/auth/me` restore, server logout, query/auth state cleanup, and CSRF retry/error handling.
- Updated backend/controller/integration tests, frontend MSW tests, and Playwright workflow; the final E2E logs out every role and verifies reload session restoration.
- Updated requirements, basic/DB/API/frontend/AWS design, OpenAPI, test specifications/evidence, phase plan, README, and document index to mark Phase 15 complete.
- Added `docs/adr/ADR-001-production-authentication.md` and `docs/adr/README.md` with ADR lifecycle rules.

### In progress / uncommitted changes

- The complete Phase 15 implementation and ADR work is uncommitted on `main`.
- New files:
  - `docs/adr/ADR-001-production-authentication.md`
  - `docs/adr/README.md`
  - `src/main/java/com/example/expense/dto/response/CsrfTokenResponse.java`
  - `src/main/java/com/example/expense/security/RestLogoutSuccessHandler.java`
  - `src/main/resources/application-local.yml`
  - `src/main/resources/db/migration/V4__add_production_authentication.sql`
  - `src/test/java/com/example/expense/service/AuthServiceTest.java`
- Modified files cover backend auth/security/user persistence, application/session configuration, backend tests, frontend auth/API/layout/tests/E2E, OpenAPI, README, Phase 15 design/test/evidence documents, and this handoff.
- No commit, push, PR, AWS resource change, or remote CI run was performed.

### Verification

- Backend final forced regression passed: 50 tests, 0 failures, 0 errors; `BUILD SUCCESSFUL` in 1 minute 2 seconds.
- Frontend `pnpm lint` and `pnpm typecheck` passed.
- Frontend Vitest / Testing Library / MSW passed: 3 files, 36 tests, 0 failures.
- Frontend Vite production build passed: 1069 modules transformed.
- Playwright Chromium real-DB workflow passed: 1 scenario, test 22.6 seconds / run 28.4 seconds; reload restore and final logout were included.
- Compose started PostgreSQL and backend healthy, Flyway V4 applied successfully, and `/actuator/health` returned `UP`.
- Local cookie header was checked with the value redacted: `HttpOnly; SameSite=Lax`; non-local default configuration is `Secure=true`.
- `git diff --check` passed.
- Phase 15 final verification added four local `E2E%` applications and fourteen related audit logs: totals increased from 14 to 18 applications and from 48 to 62 related logs. Existing data was not deleted.
- The development JDK 17.0.13 image required explicit TLS 1.2 properties for Gradle dependency resolution; compile and tests passed with that workaround.
- One intermediate backend run failed only because a newly added assertion incorrectly expected `Secure=true` while the Compose test process intentionally inherited the `local` profile. The assertion was removed, profile separation was retained, and the complete 50-test final run passed.
- ADR/index changes were made after code regression and are documentation-only; their links/files and `git diff --check` were verified. Tests were not rerun after the final ADR-only edits.
- Final Compose inspection confirmed all services are stopped.

### Next steps

1. Review the complete Phase 15 diff and ADR-001, especially cookie/session/CSRF semantics and Flyway V4.
2. Commit Phase 15 and the handoff when explicitly requested; do not push unless explicitly requested.
3. Start Phase 16 with requirements/design/test specification for receipt image/PDF upload, authorized preview/download, metadata, storage abstraction, and audit behavior.
4. Consider a new ADR in Phase 16 for local storage versus private S3 and application-proxied download versus short-lived pre-signed access.

### Blockers / important notes

- There is no blocker.
- HTTP Basic is disabled. Do not reintroduce password/Authorization persistence or store password, session ID, or CSRF token in browser storage, URLs, logs, or analytics.
- Production/default cookie configuration is `Secure=true`; only the `local` profile should default to `Secure=false`.
- Session state now depends on PostgreSQL. Preserve Flyway V4 and include session cleanup, DB load, and restore-time stale-session handling in future production operations.
- An ADMIN session from an earlier pre-final E2E and an anonymous CSRF session may remain in the local session tables until the 30-minute idle timeout/cleanup; the final E2E itself logged out all roles.
- The user intentionally removed remote CI. Keep `.github/workflows/ci.yml` ignored and local-only unless explicitly instructed otherwise.
- No AWS resources, S3 file API, IaC, deployment pipeline, JWT, OIDC, MFA, password reset, or self-registration were implemented.
- The persistent local PostgreSQL volume contains 18 `E2E%` applications and 62 related audit logs. Do not delete them without explicit approval.

### Resume commands

```bash
git status --short --branch
git log -1 --oneline
git diff --stat
git diff -- docs/adr README.md docs/00_document_index.md
git diff --check
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
  -e 'JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dhttps.protocols=TLSv1.2 -Djdk.tls.client.protocols=TLSv1.2' \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon --console=plain test --rerun-tasks
docker compose stop
```

## History

- 2026-07-20 20:06 JST: Completed uncommitted Phase 15 production authentication and ADR-001. Spring Session JDBC, PostgreSQL sessions, CSRF, secure cookie profiles, session rotation/logout, account lock, frontend reload restore, 50 backend tests, 36 frontend tests, production build, and real-DB Playwright E2E were verified; all services are stopped and Phase 16 receipt file handling is next.
- 2026-07-18 21:14 JST: Saved the final end-of-day handoff after closure commit `80a1061`. Final inspection showed local `main` and `origin/main` at the same commit even though Codex ran no push command; all services are stopped, and only this handoff refresh is uncommitted.
- 2026-07-18 21:01 JST: Completed Phase 14B and project closure locally with the full React/MUI application, logout confirmation, automatic Compose backend startup, 42 passing backend tests, 35 passing frontend tests, a passing real-DB Playwright workflow, aligned documentation, and a local commit. No push or remote CI/AWS action was performed.
- 2026-07-16 16:08 JST: Saved the end-of-day handoff after completing uncommitted Phase 14A with MUI UI design/wireframes, Review API, integer/maximum amount validation, 42 passing backend tests, and a verified React/MUI frontend foundation. Phase 14B shared infrastructure, pages, and frontend tests are next.
- 2026-07-15 15:07 JST: Restored the CI workflow as an ignored local-only file and completed the uncommitted Phase 13 AWS architecture design. Phase 14 React frontend design is next; no AWS resources, commit, or push were created.
- 2026-07-14 16:47 JST: Completed and verified the production Dockerfile and Phase 12 documentation. The pushed CI workflow was later ignored and deleted; current code/documentation consistency must be resolved next.
- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
