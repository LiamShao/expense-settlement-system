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

## 4. 非機能要件

| ID | 要件 | 内容 | 状態 |
|---|---|---|---|
| NFR-SEC-001 | 認証 | HTTP Basic 認証により API を保護する。 | 実装済み |
| NFR-SEC-002 | 認可 | ロールと申請者本人チェックにより操作を制御する。 | 実装済み |
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

## 5. 前提・制約

- React / TypeScript frontend と Spring Boot REST API は実装済みとする。
- 認証方式は学習目的として HTTP Basic を採用する。
- 領収書ファイルの実体管理は未実装であり、DB には object key のみ保持する。
- 監査ログは業務操作の追跡を目的とし、認証失敗や参照操作は現時点では記録対象外とする。
- AWS architecture は設計のみ完了しており、AWS resource、IaC、S3 file API、CI/CD deployment は未実装とする。
- GitHub Actions workflow は設計・local 検証済みだが、remote から削除した local-only file のため remote CI は稼働していない。
- 金額は 1 円以上 999999999999 円以下の整数とし、申請合計も 999999999999 円以下とする。
