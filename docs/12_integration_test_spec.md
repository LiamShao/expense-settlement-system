# API 結合テスト仕様書

## 1. 目的

Spring Boot の API から PostgreSQL までを接続し、Controller、Spring Security、Service、MyBatis Mapper、Flyway migration の連携を確認する。

Service や Mapper を mock に置き換えず、実際の HTTP リクエストと DB 更新結果を検証する。

## 2. テスト範囲

```text
MockMvc
  ↓
Spring Security / Controller
  ↓
Service
  ↓
MyBatis Mapper / XML SQL
  ↓
PostgreSQL Testcontainer
```

対象に含めるもの:

- Flyway による schema 作成と初期データ投入
- DB ユーザーを利用した HTTP Basic 認証
- Auth、ExpenseApplication、AuditLog の主要 endpoint
- 経費申請の登録、検索、状態遷移
- ロールおよび申請者本人による認可
- 業務操作に伴う監査ログ登録

対象外:

- ブラウザ、Swagger UI、Prism Mock Server
- 負荷、性能、長時間稼働試験
- AWS など外部環境へのデプロイ確認

## 3. 実行環境

| 項目 | 内容 |
|---|---|
| Test framework | JUnit 5 / Spring Boot Test / MockMvc |
| Database | Testcontainers が起動する PostgreSQL 16 |
| DB initialization | Flyway `V1`～最新 migration |
| Authentication | seed user による HTTP Basic 認証 |
| Isolation | 各テストを transaction rollback して初期状態へ戻す |

Docker daemon を利用できない環境では結合テストを実行できないため、CI でも Docker を利用可能にする。

## 4. テストデータ

Flyway seed data の次のユーザーを利用する。

| Role | Email | Password | User ID |
|---|---|---|---|
| USER | `user@example.com` | `Password123!` | 1 |
| APPROVER | `approver@example.com` | `Password123!` | 2 |
| ADMIN | `admin@example.com` | `Password123!` | 3 |

初期経費申請 ID `1` は USER の `DRAFT` データとして利用する。

## 5. テストケース

| No | 対象 | テスト内容 | 期待結果 |
|---|---|---|---|
| IT-AUTH-001 | `POST /api/auth/login` | seed user のメールアドレスとパスワードでログインする | 200、認証方式 `Basic`、DB のユーザー情報を返す |
| IT-AUTH-002 | `GET /api/auth/me` | HTTP Basic 認証で現在ユーザーを取得する | 200、認証した USER の情報を返す |
| IT-EXP-001 | `POST /api/expense-applications` | 明細を含む経費申請を作成する | DB に `DRAFT` と明細合計金額を保存し、作成監査ログを登録する |
| IT-EXP-002 | `GET /api/expense-applications` | USER と ADMIN が一覧検索する | USER は自分の申請だけ、ADMIN は全ユーザーの申請を取得できる |
| IT-EXP-003 | submit / approve | USER が作成・申請し、APPROVER が承認する | `DRAFT` → `SUBMITTED` → `APPROVED` と遷移し、承認者を保存する |
| IT-EXP-004 | `GET /api/expense-applications/{id}` | USER が他人の申請を参照する | 403 と共通エラーレスポンスを返す |
| IT-AUD-001 | `GET /api/audit-logs` | ADMIN が作成・申請・承認ログを検索する | 対象申請の監査ログを DB から取得できる |
| IT-AUD-002 | `GET /api/audit-logs` | USER が監査ログを検索する | 403 と共通エラーレスポンスを返す |

## 6. 判定基準

- Testcontainer の PostgreSQL に Flyway migration が正常適用されること。
- 全結合テストが成功し、テスト間でデータが干渉しないこと。
- HTTP status、JSON response、DB 永続化結果が期待値と一致すること。
- 失敗時に Spring Boot、SQL、container のログから原因を追跡できること。

## 7. 実行方法

```bash
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

上記は Docker Desktop で開発コンテナから Testcontainers を実行する場合のコマンドである。`--user 1000:0` は workspace のファイル所有者を維持しながら Docker socket へ接続するために指定する。

ホストに Java 17 と Docker がある場合は次でも実行できる。

```bash
./gradlew test
```
