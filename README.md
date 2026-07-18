# 経費精算システム

日本企業の業務フローを想定した、経費申請・承認を行う Web アプリケーションです。
React / TypeScript / MUI と Java / Spring Boot / PostgreSQL / MyBatis / Spring Security を用いて、単純な CRUD ではなく、権限・ステータス遷移・監査ログ・テスト・AWS 構成説明まで含めたポートフォリオとして作成しています。

## プロジェクト概要

本システムは、社員が交通費・会議費・宿泊費などの経費を申請し、承認者が承認または差戻しを行うことを想定した経費精算システムです。

バックエンド API に加え、ログイン、申請 CRUD・申請、承認・差戻し、監査ログ検索を行う React / TypeScript frontend を実装済みです。

## 開発背景

日本の業務系開発現場では、Java / Spring Boot、RDB、SQL、設計書、テスト仕様書、権限設計、状態管理の理解が重視されます。  
本プロジェクトでは、実務に近い題材として経費精算業務を選び、以下を重点的に確認できる構成にしています。

- Spring Boot による REST API 開発
- MyBatis による SQL 明示型のデータアクセス
- Flyway による DB migration 管理
- Spring Security による認証・認可
- PostgreSQL を前提とした業務 DB 設計
- JUnit / MockMvc を用いたテスト方針
- AWS 上での構成説明
- 日本語の基本設計書・DB 定義書・API 仕様書・テスト仕様書

## 使用技術

| 分類 | 技術 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.6 |
| Web | Spring Web |
| Security | Spring Security |
| Database | PostgreSQL 16 |
| DB Migration | Flyway |
| ORM / SQL Mapper | MyBatis |
| Validation | Jakarta Bean Validation |
| API Doc | springdoc-openapi / Swagger UI |
| Test | JUnit 5, Mockito, Spring Boot Test, MockMvc, Vitest, Testing Library, MSW, Playwright |
| Dev Environment | Docker, docker-compose |
| Build | Gradle |
| CI | GitHub Actions 設計（workflow は local-only / ignore） |
| Frontend | React 19.2.7 / TypeScript 5.9.3 / MUI 9.2.0 / Vite 8.1.4 |
| AWS Design | ECS Fargate, RDS, S3, ALB, CloudWatch, IAM, VPC, Secrets Manager |

## 主な機能

### 実装済み

- Docker による Java 17 / Gradle 開発環境
- PostgreSQL の docker-compose 構成
- Flyway migration
- ユーザー、経費申請、経費明細、監査ログの DB schema
- 初期ユーザー投入
- Entity / DTO / Enum の定義
- MyBatis Mapper interface / XML
- Spring Security による HTTP Basic 認証
- DB ユーザーを利用したログイン
- ログインユーザー取得 API
- 経費申請 CRUD
- 経費申請の検索・ページング
- 経費明細の登録・更新
- 経費申請合計金額の自動計算
- USER は自分の申請のみ参照・編集・削除可能
- ステータス遷移
  - 下書き
  - 申請中
  - 承認済み
  - 差戻し
- 承認 / 差戻し
- APPROVER / ADMIN 向け承認待ち一覧・詳細 API
- 金額を整数円、明細・合計を最大 999999999999 円に制限
- 権限チェック
  - USER は自分の申請のみ参照・編集可能
  - ADMIN は全ユーザーの申請を参照可能
  - APPROVER / ADMIN は承認・差戻し可能
  - APPROVER / ADMIN は自分の申請を承認・差戻し不可
- 操作ログ
  - 経費申請の作成・更新・削除・申請・承認・差戻しを監査ログに記録
  - ADMIN は監査ログを検索可能
- Global Exception Handler
  - Validation、リクエスト形式、業務例外、予期しない例外の JSON 形式を統一
  - Spring Security の未認証・権限不足レスポンスを統一
  - 500 response では内部例外情報を非公開
- OpenAPI / Swagger
  - `docs/openapi.yaml` を正式な API 契約として管理
  - Swagger UI から HTTP Basic 認証付き API を実行可能
  - Prism Mock Server を Docker Compose profile で起動可能
  - OpenAPI の endpoint、operationId、認証、エラー契約を自動テスト
