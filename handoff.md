# Project Handoff

## Current handoff

### Date

2026-07-23 18:13 JST (Asia/Tokyo)

### Current status

- Phase 0–15 is complete and committed. Local `main` and `origin/main` are both at `d6c80fa` (`using session JDBC`).
- Phase 16 is in progress. Phase 16A design, 16B backend foundation, 16C business service, 16D HTTP API, and 16E offline private-S3 adapter are complete; the receipt frontend is the remaining main slice.
- The complete Phase 16 source, migration, API/OpenAPI, test, frontend reconciliation, documentation, and ADR-002 work is uncommitted.
- No active Codex goal remains. No project container is running; only the unrelated `stocklens-postgres` container is running.
- No commit, push, PR, remote CI, real AWS connection, AWS resource creation, credential use, or cost-producing action was performed in this session.

### Completed

- Implemented the Phase 16 receipt contract and architecture recorded in `docs/17_receipt_file_design.md`, `docs/adr/ADR-002-receipt-storage-and-delivery.md`, and `docs/09_phase_plan.md`.
- Added Flyway V5 `receipt_files` metadata/lifecycle state, MyBatis mapping, active/stale lookup, cleanup boundaries, and expense-item ID reconciliation.
- Added file validation, server-generated storage keys, local/fail-closed storage and scanner adapters, EICAR test detection, authorization, upload/replace/delete, recovery, and audit services.
- Added the Session/CSRF multipart upload, metadata, authorized binary preview/download, delete API, stable file error codes, security/cache headers, OpenAPI v0.6 contract, controller tests, and real PostgreSQL/local-storage integration tests.
- Added AWS SDK v2 `S3ReceiptStorage` with exact-length streaming, verified Content-Type, SSE-S3, `If-None-Match: *`, generated-key validation, 404/error mapping, conditional `type=s3` configuration, and fail-fast bucket validation.
- Added mocked `S3Client` and Spring configuration contract tests. These tests require no AWS account, bucket, credential, endpoint, or network access.
- Strengthened the storage port so local and S3 adapters receive exact content length; local storage rejects length mismatch before publishing the final object.
- Updated requirements, basic/API/DB/authority/error/frontend/AWS design, unit/integration test specifications, test evidence, README, document index, OpenAPI, and ADR index through Phase 16E.

### In progress / uncommitted changes

- All Phase 16 changes are uncommitted on `main`; inspect `git status --short` before editing or committing.
- New files include the receipt design/ADR, V5 migration, receipt entity/mapper/controller/service/storage/configuration classes, and their unit/config/controller tests.
- Modified files include expense item reconciliation, cleanup/audit/error handling, application profiles, OpenAPI and related documentation, integration tests, and the frontend item-ID preservation needed before file UI integration.
- The frontend still presents the legacy receipt-object-key behavior. File picker, metadata display, preview/download, replace/delete controls, progress/error handling, component/MSW tests, and receipt browser E2E remain to be implemented.
- This updated `handoff.md` is also uncommitted. No work was committed or published.

### Verification

