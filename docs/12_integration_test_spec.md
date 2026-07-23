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
- DB ユーザー、Spring Session JDBC、secure cookie、CSRF を利用した認証
- Auth、ExpenseApplication、Review、AuditLog の主要 endpoint
- 経費申請の登録、検索、状態遷移
- ロールおよび申請者本人による認可
- 業務操作に伴う監査ログ登録
- Phase 16 実装後の領収書 multipart upload、metadata、binary content、private local storage、監査

対象外:

- ブラウザ、Swagger UI、Prism Mock Server
- 負荷、性能、長時間稼働試験
- AWS など外部環境へのデプロイ確認
- 実 AWS S3 と production malware scanner（adapter contract/failure は local test 対象）

## 3. 実行環境

| 項目 | 内容 |
|---|---|
| Test framework | JUnit 5 / Spring Boot Test / MockMvc |
| Database | Testcontainers が起動する PostgreSQL 16 |
| DB initialization | Flyway `V1`～最新 migration |
| Authentication | seed user による Session cookie 認証。Unsafe method は CSRF header 必須。 |
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
| IT-AUTH-001 | `GET /api/auth/csrf` → `POST /api/auth/login` | CSRF token と seed user credential でログインする | 200、認証方式 `Session`、DB user、`HttpOnly` / `SameSite` cookie を返す |
| IT-AUTH-002 | `GET /api/auth/me` | Login response の Session cookie で現在ユーザーを取得する | 200、認証した USER の情報を返し、Basic header は受け付けない |
| IT-AUTH-003 | Session fixation | CSRF 取得前 session ID で login する | Login 成功時に session ID が変わり、security context と CSRF state を新 session へ引き継ぐ |
| IT-AUTH-004 | `POST /api/auth/logout` | Login 済み cookie と CSRF token で logout する | Current session を削除し、旧 cookie の再利用は 401 |
| IT-AUTH-005 | Login failure lock | 同一 account で password を連続 5 回誤る | failure count と 15 分 lock を DB に保存し、正しい password でも lock 中は 401 |
| IT-AUTH-006 | CSRF protection | Session cookie 付き unsafe request で token を省略する | 403 と `CSRF_INVALID`、業務更新なし |
| IT-EXP-001 | `POST /api/expense-applications` | 明細を含む経費申請を作成する | DB に `DRAFT` と明細合計金額を保存し、作成監査ログを登録する |
| IT-EXP-002 | `GET /api/expense-applications` | USER と ADMIN が一覧検索する | USER は自分の申請だけ、ADMIN は全ユーザーの申請を取得できる |
| IT-EXP-003 | submit / approve | USER が作成・申請し、APPROVER が承認する | `DRAFT` → `SUBMITTED` → `APPROVED` と遷移し、承認者を保存する |
| IT-EXP-004 | `GET /api/expense-applications/{id}` | USER が他人の申請を参照する | 403 と共通エラーレスポンスを返す |
| IT-REV-001 | `GET /api/reviews`, `/api/reviews/{id}` | APPROVER と USER が承認待ちを参照する | APPROVER は他人の `SUBMITTED` だけを一覧・詳細参照でき、自己申請は 400、USER は 403 となる |
| IT-AUD-001 | `GET /api/audit-logs` | ADMIN が作成・申請・承認ログを検索する | 対象申請の監査ログを DB から取得できる |
| IT-AUD-002 | `GET /api/audit-logs` | USER が監査ログを検索する | 403 と共通エラーレスポンスを返す |
| IT-RCP-000 | Receipt persistence foundation | Flyway V5 適用後、mapper で `UPLOADING` → `PENDING_SCAN` → `ACTIVE` → `PENDING_DELETE` を操作する | Metadata、active lookup、stale lookup、delete が PostgreSQL 制約と一致する |
| IT-RCP-SVC-001 | Receipt service foundation | 実 PostgreSQL/local storage で service の upload → replace → delete を実行する | Active switch、旧 object/metadata cleanup、audit が一致する |
| IT-EXP-005 | Expense item reconciliation | Update JSON で既存 item ID、新規 item、削除 item を送る | 既存 ID を維持し update/insert/delete を差分反映する |
| IT-RCP-001 | Receipt upload / metadata | Owner が CSRF 付き multipart で valid JPEG を upload する | 200、metadata/DB/object/audit が一致し storage key は response にない |
| IT-RCP-002 | Receipt validation | 10 MiB boundary、超過、unsupported type、signature mismatch、EICAR pattern を upload する | Boundary は成功、各異常は 400/413/415/422 で active file なし |
| IT-RCP-003 | Receipt authorization | USER/APPROVER/ADMIN が owner/review/admin path で metadata/content を取得する | 設計した role/ownership/status matrix と一致し、拒否時 storage を返さない |
| IT-RCP-004 | Receipt content | `inline` / `attachment` で JPEG/PNG/PDF を取得する | Binary body、検証済み type、Content-Disposition、`nosniff`、`private, no-store` |
| IT-RCP-005 | Receipt replace/delete | Editable owner が replace/delete し、submitted application で再試行する | Replace は旧 file を安全に切替、delete cleanup/audit、submitted は 403 |
| IT-RCP-006 | Receipt failure/recovery | Storage/scan/DB/delete failure と concurrent replace を発生させる | 旧 active 保護、stale state/retry、409/503、orphan を reconciliation 可能 |

`IT-RCP-000` は Phase 16B、`IT-RCP-SVC-001` と `IT-EXP-005` は Phase 16C で実装済みである。Phase 16D では `IT-RCP-001`～`005` の HTTP 契約を実装し、PDF binary/inline、attachment Controller、10 MiB boundary、validation、EICAR、authorization、replace/delete を確認した。JPEG/PNG binary matrix と `IT-RCP-006` の concurrent/DB failure API test は後続 regression で補完する。

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
