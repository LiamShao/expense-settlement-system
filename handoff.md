# Project Handoff

## Current handoff

### Date

2026-07-14 (Asia/Tokyo)

### Current status

- Phase 11 (PostgreSQL API integration tests and evidence) is complete.
- The full test suite passes: 34 tests, 0 failures, 0 errors.
- Phase 11 was committed and pushed as `31cf265` (`integration test finished`); `main` matches `origin/main`.
- Only the new handoff mechanism files are currently uncommitted.

### Completed

- Added eight full-stack API integration tests using Spring Boot Test, MockMvc, MyBatis, Flyway, and a PostgreSQL 16 Testcontainer.
- Covered database-backed login, Basic authentication, expense creation, USER/ADMIN search scope, submit/approve workflow, ownership authorization, and audit-log persistence/access.
- Added `docs/12_integration_test_spec.md` and registered it in the document indexes.
- Updated README, requirements, basic design, test evidence, and phase plan to mark Phase 11 complete.
- Updated the managed Testcontainers version to 1.21.4 for compatibility with the local Docker Engine.

### In progress / uncommitted changes

- Added: `AGENTS.md`
- Added: `handoff.md`

### Verification

- Full regression result: `BUILD SUCCESSFUL`.
- Result count: 34 tests, 0 failures, 0 errors.
- `git diff --check` passed after the handoff files were added.
- The successful integration-test run used Docker Desktop with the Docker socket mounted into the development container.

### Next steps

1. Review and commit `AGENTS.md` and `handoff.md` so the handoff workflow is shared with future checkouts.
2. Start Phase 12 with documentation-first design for GitHub Actions and the production Dockerfile.
3. Define CI triggers, test execution, Testcontainers/Docker requirements, build artifacts, and failure handling.
4. Implement a production multi-stage Dockerfile and verify the resulting application image.

### Blockers / important notes

- The host does not have a directly usable Java runtime, so Gradle verification is run through the `java-dev` Docker Compose service.
- In the development container, Testcontainers requires Docker socket access and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`.
- Using group `0` for the container process allows Docker socket access while keeping workspace files owned by UID 1000.
- The OpenAI Codex manual fetch was unavailable during handoff setup because the sandbox could not resolve `developers.openai.com`; the repository-level behavior is implemented through `AGENTS.md`.

### Resume commands

```bash
git status --short
git diff --check
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

## History

- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
