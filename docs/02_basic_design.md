# 基本設計書

## 1. システム構成

本システムは React / TypeScript による SPA、Spring Boot による REST API、PostgreSQL による RDB で構成する。frontend と backend API は実装済みである。

```text
Browser / React SPA
  ↓ HTTPS / JSON or multipart / SESSION cookie / CSRF header
Spring Boot API
  ├─ MyBatis / Spring Session JDBC → PostgreSQL
  └─ ReceiptStorage → local private filesystem（Phase 16B）/ private S3（Phase 16E）
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
| Storage port / adapter | `storage`（Phase 16B/16E 実装済み） | `ReceiptStorage` / `MalwareScanner` port、local/fail-closed adapter、AWS SDK v2 private S3 adapter。 |

Frontend は feature 単位で auth、expenses、reviews、audit logs を分離し、routing、server state、form、API client の責務を分ける。詳細は `docs/15_frontend_design.md` に定義する。

## 3. 認証方式

- Phase 15 で HTTP Basic を廃止し、Spring Security + Spring Session JDBC の server-side session を利用する。
- ログインユーザーは DB の `users` テーブルから取得する。
- Spring Security の `SecurityUser` によりログインユーザー ID、メール、ロールを参照する。
- Browser は opaque な `SESSION` ID だけを `HttpOnly` cookie に保持し、session 本体は PostgreSQL の `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES` に保存する。
- Session idle timeout は 30 分とし、login 時に session ID を変更し、logout 時に current session を無効化する。
- Cookie は `SameSite=Lax`、default `Secure=true` とする。`local` profile の loopback HTTP だけ `Secure=false` を明示的に許容する。
- Unsafe method は session に紐づく CSRF token を `X-CSRF-TOKEN` header で送信する。
- 連続 5 回の login failure で account を 15 分 lock する。Failure response は account existence、disabled、locked を区別しない。
- 認証方式の選択理由と再検討条件は [ADR-001](adr/ADR-001-production-authentication.md) に記録する。

## 4. 主要機能

### 4.1 認証

| 機能 | 概要 |
|---|---|
| CSRF token 取得 | Session に紐づく CSRF token と request header name を返す。 |
| ログイン | メールアドレスとパスワードを検証し、session を開始してユーザー情報を返す。 |
| ログインユーザー取得 | 認証済みユーザー自身の情報を返す。 |
| ログアウト | Current session を server-side で無効化し、session cookie を削除する。 |

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
| 承認待ち検索 | APPROVER / ADMIN が他人の `SUBMITTED` 申請を検索・詳細参照する。 |

### 4.3 領収書ファイル（Phase 16D HTTP API 実装済み）

| 機能 | 概要 |
|---|---|
| Upload / replace | 申請者本人が `DRAFT` / `RETURNED` の明細へ JPEG/PNG/PDF を登録する。 |
| Metadata | File name、Content-Type、size、checksum、upload 日時を取得し、storage key は返さない。 |
| Preview / download | Owner、reviewer、ADMIN の認可後に backend が private storage から stream する。 |
| Delete | 編集可能 status の申請者本人だけが active receipt を削除できる。 |
| Validation / scan | 10/11 MiB multipart limit、file name/extension/type/magic bytes/SHA-256 validation、scanner port、local EICAR adapter、未設定時 fail closed を実装済み。 |
| Recovery | V5 state schema/mapper は実装済み。DB/storage orchestration、cleanup、retry/reconciliation Service は後続。 |

## 5. 業務ルール

| ID | ルール |
|---|---|
| BR-EXP-001 | 経費申請作成時の初期ステータスは `DRAFT` とする。 |
| BR-EXP-002 | 合計金額はリクエストの明細金額合計から Service 層で算出する。 |
| BR-EXP-003 | USER は他ユーザーの経費申請を参照・操作できない。 |
| BR-EXP-004 | `DRAFT` / `RETURNED` のみ申請者が更新・削除できる。 |
| BR-EXP-005 | 明細金額は 1 以上 999999999999 以下の整数円、申請合計は 999999999999 円以下とする。 |
| BR-WF-001 | `DRAFT` / `RETURNED` のみ申請できる。 |
| BR-WF-002 | `SUBMITTED` のみ承認・差戻しできる。 |
| BR-WF-003 | 承認・差戻しは `APPROVER` / `ADMIN` のみ実行できる。 |
| BR-WF-004 | 承認者・管理者は自分の申請を承認・差戻しできない。 |
| BR-RCP-001 | 1 経費明細につき active receipt は最大 1 件とする。 |
| BR-RCP-002 | Upload / replace / delete は申請者本人かつ `DRAFT` / `RETURNED` だけ許可する。 |
| BR-RCP-003 | APPROVER は他人の `SUBMITTED` review、ADMIN は review または管理参照できる申請の receipt content だけ取得できる。 |
| BR-RCP-004 | Storage key は application が生成し、request、response、URL、log に公開しない。 |

## 6. 補足機能

- ADMIN 全件参照と監査ログ登録を実装済みとする。
- Global Exception Handler により API と Security のエラーレスポンスを統一する。
- `docs/openapi.yaml` を正式契約とし、Swagger UI と Prism Mock Server から利用する。
- Controller 単体テストに加え、Testcontainers PostgreSQL を利用した API 結合テストを実施する。

## 7. CI / container 構成

```text
pull request / main push（workflow design）
  ↓
GitHub Actions
  ├─ Gradle full test / executable JAR build
  └─ production image build / PostgreSQL smoke test
```

- GitHub-hosted Ubuntu runner と Java 17 で Gradle Wrapper を実行する。
- Testcontainers が runner の Docker daemon に PostgreSQL 16 を起動し、API 結合テストを実行する。
- production `Dockerfile` は multi-stage build とし、runtime image には JRE と executable JAR のみを配置する。
- application container は非 root user で実行し、DB credential は環境変数から受け取る。
- GitHub Actions workflow は設計・local 検証済みだが、remote から削除した local-only file であり、現在 remote CI は稼働していない。
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
- 領収書は storage adapter を介して local private filesystem / private S3 に保存し、backend proxy で認可済み content を streaming する。Phase 16B で local foundation、Phase 16C で Service、Phase 16D で HTTP API、Phase 16E で S3 adapter を実装済みで、frontend file UI は未実装である。
- CloudWatch Logs、Metrics、Alarm と ECS deployment rollback、RDS backup/restore を運用設計に含める。
- 詳細は `docs/14_aws_architecture_design.md` に定義する。AWS resource と deployment pipeline は未構築である。

## 9. Frontend 構成

```text
Browser
  ↓ React Router
Page / feature component
  ↓ query / form
Shared API client
  ↓ SESSION cookie / X-CSRF-TOKEN / JSON
Spring Boot API
```

- Login、申請一覧・詳細・作成・編集、承認待ち、監査ログを SPA route として提供する。
- Password と session ID は JavaScript から参照せず、browser storage に保存しない。CSRF token は JavaScript memory のみに保持する。
- Application 起動時に `/api/auth/me` を実行し、有効 session があれば login state を復元する。
- Local は Vite proxy、production は reverse proxy により frontend と `/api` を same-origin で公開する。
- Role / status による表示制御を行うが、最終的な認可は backend が実施する。
- APPROVER / ADMIN の承認画面は `/api/reviews` と `/api/reviews/{id}` で他人の `SUBMITTED` 申請を参照する。
- 詳細な画面項目、navigation、API、error、pagination、test 方針は `docs/15_frontend_design.md` に定義する。
- 領収書 file の metadata/state、API、storage、security、frontend、verification は `docs/17_receipt_file_design.md` に定義する。
