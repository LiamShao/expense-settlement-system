# 基本設計書

## 1. システム構成

本システムは Spring Boot による REST API と PostgreSQL による RDB で構成する。

```text
Client
  ↓ HTTP
Spring Boot API
  ↓ MyBatis
PostgreSQL
```

ローカル開発では Docker Compose を利用し、API コンテナと PostgreSQL コンテナを起動する。

## 2. レイヤ構成

| レイヤ | package | 役割 |
|---|---|---|
| Controller | `controller` | HTTP リクエストを受け取り、Service を呼び出す。 |
| Service | `service` | 業務ルール、権限チェック、状態遷移を行う。 |
| Repository | `repository` | MyBatis Mapper interface として DB 操作を定義する。 |
| SQL Mapper | `src/main/resources/mapper` | SQL を XML で定義する。 |
| Entity | `entity` | DB テーブルに対応するオブジェクト。 |
| DTO | `dto` | API リクエスト・レスポンス用オブジェクト。 |
| Security | `security`, `config` | 認証・認可の基盤。 |

## 3. 認証方式

- HTTP Basic 認証を利用する。
- ログインユーザーは DB の `users` テーブルから取得する。
- Spring Security の `SecurityUser` によりログインユーザー ID、メール、ロールを参照する。

## 4. 主要機能

### 4.1 認証

| 機能 | 概要 |
|---|---|
| ログイン確認 | メールアドレスとパスワードを検証し、ユーザー情報を返す。 |
| ログインユーザー取得 | 認証済みユーザー自身の情報を返す。 |

### 4.2 経費申請

| 機能 | 概要 |
|---|---|
| 一覧検索 | 自分の経費申請をページング検索する。 |
| 詳細取得 | 自分の経費申請ヘッダと明細を取得する。 |
| 作成 | 経費申請を `DRAFT` として作成する。 |
| 更新 | `DRAFT` または `RETURNED` の申請を更新する。 |
| 削除 | `DRAFT` または `RETURNED` の申請を削除する。 |
| 申請 | `DRAFT` または `RETURNED` を `SUBMITTED` に変更する。 |
| 承認 | `SUBMITTED` を `APPROVED` に変更する。 |
| 差戻し | `SUBMITTED` を `RETURNED` に変更し、差戻し理由を保存する。 |

## 5. 業務ルール

| ID | ルール |
|---|---|
| BR-EXP-001 | 経費申請作成時の初期ステータスは `DRAFT` とする。 |
| BR-EXP-002 | 合計金額はリクエストの明細金額合計から Service 層で算出する。 |
| BR-EXP-003 | USER は他ユーザーの経費申請を参照・操作できない。 |
| BR-EXP-004 | `DRAFT` / `RETURNED` のみ申請者が更新・削除できる。 |
| BR-WF-001 | `DRAFT` / `RETURNED` のみ申請できる。 |
| BR-WF-002 | `SUBMITTED` のみ承認・差戻しできる。 |
| BR-WF-003 | 承認・差戻しは `APPROVER` / `ADMIN` のみ実行できる。 |
| BR-WF-004 | 承認者・管理者は自分の申請を承認・差戻しできない。 |

## 6. 補足機能

- ADMIN 全件参照と監査ログ登録を実装済みとする。
- Global Exception Handler により API と Security のエラーレスポンスを統一する。
- `docs/openapi.yaml` を正式契約とし、Swagger UI と Prism Mock Server から利用する。
- Controller 単体テストに加え、Testcontainers PostgreSQL を利用した API 結合テストを実施する。

## 7. CI / container 構成

```text
pull request / main push
  ↓
GitHub Actions
  ├─ Gradle full test / executable JAR build
  └─ production image build / PostgreSQL smoke test
```

- GitHub-hosted Ubuntu runner と Java 17 で Gradle Wrapper を実行する。
- Testcontainers が runner の Docker daemon に PostgreSQL 16 を起動し、API 結合テストを実行する。
- production `Dockerfile` は multi-stage build とし、runtime image には JRE と executable JAR のみを配置する。
- application container は非 root user で実行し、DB credential は環境変数から受け取る。
- 詳細は `docs/13_ci_container_design.md` に定義する。

## 8. AWS production 構成

```text
Internet
  ↓ HTTPS
Application Load Balancer（public subnet / 2 AZ）
  ↓ TCP 8080
ECS Fargate（private application subnet / 2 AZ）
  ↓ TCP 5432
RDS PostgreSQL Multi-AZ（private database subnet / 2 AZ）
```

- Route 53 と ACM を利用し、public entry point は ALB の HTTPS listener のみとする。
- ECS task と RDS に public IP を付与しない。
- production image は ECR private repository に保存し、immutable tag と image scan を利用する。
- DB credential は Secrets Manager で管理し、ECS の task execution role と application task role を分離する。
- 領収書は private S3 bucket に保存する設計とするが、file upload/download は後続実装とする。
- CloudWatch Logs、Metrics、Alarm と ECS deployment rollback、RDS backup/restore を運用設計に含める。
- 詳細は `docs/14_aws_architecture_design.md` に定義する。AWS resource と deployment pipeline は未構築である。
