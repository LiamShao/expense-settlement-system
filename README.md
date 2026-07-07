# 経費精算システム

日本企業の業務フローを想定した、経費申請・承認を行うバックエンド API プロジェクトです。  
Java / Spring Boot / PostgreSQL / MyBatis / Spring Security を用いて、単純な CRUD ではなく、権限・ステータス遷移・監査ログ・テスト・AWS 構成説明まで含めたポートフォリオとして作成しています。

## プロジェクト概要

本システムは、社員が交通費・会議費・宿泊費などの経費を申請し、承認者が承認または差戻しを行うことを想定した経費精算 API です。

現在はバックエンド API を中心に実装しています。画面については、後続フェーズで React / TypeScript による管理画面を追加予定です。

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
| Test | JUnit 5, Mockito, Spring Boot Test, MockMvc |
| Dev Environment | Docker, docker-compose |
| Build | Gradle |
| CI | GitHub Actions 追加予定 |
| Frontend | React / TypeScript 追加予定 |
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

### 実装予定

- 経費申請 CRUD
- 経費申請の検索・ページング
- ステータス遷移
  - 下書き
  - 申請中
  - 承認済み
  - 差戻し
- 承認 / 差戻し
- 権限チェック
  - USER は自分の申請のみ参照・編集可能
  - APPROVER は承認・差戻し可能
  - APPROVER は自分の申請を承認不可
  - ADMIN は全件参照可能
- 操作ログ
- Global Exception Handler
- Swagger / OpenAPI 詳細化
- テストコード拡充
- GitHub Actions
- React フロントエンド
- 日本語ドキュメント一式

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

後続フェーズで以下の観点を重点的にテストします。

- 正常系
- 異常系
- 境界値
- 権限
- ステータス遷移
- Validation
- 例外ハンドリング
- DB 更新結果確認

想定するテスト構成:

```text
Service 単体テスト       : JUnit 5 + Mockito
Controller API テスト    : MockMvc
Security テスト          : spring-security-test
DB integration test      : Spring Boot Test / Testcontainers
```

## AWS 構成案

本番想定では以下の AWS 構成を説明できる形にします。

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

## ローカル起動方法

### 1. Docker 開発環境を build

```bash
docker compose build java-dev
```

### 2. PostgreSQL を起動

```bash
docker compose up -d postgres
```

PostgreSQL はホスト側 `15432` に公開しています。

```text
Host     : localhost
Port     : 15432
Database : expense_db
User     : expense_user
Password : expense_password
```

### 3. アプリケーション起動

既存の `java-dev` コンテナを使う場合:

```bash
docker compose run --rm --service-ports java-dev ./gradlew bootRun
```

または、コンテナ内に入って実行します。

```bash
docker compose run --rm java-dev
./gradlew bootRun
```

### 4. テスト実行

```bash
docker compose run --rm --no-deps java-dev ./gradlew --no-daemon test
```

Gradle cache の lock が残る場合は、既存コンテナ側で daemon を停止します。

```bash
docker exec expense-java-dev ./gradlew --stop
```

## 今後の改善点

- JWT 認証への拡張
- Refresh token 設計
- Global Exception Handler の実装
- API エラー形式の統一
- Service 層の業務ルール実装
- 承認・差戻しフロー
- 操作ログの詳細化
- Testcontainers による DB integration test
- Swagger の API 説明追加
- GitHub Actions による CI
- 本番用 Dockerfile
- React / TypeScript フロントエンド
- AWS デプロイ手順の具体化

## 面接で説明するポイント

- TypeScript / Node.js のバックエンド経験を活かし、Java / Spring Boot のレイヤードアーキテクチャに置き換えて実装していること
- MyBatis を採用し、SQL を明示的に管理していること
- Flyway により DB schema の変更履歴を管理していること
- Entity と DTO を分離し、DB 構造と API 入出力を分けていること
- Spring Security の `UserDetailsService` を実装し、DB ユーザーによる認証を行っていること
- 経費精算という日本企業で説明しやすい業務テーマを選んでいること
- 後続で権限、状態遷移、テスト、AWS 構成、日式ドキュメントまで整備する計画であること

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
Phase 8  Global Exception Handler
Phase 9  OpenAPI / Swagger
Phase 10 テストコード
Phase 11 README / docs
Phase 12 GitHub Actions / 本番 Dockerfile
Phase 13 AWS 構成設計
Phase 14 React フロントエンド
```
