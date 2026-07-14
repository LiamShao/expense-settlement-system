# CI / production container 設計書

## 1. 目的

GitHub Actions でテストと production container image の検証を自動化し、pull request と `main` branch の品質を継続的に確認する。

開発用 `Dockerfile.dev` と application 実行用 `Dockerfile` を分離し、production image には実行に必要な JRE と application JAR のみを含める。

## 2. CI 実行条件

| Event | 条件 | 目的 |
|---|---|---|
| `pull_request` | target branch が `main` | merge 前に regression を検出する。 |
| `push` | branch が `main` | merge 後の `main` を再検証する。 |
| `workflow_dispatch` | 手動実行 | 設定変更や障害調査時に再実行する。 |

同一 branch または pull request の古い実行は cancel し、最新 commit の結果を優先する。

## 3. CI job 設計

```text
test
  ├─ Java 17 / Gradle Wrapper setup
  ├─ unit / controller / contract / integration test
  ├─ executable JAR build
  └─ test report / JAR artifact upload
       ↓ success
container-image
  ├─ production image build
  ├─ PostgreSQL 16 起動
  ├─ application container 起動
  └─ /actuator/health smoke test
```

### 3.1 test job

| 項目 | 設計 |
|---|---|
| Runner | GitHub-hosted Ubuntu runner |
| Java | Temurin 17 |
| Gradle | repository の Gradle Wrapper |
| Command | `./gradlew --no-daemon clean test bootJar` |
| Database | Testcontainers が Docker daemon 上に起動する PostgreSQL 16 |
| Cache | Gradle Action の basic cache provider |
| Timeout | 20 minutes |

GitHub-hosted Ubuntu runner 上で Gradle を直接実行するため、ローカル開発コンテナで必要な Docker socket mount と `TESTCONTAINERS_HOST_OVERRIDE` は指定しない。Testcontainers は runner の Docker daemon を自動検出する。

### 3.2 container-image job

`test` job が成功した場合だけ実行する。`Dockerfile` から image を build し、PostgreSQL 16 と application container を起動する。

application container には DB 接続情報を環境変数で渡し、`/actuator/health` が HTTP 200 を返すことを確認する。これにより JAR の配置、非 root user での Java 起動、Flyway migration、PostgreSQL 接続を検証する。

Phase 12 では registry への image push は行わない。image repository、tag、認証、provenance は AWS deployment 設計と合わせて後続 Phase で定義する。

## 4. production Dockerfile 設計

| Stage | Base image | 内容 |
|---|---|---|
| `builder` | Gradle 8.10.2 / JDK 17 | Gradle Wrapper で executable JAR を生成する。test は CI の test job で実行済みのため除外する。 |
| runtime | Eclipse Temurin 17 JRE | JAR のみを配置し、非 root user で application を実行する。 |

runtime image は port `8080` を公開する。DB URL、DB user、DB password、server port は既存の Spring Boot 環境変数で注入し、credential を image に含めない。

## 5. 成果物

| Artifact | 作成条件 | Retention | 用途 |
|---|---|---|---|
| `test-reports` | 常時 | 14 days | HTML report と JUnit XML による失敗原因確認 |
| `application-jar` | test 成功時 | 14 days | CI で生成した executable JAR の確認 |

container image は runner 内で smoke test まで行い、artifact として保存しない。

## 6. 失敗時対応

| Failure | 確認箇所 | 対応 |
|---|---|---|
| Compile / test failure | Gradle log、`test-reports` | 最初の failure と関連 stack trace を確認し、local で再現する。 |
| Testcontainers failure | Gradle log、Docker availability | Docker daemon、PostgreSQL image pull、container log を確認する。 |
| Image build failure | Docker build log | builder/runtime stage、base image、JAR output を確認する。 |
| Smoke test failure | application container log | DB connection、Flyway、port、health endpoint を確認する。 |

CI が失敗した commit は merge しない。flaky test は再実行だけで正常扱いにせず、再現条件と原因を記録する。

## 7. Local verification

全テスト:

```bash
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

production image build:

```bash
docker build -t expense-settlement-system:local .
```