- テストコード
  - ExpenseApplication / AuditLog Service の業務ルールを JUnit 5 / Mockito で検証
  - Auth / ExpenseApplication / Review / AuditLog Controller を MockMvc で検証
  - Testcontainers PostgreSQL を利用した主要 API の結合テスト
  - 全 42 backend テストが成功
- React frontend
  - password / Authorization value を browser storage に保存しない HTTP Basic in-memory authentication
  - role guard、responsive sidebar / drawer、MUI theme、共通 loading / error / empty / dialog component
  - URL query と同期した申請一覧・承認待ち・監査ログ検索と server-side pagination
  - 申請詳細、明細を含む作成・編集、削除・申請・承認・差戻し
  - role / status action matrix、form validation、logout confirmation、MSW route integration を含む 35 frontend test
  - 実 PostgreSQL API に対する USER / APPROVER / ADMIN の Playwright E2E workflow
  - ESLint、typecheck、Vitest、production build、Playwright E2E が成功
- CI design / production container
  - GitHub Actions workflow は設計・local 検証済みだが、現在は ignore された local-only file で remote CI は未設定
  - workflow 設計では全テスト、executable JAR、テストレポートを生成
  - production multi-stage Dockerfile を非 root runtime user で実行
  - PostgreSQL と接続した production image の health check
- 日本語ドキュメント一式
  - 要件定義書
  - 基本設計書
  - DB 定義書
  - API 仕様書
  - 権限マトリクス
  - 状態遷移表
  - 単体テスト仕様書
  - テストエビデンス
  - AWS production architecture 設計
  - React / TypeScript frontend 設計
  - MUI UI theme / wireframe 設計

## アーキテクチャ

典型的な Spring Boot のレイヤードアーキテクチャを採用しています。

```text
Controller
  ↓
Service
  ↓
Repository / MyBatis Mapper
  ↓
Mapper XML SQL
  ↓
PostgreSQL
```

現在の主な package 構成は以下です。

```text
com.example.expense
├── common       # 共通レスポンス、ページング、Enum
├── config       # Spring Security などの設定
├── controller   # REST API Controller
├── dto          # Request / Response DTO
├── entity       # DB テーブルに対応する Entity
├── repository   # MyBatis Mapper interface
├── security     # Spring Security 連携クラス
└── service      # 業務処理
```

MyBatis の SQL は以下に配置しています。

```text
src/main/resources/mapper/
```

DB migration は以下に配置しています。

```text
src/main/resources/db/migration/
```

## DB 設計

主要テーブルは以下です。

| テーブル | 概要 |
|---|---|
| users | ユーザー、社員番号、メールアドレス、ロール |
| expense_applications | 経費申請ヘッダ、申請者、ステータス、合計金額 |
| expense_items | 経費明細、利用日、カテゴリ、金額、領収書キー |
| audit_logs | 操作ログ |

ステータスは DB 上では `VARCHAR + CHECK 制約` で管理し、Java 側では `ExpenseStatus` enum として扱います。

```text
DRAFT      : 下書き
SUBMITTED  : 申請中
APPROVED   : 承認済み
RETURNED   : 差戻し
```

## API 仕様

現時点で利用可能な API は以下です。

| Method | Endpoint | 認証 | 概要 |
|---|---|---|---|
| POST | `/api/auth/login` | 不要 | ログイン確認 |
| GET | `/api/auth/me` | 必要 | ログインユーザー取得 |
| GET | `/api/expense-applications` | 必要 | 経費申請一覧検索。ADMIN は全件参照可能 |
| GET | `/api/expense-applications/{id}` | 必要 | 経費申請詳細取得。ADMIN は他人の申請も参照可能 |
| POST | `/api/expense-applications` | 必要 | 経費申請作成 |
| PUT | `/api/expense-applications/{id}` | 必要 | 下書き・差戻しの経費申請更新 |
| DELETE | `/api/expense-applications/{id}` | 必要 | 下書き・差戻しの経費申請削除 |
| POST | `/api/expense-applications/{id}/submit` | 必要 | 下書き・差戻しの経費申請を申請 |
| POST | `/api/expense-applications/{id}/approve` | 必要 | 申請中の経費申請を承認 |
| POST | `/api/expense-applications/{id}/return` | 必要 | 申請中の経費申請を差戻し |
| GET | `/api/reviews` | 必要 | APPROVER / ADMIN の承認待ち一覧検索 |
| GET | `/api/reviews/{id}` | 必要 | APPROVER / ADMIN の承認待ち詳細取得 |
| GET | `/api/audit-logs` | 必要 | 監査ログ検索。ADMIN のみ |
| GET | `/actuator/health` | 不要 | ヘルスチェック |

