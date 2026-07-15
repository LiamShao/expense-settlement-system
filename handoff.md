# Project Handoff

## Current handoff

### Date

2026-07-15 15:07 JST (Asia/Tokyo)

### Current status

- Phase 13 AWS architecture design is complete locally. A new `docs/14_aws_architecture_design.md` defines the production network, ECS Fargate, RDS, S3, Secrets Manager/IAM, monitoring, deployment, recovery, cost, and responsibility boundaries.
- README, requirements, basic design, document index, and phase plan were updated. Phase 13 is marked complete and Phase 14 React frontend is the next planned phase.
- No AWS resources were created. IaC, AWS deployment, S3 file APIs, and remote CI/CD remain unimplemented.
- `main` still matches `origin/main` at `332f546`; all Phase 13 documentation changes and this handoff are uncommitted.

### Completed

- Confirmed that the earlier CI workflow deletion was intentional.
- Restored `.github/workflows/ci.yml` from `8681f2c` as an ignored local-only file while retaining `.gitignore`; it is not tracked, committed, pushed, or active on GitHub.
- Added the Phase 13 AWS architecture design using an `ap-northeast-1`, two-AZ production baseline with an internet-facing ALB and private ECS/RDS tiers.
- Defined ECR immutable image/deployment policy, ECS circuit-breaker rollback, RDS Multi-AZ/backup policy, private S3 receipt storage, IAM role separation, CloudWatch monitoring, provisional RTO/RPO, cost considerations, and AWS/project responsibility boundaries.
- Updated the formal plan so Phase 14 frontend design and implementation are next.

### In progress / uncommitted changes

- Modified: `README.md`.
- Modified: `docs/00_document_index.md`.
- Modified: `docs/01_requirements.md`.
- Modified: `docs/02_basic_design.md`.
- Modified: `docs/09_phase_plan.md`.
- Untracked: `docs/14_aws_architecture_design.md`.
- Modified: `handoff.md` (this end-of-day snapshot).
- Ignored local-only file: `.github/workflows/ci.yml`; its blob matches `8681f2c` exactly and it must remain ignored until the user decides otherwise.
- No application source, test, Dockerfile, or infrastructure files were changed today.

### Verification

- `git diff --check` passed after the Phase 13 documentation changes.
- Local Markdown link target check passed for README and `docs/*.md`.
- `.github/workflows/ci.yml` exists locally, is matched by `.gitignore`, and has the same Git blob hash as `8681f2c:.github/workflows/ci.yml`.
- Application tests were not run today because all Phase 13 changes are documentation-only.
- Last full application regression remains 2026-07-14: 34 tests, 0 failures, 0 errors, 0 skipped.
- No AWS deployment, remote GitHub Actions run, commit, or push was performed.

### Next steps

1. Review the uncommitted Phase 13 documentation diff and adjust any provisional production assumptions if needed; do not push unless the user explicitly allows it.
2. Start Phase 14 with a frontend design document covering page list, navigation, field definitions, role/status behavior, authentication, API integration, errors, and pagination.
3. Derive the initial pages from the implemented APIs: login, application list/detail/create/edit, submit/approve/return operations, and ADMIN audit-log search.
4. Update README, requirements, basic design, authority matrix, and phase plan to distinguish frontend design from implementation before creating the React application.
5. After design approval, choose the React/TypeScript toolchain and implement Phase 14 with tests.

### Blockers / important notes

- The user intentionally removed remote CI. Keep `.github/workflows/ci.yml` ignored and local-only; do not force-add, commit, or push it without a new explicit request.
- Phase 13 values such as `10.20.0.0/16`, ECS 0.5 vCPU/1 GiB, scaling 2-6, 30/90-day log retention, RPO 5 minutes, and RTO 4 hours are provisional and must be confirmed before infrastructure implementation.
- HTTP Basic remains the implemented authentication method; the AWS design flags JWT/OIDC and stronger production authentication as a later requirement.
- Phase 13 is design-only. Do not describe AWS, S3 upload/download, IaC, ECR deployment, or CloudWatch alarms as implemented.
- The host does not have a directly usable Java runtime, so Gradle verification is run through the `java-dev` Docker Compose service.
- In the development container, Testcontainers requires Docker socket access and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`.
- Using group `0` for the container process allows Docker socket access while keeping workspace files owned by UID 1000.

### Resume commands

```bash
git status --short --branch
git diff -- README.md docs/ handoff.md
sed -n '1,360p' docs/14_aws_architecture_design.md
sed -n '1,140p' docs/09_phase_plan.md
git diff --check
git check-ignore -v .github/workflows/ci.yml
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

## History

- 2026-07-15 15:07 JST: Restored the CI workflow as an ignored local-only file and completed the uncommitted Phase 13 AWS architecture design. Phase 14 React frontend design is next; no AWS resources, commit, or push were created.
- 2026-07-14 16:47 JST: Completed and verified the production Dockerfile and Phase 12 documentation. The pushed CI workflow was later ignored and deleted; current code/documentation consistency must be resolved next.
- 2026-07-14: Completed Phase 11 with eight PostgreSQL API integration tests; full suite passed with 34 tests. Added the repository handoff workflow.
