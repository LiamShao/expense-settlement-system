# Phase 0: ローカル Java 開発環境

## 目的

Java 17 / Gradle をローカル PC に直接インストールせず、Docker 上で同一の開発環境を利用できるようにする。

## 使用イメージ

- `gradle:8.10.2-jdk17-jammy`
- Java 17
- Gradle 8.10.2
- Timezone: `Asia/Tokyo`
- Encoding: `UTF-8`

## サービス構成

- `backend`: PostgreSQL の health check 完了後、`./gradlew --no-daemon bootRun` で Spring Boot を自動起動するデフォルトサービス。
- `java-dev`: shell、test、任意の Gradle task に使用する `tools` profile の開発用サービス。
- `postgres`: local PostgreSQL 16。

## Backend 起動方法

```bash
docker compose up -d --build --wait
```

`http://localhost:8080/actuator/health` が healthy になるまで Compose が待機する。

## Java 開発コンテナ

`java-dev` は default の `docker compose up` では起動しない。サービスを明示した `run` では profile 指定なしで利用できる。

```bash
docker compose run --rm java-dev
java -version
gradle -version
```

任意の Gradle task も実行できる。

```bash
docker compose run --rm java-dev ./gradlew test
```

## 補足

local の `backend` と `java-dev` は `Dockerfile.dev` を共有する。本番 application image は repository root の multi-stage `Dockerfile` で別管理し、開発用 image と責務を分離している。
