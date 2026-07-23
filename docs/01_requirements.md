# 要件定義書

## 1. システム概要

本システムは、社員が経費申請を作成し、承認者が申請内容を承認または差戻しできる Web application である。React / TypeScript frontend、Spring Boot API、PostgreSQL で構成する。

対象業務は、交通費、会議費、備品費、宿泊費などの社内経費精算を想定する。

## 2. 利用者

| ロール | 利用者 | 概要 |
|---|---|---|
| USER | 一般社員 | 自分の経費申請を作成、参照、更新、削除、申請する。 |
| APPROVER | 承認者 | 申請中の経費申請を承認または差戻しする。 |
| ADMIN | 管理者 | 全ユーザーの経費申請を参照し、管理者として承認・差戻し、監査ログ参照を行う。 |

## 3. 機能要件

| ID | 要件 | 状態 |
|---|---|---|
| REQ-AUTH-001 | ユーザーはメールアドレスとパスワードでログインできる。 | 実装済み |
| REQ-AUTH-002 | 認証済みユーザーは自分のユーザー情報を取得できる。 | 実装済み |
| REQ-AUTH-003 | Login 成功時に server-side session を開始し、browser reload 後も有効期限内の認証状態を復元できる。 | 実装済み |
| REQ-AUTH-004 | ユーザーは current session を logout でき、logout 後の session ID は再利用できない。 | 実装済み |
| REQ-AUTH-005 | 連続 5 回の login failure で account を 15 分間一時 lock し、成功時に failure count を reset する。 | 実装済み |
| REQ-AUTH-006 | Account は管理者または seed/migration により作成し、self-registration は提供しない。 | 実装済み |
| REQ-EXP-001 | USER は経費申請を下書きとして作成できる。 | 実装済み |
| REQ-EXP-002 | USER は自分の経費申請一覧を検索できる。 | 実装済み |
| REQ-EXP-003 | USER は自分の経費申請詳細を参照できる。 | 実装済み |
| REQ-EXP-004 | USER は下書きまたは差戻しの経費申請を更新できる。 | 実装済み |
| REQ-EXP-005 | USER は下書きまたは差戻しの経費申請を削除できる。 | 実装済み |
| REQ-EXP-006 | 経費申請の合計金額は明細金額から自動計算される。 | 実装済み |
| REQ-WF-001 | USER は下書きまたは差戻しの経費申請を申請中にできる。 | 実装済み |
| REQ-WF-002 | APPROVER / ADMIN は申請中の経費申請を承認できる。 | 実装済み |
| REQ-WF-003 | APPROVER / ADMIN は申請中の経費申請を差戻しできる。 | 実装済み |
| REQ-WF-004 | APPROVER / ADMIN は自分の経費申請を承認・差戻しできない。 | 実装済み |
| REQ-ADM-001 | ADMIN は全ユーザーの経費申請一覧と詳細を参照できる。 | 実装済み |
| REQ-AUD-001 | 経費申請の作成、更新、削除、申請、承認、差戻しは監査ログに記録される。 | 実装済み |
| REQ-AUD-002 | ADMIN は監査ログを検索できる。USER / APPROVER は監査ログを参照できない。 | 実装済み |
| REQ-REV-001 | APPROVER / ADMIN は他人の申請中データを承認待ち一覧で検索できる。 | 実装済み |
| REQ-REV-002 | APPROVER / ADMIN は他人の申請中データを承認判断のため詳細参照できる。 | 実装済み |
| REQ-FE-001 | 利用者は React / TypeScript の画面から login、申請検索、詳細参照、作成、編集、削除、申請を行える。 | 実装済み |
| REQ-FE-002 | APPROVER / ADMIN は承認待ち画面から他人の申請内容を確認し、承認または差戻しできる。 | 実装済み |
| REQ-FE-003 | ADMIN は画面から監査ログを検索・ページングできる。 | 実装済み |
| REQ-FE-004 | frontend は role、申請者、status に応じて navigation と action の表示・操作可否を制御する。 | 実装済み |
| REQ-RCP-001 | 申請者は自分の `DRAFT` / `RETURNED` の経費明細に JPEG、PNG、PDF の領収書を 1 件 upload、replace、delete できる。 | 設計済み・未実装 |
| REQ-RCP-002 | 申請者は全 status の自分の領収書 metadata と content を preview/download できる。 | 設計済み・未実装 |
| REQ-RCP-003 | APPROVER / ADMIN は他人の `SUBMITTED` 申請を審査する場合に限り領収書を preview/download でき、ADMIN は管理対象の全申請を参照できる。 | 設計済み・未実装 |
| REQ-RCP-004 | Application は original file name を表示用 metadata として保持し、storage key は server で生成して API 利用者へ公開しない。 | 設計済み・未実装 |
| REQ-RCP-005 | 領収書の upload、replace、delete、preview、download を監査ログへ記録する。 | 設計済み・未実装 |
| REQ-FE-005 | Frontend は object key 手入力を file selection、validation、upload state、preview/download、replace/delete UI に置き換える。 | 設計済み・未実装 |

