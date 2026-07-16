# Project Handoff

## Current handoff

### Date

2026-07-16 16:08 JST (Asia/Tokyo)

### Current status

- Phase 14A is complete locally and uncommitted. It includes the React functional design, MUI visual specification/wireframes, Review API, integer/maximum amount validation, OpenAPI/test updates, and a React + MUI frontend foundation.
- Phase 14B is explicitly deferred until tomorrow. Its scope is shared frontend infrastructure, business pages, and frontend tests/E2E.
- Backend full regression passed with 42 tests. Frontend lint, typecheck, one foundation test, and production build all passed.
- `main` matches `origin/main`; no commit, push, AWS change, or remote CI run was performed.

### Completed

- Added `docs/15_frontend_design.md` covering routes, fields, permissions, Basic authentication handling, API integration, errors, pagination, accessibility, and test strategy.
- Chose MUI and added `docs/16_ui_design.md` with theme tokens, desktop/tablet layout, common component rules, and wireframes for Login, expense list/detail/form, reviews, and audit logs.
- Added `GET /api/reviews` and `GET /api/reviews/{id}` for APPROVER / ADMIN. Results are restricted to other users' `SUBMITTED` applications; USER access and self-review are rejected.
- Defined item amounts as integer yen from 1 to 999999999999 and application totals at or below 999999999999; updated validation, service checks, API docs, OpenAPI, and DB documentation.
- Added Review controller/service/mapper coverage, amount validation tests, OpenAPI contract paths, and PostgreSQL integration coverage.
- Created `frontend/` with React 19.2.7, TypeScript 5.9.3, MUI 9.2.0, Vite 8.1.4, router/query/form dependencies, ESLint, Vitest, Testing Library, MSW, Vite proxy, and `pnpm-lock.yaml`.
- Updated README, requirements, basic design, API spec, authority matrix, test specifications/evidence, document index, and phase plan for Phase 14A/14B.

### In progress / uncommitted changes

- Modified: `README.md`, `handoff.md`, `docs/00_document_index.md`, `docs/01_requirements.md`, `docs/02_basic_design.md`, `docs/03_db_definition.md`, `docs/04_api_spec.md`, `docs/05_authority_matrix.md`, `docs/07_unit_test_spec.md`, `docs/08_test_evidence.md`, `docs/09_phase_plan.md`, `docs/12_integration_test_spec.md`, `docs/openapi.yaml`.
- Untracked design documents: `docs/15_frontend_design.md`, `docs/16_ui_design.md`.
- Modified backend source: `ExpenseItemRequest.java`, `ExpenseApplicationMapper.java`, `ExpenseApplicationService.java`, `ExpenseApplicationMapper.xml`.
- Untracked backend source: `ReviewController.java`, `ReviewSearchRequest.java`.
- Modified backend tests: `OpenApiContractTest.java`, `ExpenseApplicationControllerTest.java`, `ApiIntegrationTest.java`, `ExpenseApplicationServiceTest.java`.
- Untracked backend test: `ReviewControllerTest.java`.
- Untracked frontend foundation: all tracked candidates under `frontend/`; `node_modules`, `dist`, and coverage are ignored by `frontend/.gitignore`.
- Ignored local-only `.github/workflows/ci.yml` remains outside version control and must not be force-added.

### Verification

- Backend: `BUILD SUCCESSFUL`; 42 tests, 0 failures, 0 errors, 0 skipped, including Testcontainers PostgreSQL integration tests.
- Frontend: `pnpm lint` passed.
- Frontend: `pnpm typecheck` passed.
- Frontend: Vitest 1 test passed.
- Frontend: Vite production build passed; 898 modules transformed.
- `git diff --check` passed.
- Local Markdown link target check passed for README and `docs/*.md`.
- `frontend/pnpm-lock.yaml` exists. TypeScript was intentionally pinned to 5.9.3 because TypeScript 7.0.2 was incompatible with current `typescript-eslint`.

### Next steps

1. Start Phase 14B by implementing the MUI theme and responsive application layout from `docs/16_ui_design.md`.
2. Add React Router, TanStack Query, the HTTP Basic in-memory authentication state, route guards, API client, and common error/loading/empty/dialog components.
3. Implement Login, expense list/detail/create/edit, delete/submit, reviews/approve/return, and ADMIN audit-log pages.
4. Add permission/status matrix tests, form tests, MSW route integration tests, and USER/APPROVER/ADMIN E2E workflows.
5. Update frontend evidence and documentation, then review the complete uncommitted Phase 14A/14B diff before any commit or push.

### Blockers / important notes

- There is no blocker for starting Phase 14B.
- HTTP Basic credentials must remain in JavaScript memory only; do not store the password or Authorization value in localStorage, sessionStorage, cookies, URLs, logs, or analytics. Reload requires login again.
- Local frontend uses the Vite `/api` proxy to `http://localhost:8080`; production is designed for same-origin reverse proxying because backend CORS is not configured.
- MUI Core `Table` is selected instead of MUI X Data Grid; date inputs use standard MUI `TextField` date inputs for this scope.
- The user intentionally removed remote CI. Keep `.github/workflows/ci.yml` ignored and local-only unless explicitly instructed otherwise.
- No AWS resources, S3 file API, IaC, or deployment pipeline were implemented.

### Resume commands

```bash
git status --short --branch
git diff --check
git diff -- README.md docs/ src/
sed -n '1,380p' docs/16_ui_design.md
cd frontend
pnpm lint && pnpm typecheck && pnpm test && pnpm build
cd ..
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

## History

- 2026-07-16 16:08 JST: Saved the end-of-day handoff after completing uncommitted Phase 14A with MUI UI design/wireframes, Review API, integer/maximum amount validation, 42 passing backend tests, and a verified React/MUI frontend foundation. Phase 14B shared infrastructure, pages, and frontend tests are next.
- 2026-07-15 15:07 JST: Restored the CI workflow as an ignored local-only file and completed the uncommitted Phase 13 AWS architecture design. Phase 14 React frontend design is next; no AWS resources, commit, or push were created.
- 2026-07-14 16:47 JST: Completed and verified the production Dockerfile and Phase 12 documentation. The pushed CI workflow was later ignored and deleted; current code/documentation consistency must be resolved next.
- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
