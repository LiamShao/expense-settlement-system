# OpenAPI / Swagger / Mock 利用手順

## 1. 正式な API 契約

`docs/openapi.yaml` を本プロジェクトの OpenAPI 契約の唯一の原本とする。Gradle の `processResources` が原本を `static/openapi.yaml` として build artifact に格納し、Swagger UI はそのファイルを読み込む。

## 2. Swagger UI

アプリケーション起動後、以下を開く。

```text
http://localhost:8080/swagger-ui.html
```

Phase 15 以降は HTTP Basic を使用しない。まず `GET /api/auth/csrf` を実行し、同じ browser session で `POST /api/auth/login` の `X-CSRF-TOKEN` header に取得 token を設定する。Login 成功後は browser が `SESSION` cookie を送信する。Swagger UI で unsafe method を実行する場合も最新の CSRF token を header に設定する。

Swagger UI が読み込む契約は以下で確認できる。

```text
http://localhost:8080/openapi.yaml
```

## 3. Prism Mock Server

アプリケーションや PostgreSQL を起動せず、OpenAPI の example から Mock API を返す場合は以下を実行する。

```bash
docker compose --profile mock up api-mock
```

Mock Base URL:

```text
http://localhost:4010
```

例。Prism は OpenAPI example と security requirement の形を確認するための mock であり、Spring Session JDBC の login、cookie rotation、logout invalidation を再現するものではない。

```bash
curl -H 'Cookie: SESSION=mock-session' \
  http://localhost:4010/api/expense-applications
```

停止:

```bash
docker compose --profile mock down
```

## 4. 更新ルール

- Endpoint、DTO、Validation、status code を変更する前に `docs/openapi.yaml` を更新する。
- 実装後に `OpenApiContractTest` と全テストを実行する。
- Swagger UI と Mock Server の両方が同じ `docs/openapi.yaml` を使用する状態を維持する。
- Session lifecycle、CSRF、account lock は Mock Server ではなく backend integration test と real API E2E で検証する。