## 4. 非機能要件

| ID | 要件 | 内容 | 状態 |
|---|---|---|---|
| NFR-SEC-001 | 認証 | Spring Session JDBC と secure cookie により API を保護し、HTTP Basic を廃止する。 | 実装済み |
| NFR-SEC-002 | 認可 | ロールと申請者本人チェックにより操作を制御する。 | 実装済み |
| NFR-SEC-003 | Browser credential | Session cookie は `HttpOnly`、production `Secure`、`SameSite=Lax` とし、password/session ID を JavaScript storage、URL、log、analytics に保存しない。 | 実装済み |
| NFR-SEC-004 | CSRF / session | Unsafe method は CSRF token を必須とし、login 時に session ID を rotation、idle 30 分で session expiry とする。 | 実装済み |
| NFR-DB-001 | DB 管理 | Flyway により migration を管理する。 | 実装済み |
| NFR-TEST-001 | テスト | JUnit 5 / Mockito による単体テストと Testcontainers PostgreSQL による API 結合テストを実施する。 | 実装済み |
| NFR-OPS-001 | 実行環境 | Docker Compose により Java / PostgreSQL 開発環境を提供する。 | 実装済み |
| NFR-CI-001 | 継続的インテグレーション | pull request と `main` push で全自動テストと application package build を実行する。 | 設計・local 検証済み、remote 未設定 |
| NFR-OPS-002 | production container | multi-stage build と非 root user を利用した application 実行用 image を提供する。 | 実装済み |
| NFR-AWS-001 | AWS architecture | ALB、ECS Fargate、RDS PostgreSQL、S3 を利用した Multi-AZ production architecture を定義する。 | 設計済み・未構築 |
| NFR-AWS-002 | Network security | ECS と RDS を private subnet に配置し、security group で ALB、ECS、RDS 間の必要通信だけを許可する。 | 設計済み・未構築 |
| NFR-AWS-003 | Secret / IAM | DB credential を Secrets Manager で管理し、task execution role、task role、deployment role を最小権限で分離する。 | 設計済み・未構築 |
| NFR-AWS-004 | Monitoring / recovery | CloudWatch による log、metric、alarm と、RDS backup/restore、ECS rollback の方針を定義する。 | 設計済み・未構築 |
| NFR-FE-001 | Usability | 日本語 UI、responsive layout、loading、empty、error state を一貫して提供する。 | 実装済み |
| NFR-FE-002 | Accessibility | label、keyboard operation、focus management、色に依存しない状態表示を提供する。 | 実装済み |
| NFR-FE-003 | Frontend test | Unit、component、API mock integration、主要 workflow の E2E test を実施する。 | 実装済み |
| NFR-FILE-001 | File validation | 1 file 10 MiB 以下、JPEG/PNG/PDF の allowlist、extension/Content-Type/magic bytes 整合、SHA-256、malware scan を server で検証する。 | Backend/local 実装済み。Production scanner 運用は Phase 18 gate。 |
| NFR-FILE-002 | Private storage | Local/test filesystem と private S3 を storage adapter で分離し、public object、利用者指定 key、container local production data を使用しない。 | Application adapter 実装済み。実 bucket/IAM は Phase 17/18。 |
| NFR-FILE-003 | File access security | File content は認証・認可済み backend endpoint から streaming し、storage key/path/永続 URL を公開しない。 | Backend API 実装済み。Frontend は後続。 |
| NFR-FILE-004 | Consistency / recovery | DB と storage の非 atomic operation を state、cleanup、retry/reconciliation で回復し、replace 失敗時に旧 active file を維持する。 | V5 state/mapper、Service、cleanup/retry 実装済み。 |

## 5. 前提・制約

- React / TypeScript frontend と Spring Boot REST API は実装済みとする。
- Phase 14B まで採用した学習目的の HTTP Basic は Phase 15 で廃止し、Spring Session JDBC に移行済みである。
- Frontend と `/api` は same-origin で公開し、account self-registration、password reset、MFA、OIDC は Phase 15 対象外とする。
- Phase 16A の設計、Phase 16B の V5 metadata/storage/scanner、Phase 16C の reconciliation/Service、Phase 16D の HTTP API、Phase 16E の S3 adapter は完了した。Frontend UI は未実装であり、現行画面は object key のみを扱う。
- 領収書の法定・社内保存年限は未確定のため、Phase 16 では active file の自動期限削除を行わない。
- Production malware scanner の製品・運用は Phase 18 の deployment gate で確定し、未設定または障害時は file を active にしない。
- 監査ログは業務操作の追跡を目的とし、認証失敗や参照操作は現時点では記録対象外とする。
- AWS architecture と application の S3 adapter/API は実装済みだが、AWS resource、IaC、実 S3 接続、CI/CD deployment は未実装とする。
- GitHub Actions workflow は設計・local 検証済みだが、remote から削除した local-only file のため remote CI は稼働していない。
- 金額は 1 円以上 999999999999 円以下の整数とし、申請合計も 999999999999 円以下とする。
