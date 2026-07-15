# Project Handoff

## Current handoff

### Date

2026-07-14 16:47 JST (Asia/Tokyo)

### Current status

- Phase 12 documentation and the production multi-stage Dockerfile were completed, verified, committed as `8681f2c` (`add CI and production container`), and pushed.
- The GitHub Actions workflow added by `8681f2c` was subsequently deleted by pushed commit `d474186`; commit `ab38fa5` also added `.github/workflows/ci.yml` to `.gitignore`.
- The repository therefore currently has no GitHub Actions workflow, although the Phase 12 documentation and phase plan still describe CI as implemented/complete.
- Before this handoff update, the working tree was clean and `main` matched `origin/main` at `d474186`.

### Completed

- Added `Dockerfile` with a Gradle/JDK 17 builder stage and Eclipse Temurin 17 JRE runtime stage.
- Configured the production image to run as non-root user `spring:spring`, expose port 8080, and accept database credentials through environment variables.
- Added `docs/13_ci_container_design.md` and updated README, requirements, basic design, test evidence, document index, and phase plan for Phase 12.
- Added and locally validated a two-job GitHub Actions workflow in `8681f2c`; this workflow is no longer present at current `HEAD` because of `d474186`.
- Added and pushed the repository handoff workflow in `f58aa5d`.

### In progress / uncommitted changes

- Modified: `handoff.md` (this end-of-day snapshot only).
- No application, Dockerfile, or documentation changes were uncommitted before the handoff update.

### Verification

- Full regression at 2026-07-14 16:24 JST: `BUILD SUCCESSFUL` in 56 seconds; 34 tests, 0 failures, 0 errors, 0 skipped.
- Production image `expense-settlement-system:phase12` built successfully from the new multi-stage `Dockerfile`.
- The production container ran as `spring:spring`, connected to PostgreSQL/Flyway, and returned `UP` from `/actuator/health`.
- The workflow YAML parsed successfully and `git diff --check` passed before it was committed; no remote GitHub Actions run was confirmed.
- Current `HEAD` does not contain `.github/workflows/ci.yml`, so CI is not active in the current repository state.

### Next steps

1. Confirm why `.github/workflows/ci.yml` was ignored and deleted after `8681f2c`.
2. If the deletion was intentional, update README and `docs/01_requirements.md`, `docs/02_basic_design.md`, `docs/08_test_evidence.md`, `docs/09_phase_plan.md`, and `docs/13_ci_container_design.md` so they no longer claim CI is active.
3. If CI should remain part of Phase 12, remove its `.gitignore` entry, restore the workflow from `8681f2c`, push it, and verify the first GitHub Actions run.
4. Once code and documentation agree, start Phase 13 with the AWS architecture and responsibility-boundary design.

### Blockers / important notes

- The current CI documentation conflicts with current `HEAD`: production container support exists, but the workflow file does not.
- Do not restore or rewrite the two user-created commits without first confirming whether the CI removal was deliberate.
- The host does not have a directly usable Java runtime, so Gradle verification is run through the `java-dev` Docker Compose service.
- In the development container, Testcontainers requires Docker socket access and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`.
- Using group `0` for the container process allows Docker socket access while keeping workspace files owned by UID 1000.

### Resume commands

```bash
git status --short --branch
git log --oneline -5
git show --stat d474186
git show 8681f2c:.github/workflows/ci.yml
git diff --check
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

## History

- 2026-07-14 16:47 JST: Completed and verified the production Dockerfile and Phase 12 documentation. The pushed CI workflow was later ignored and deleted; current code/documentation consistency must be resolved next.
- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