ログインは HTTP Basic 認証を採用しています。  
`POST /api/auth/login` は認証確認とユーザー情報返却を行います。JWT は後続改善点として検討します。

## 認証確認

初期ユーザーは以下です。

| Role | Email | Password |
|---|---|---|
| USER | `user@example.com` | `Password123!` |
| APPROVER | `approver@example.com` | `Password123!` |
| ADMIN | `admin@example.com` | `Password123!` |

ログイン確認:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

ログインユーザー取得:

```bash
curl -u user@example.com:Password123! \
  http://localhost:8080/api/auth/me
```

未認証の場合は `401 Unauthorized` を返します。

## テスト方針

以下の観点を重点的にテストしています。

- 正常系
- 異常系
- 境界値
- 権限
- ステータス遷移
- Validation
- 例外ハンドリング
- DB 更新結果確認

テスト構成:

```text
Service 単体テスト       : JUnit 5 + Mockito
Controller API テスト    : MockMvc
Security テスト          : spring-security-test
DB integration test      : Spring Boot Test / MockMvc / Testcontainers
```

## 設計・テストドキュメント

日本の業務系開発における文書中心の進め方を学習するため、要件、設計、API、権限、状態遷移、テスト仕様、エビデンスを `docs/` に整理しています。

| 文書 | 概要 |
|---|---|
| [ドキュメント一覧](docs/00_document_index.md) | 文書構成と更新ルール |
| [要件定義書](docs/01_requirements.md) | 機能要件・非機能要件 |
| [基本設計書](docs/02_basic_design.md) | システム構成・業務ルール |
| [DB定義書](docs/03_db_definition.md) | テーブル・制約・インデックス |
| [API仕様書](docs/04_api_spec.md) | エンドポイント・リクエスト・制約 |
| [権限マトリクス](docs/05_authority_matrix.md) | ロール別操作可否 |
| [状態遷移表](docs/06_status_transition.md) | 経費申請ステータス遷移 |
| [単体テスト仕様書](docs/07_unit_test_spec.md) | JUnit / Mockito のテスト観点 |
| [テストエビデンス](docs/08_test_evidence.md) | テスト実行結果 |
| [開発フェーズ計画](docs/09_phase_plan.md) | 後続フェーズの文書先行計画 |
| [エラーハンドリング設計書](docs/10_error_handling.md) | 共通エラー形式・例外マッピング |
| [OpenAPI / Swagger / Mock 利用手順](docs/11_openapi_mock.md) | Swagger UI・Prism Mock Server の利用方法 |
| [API結合テスト仕様書](docs/12_integration_test_spec.md) | PostgreSQL を利用した API 結合テスト仕様 |
| [CI / production container 設計書](docs/13_ci_container_design.md) | GitHub Actions と production image の設計・運用方針 |
| [AWS アーキテクチャ設計書](docs/14_aws_architecture_design.md) | AWS 上の network、ECS、RDS、S3、security、monitoring、責任分界 |
| [フロントエンド設計書](docs/15_frontend_design.md) | React / TypeScript の画面、navigation、項目、権限、API 連携、error、test 方針 |
| [MUI UI デザイン仕様書](docs/16_ui_design.md) | MUI theme、layout、component 方針、responsive rule、主要画面 wireframe |
| [OpenAPI定義](docs/openapi.yaml) | 実装済み API の機械可読な契約 |

## AWS 構成案

本番想定では以下の AWS 構成を設計済みです。AWS resource 自体は未構築です。

```text
Internet
  ↓
ALB
  ↓
ECS Fargate
  ↓
RDS PostgreSQL

Receipt files
  ↓
S3

Logs / Metrics
  ↓
CloudWatch

Secrets
  ↓
Secrets Manager
```