- Final backend forced regression on the final code passed at 18:04–18:05 JST: 113 tests, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESSFUL`.
- Phase 16D frontend regression passed at 17:36 JST: ESLint, TypeScript typecheck, 36 Vitest tests, and Vite production build. Phase 16E changed no frontend source after that run.
- `git diff --check` passed after the final code/document updates.
- The S3 tests used Mockito only and verified put/get/head/delete requests, exact length, Content-Type, SSE-S3, overwrite prevention, key/input rejection, error privacy, and conditional configuration.
- One preliminary full-test command omitted the Docker socket and failed only during Testcontainers environment discovery. The documented socket-mounted command was then run twice successfully, including the final 113-test run.
- Receipt frontend/component/E2E, real S3 IAM/bucket/network behavior, production malware scanner, JPEG/PNG HTTP matrix, and concurrent replace/DB-failure HTTP cases have not been verified.
- Testcontainers resources were cleaned up, the project PostgreSQL container was stopped, and the persistent local PostgreSQL business data was not modified.
- Detailed evidence and exact timestamps are in `docs/08_test_evidence.md`, Sections 13–14.

### Next steps

1. Review `git status`, `docs/09_phase_plan.md` Section 6, and `docs/17_receipt_file_design.md` frontend/test sections; preserve all current uncommitted Phase 16 work.
2. Implement the receipt frontend API/types and file UI: file selection/client validation, upload/replace/delete, metadata, image/PDF preview, download, object-URL cleanup, authorization/status behavior, and stable error presentation.
3. Add MSW/component tests for file selection, validation, metadata, preview URL cleanup, replace/delete confirmation, and backend error codes.
4. Add or extend the real PostgreSQL/local-storage Playwright workflow from USER upload through APPROVER preview/download and decision.
5. Run frontend lint/typecheck/tests/build/E2E and the complete 113+ backend suite; update `docs/08_test_evidence.md` and Phase 16 status.
6. Review and commit the complete Phase 16 work only when explicitly requested; do not push unless explicitly requested.

### Blockers / important notes

- There is no blocker.
- A real S3 service is not required for current development. Keep default storage fail-closed, use local filesystem/EICAR-test adapters for local UI/E2E, and use mocked `S3Client` for adapter contracts.
- Do not create or connect to AWS resources without explicit approval. Block Public Access, Bucket owner enforced, versioning, HTTPS-only policy, IAM task role, networking, and real endpoint behavior belong to Phase 17/18 gates.
- Do not expose storage keys, bucket names, paths, credentials, or persistent S3 URLs to the browser. Continue using the authorized backend proxy endpoints.
- Production malware scanning remains unresolved; the default scanner intentionally fails closed.
- Preserve Flyway V5 and the `UPLOADING` / `PENDING_SCAN` / `ACTIVE` / `REJECTED` / `PENDING_DELETE` recovery model.
- Do not reintroduce client-writable `receiptObjectKey`; existing item IDs must remain stable during expense edits.
- HTTP Basic remains disabled. Do not store password, session ID, or CSRF token in browser storage, URLs, logs, or analytics.
- The user intentionally removed remote CI. Keep `.github/workflows/ci.yml` ignored and local-only unless explicitly instructed otherwise.
- The worktree is intentionally dirty with the whole Phase 16 implementation. Do not discard or overwrite unrelated/user changes.

### Resume commands

```bash
git status --short --branch
git log -1 --oneline
git diff --stat
sed -n '95,205p' docs/09_phase_plan.md
sed -n '210,270p' docs/17_receipt_file_design.md
git diff --check
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
cd ..
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon --console=plain test --rerun-tasks
docker compose up -d --build --wait backend
cd frontend
pnpm e2e
cd ..
docker compose stop backend postgres
```

## History

- 2026-07-23 18:13 JST: Completed uncommitted Phase 16A–16E through the private S3 adapter and offline SDK contract tests. Final backend regression passed 113 tests, frontend regression remained green at 36 tests/build, no real AWS was used, project containers are stopped, and receipt frontend UI/E2E is next.
- 2026-07-20 20:06 JST: Completed uncommitted Phase 15 production authentication and ADR-001. Spring Session JDBC, PostgreSQL sessions, CSRF, secure cookie profiles, session rotation/logout, account lock, frontend reload restore, 50 backend tests, 36 frontend tests, production build, and real-DB Playwright E2E were verified; all services are stopped and Phase 16 receipt file handling is next.
- 2026-07-18 21:14 JST: Saved the final end-of-day handoff after closure commit `80a1061`. Final inspection showed local `main` and `origin/main` at the same commit even though Codex ran no push command; all services are stopped, and only this handoff refresh is uncommitted.
- 2026-07-18 21:01 JST: Completed Phase 14B and project closure locally with the full React/MUI application, logout confirmation, automatic Compose backend startup, 42 passing backend tests, 35 passing frontend tests, a passing real-DB Playwright workflow, aligned documentation, and a local commit. No push or remote CI/AWS action was performed.
- 2026-07-16 16:08 JST: Saved the end-of-day handoff after completing uncommitted Phase 14A with MUI UI design/wireframes, Review API, integer/maximum amount validation, 42 passing backend tests, and a verified React/MUI frontend foundation. Phase 14B shared infrastructure, pages, and frontend tests are next.
- 2026-07-15 15:07 JST: Restored the CI workflow as an ignored local-only file and completed the uncommitted Phase 13 AWS architecture design. Phase 14 React frontend design is next; no AWS resources, commit, or push were created.
- 2026-07-14 16:47 JST: Completed and verified the production Dockerfile and Phase 12 documentation. The pushed CI workflow was later ignored and deleted; current code/documentation consistency must be resolved next.
- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