設計観点:

- ECS Fargate によるコンテナ実行
- RDS PostgreSQL による managed database
- S3 による領収書ファイル保存
- ALB による HTTPS 終端
- CloudWatch Logs / Metrics による監視
- IAM Role による最小権限
- VPC / subnet / security group によるネットワーク分離
- Secrets Manager による DB 接続情報管理

詳細、未実装範囲、復旧・監視・費用方針は [AWS アーキテクチャ設計書](docs/14_aws_architecture_design.md) を参照してください。

## ローカル起動方法

### 1. Backend / PostgreSQL 起動

```bash
docker compose up -d --build --wait
```

上記コマンドで PostgreSQL の health check 完了後に Spring Boot が自動起動し、backend の health check 完了まで待機します。

```text
Backend     : http://localhost:8080
Swagger UI  : http://localhost:8080/swagger-ui.html
Health      : http://localhost:8080/actuator/health
```

PostgreSQL はホスト側 `15432` に公開しています。

```text
Host     : localhost
Port     : 15432
Database : expense_db
User     : expense_user
Password : expense_password
```

起動状態とログを確認する場合:

```bash
docker compose ps
docker compose logs -f backend
```

停止する場合:

```bash
docker compose down
```

`postgres-data` volume は `docker compose down` では削除されません。

### 2. Java / Gradle ツールコンテナ

```bash
docker compose run --rm java-dev
```

`java-dev` は `tools` profile の対話・テスト用コンテナです。明示的な `docker compose run` では profile 指定なしで利用できます。

```bash
docker compose run --rm java-dev ./gradlew test
```

### 3. テスト実行

Docker Desktop 上で Testcontainers を含む全テストを実行します。
backend と同じ source / Gradle cache を使用するため、先に backend を停止します。

```bash
docker compose stop backend
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

テスト後に backend を再開する場合:

```bash
docker compose up -d --wait backend
```

### 4. Swagger UI / Mock API

アプリケーション起動後の Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI 契約だけを使う Mock Server:

```bash
docker compose --profile mock up api-mock
```

詳細は [OpenAPI / Swagger / Mock 利用手順](docs/11_openapi_mock.md) を参照してください。

### 5. Production image

application 実行用 image を build します。

```bash
docker build -t expense-settlement-system:local .
```

DB 接続情報は `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` で指定します。CI と production container の詳細は [CI / production container 設計書](docs/13_ci_container_design.md) を参照してください。

### 6. Frontend

Node.js 22.12 以上と pnpm 10 を利用します。

```bash
cd frontend
pnpm install
pnpm dev
```

Development server は `http://localhost:5173` で起動し、`/api` を `http://localhost:8080` へ proxy します。

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

E2E は backend と PostgreSQL を起動した状態で Chromium を利用して実行します。

```bash
pnpm exec playwright install chromium
pnpm e2e
```

E2E は一意な件名で申請を作成するため再実行可能ですが、local DB に経費申請と監査ログを追加します。

## 今後の改善点

- JWT 認証への拡張
- Refresh token 設計
- 操作ログの詳細化
- AWS デプロイ手順の具体化

## 開発フェーズ

```text
Phase 0  Docker ローカル Java 環境
Phase 1  Spring Boot プロジェクト骨格
Phase 2  DB schema / Flyway
Phase 3  Entity / DTO / MyBatis Mapper
Phase 4  Spring Security ログイン / 認証基盤
Phase 5  経費申請 CRUD + Service 業務ルール
Phase 6  承認 / 差戻し
Phase 7  ADMIN 参照 / 操作ログ
Phase 8  Global Exception Handler（完了）
Phase 9  OpenAPI / Swagger（完了）
Phase 10 テストコード（完了）
Phase 11 結合テスト / エビデンス（完了）
Phase 12 CI 設計 / 本番 Dockerfile（完了、remote workflow は削除済み）
Phase 13 AWS 構成設計（完了）
Phase 14A MUI UI設計 / Review API / frontend foundation（完了）
Phase 14B React 共通基盤 / 業務画面 / frontend test（完了）
```
