# 単体テスト仕様書

## 1. 目的

Service 層の業務ルールを JUnit 5 / Mockito で検証する。DB 接続は行わず、Mapper は mock とする。

## 2. テスト対象

| 対象クラス | 概要 |
|---|---|
| `ExpenseApplicationService` | 経費申請 CRUD、申請、承認、差戻しの業務ルール |
| `AuditLogService` | 監査ログ検索の権限チェック |
| `GlobalExceptionHandler` | Controller / Service 例外の共通 JSON 変換 |
| Spring Security error handler | Security Filter の 401 / 403 JSON 変換 |

## 3. テスト方針

| 観点 | 内容 |
|---|---|
| 正常系 | 想定される入力で正しいレスポンスと Mapper 呼び出しになること。 |
| 異常系 | 権限違反、ステータス不正、自己承認などで例外になること。 |
| 権限 | USER / APPROVER / ADMIN の操作可否を確認すること。 |
| 状態遷移 | `DRAFT`, `SUBMITTED`, `APPROVED`, `RETURNED` の遷移条件を確認すること。 |
| DB 更新 | 実 DB ではなく、Mapper の更新メソッド呼び出し有無で確認すること。 |

## 4. テストケース

| No | 要件ID | テスト名 | 前提 | 操作 | 期待結果 | 実装状況 |
|---|---|---|---|---|---|---|
| UT-EXP-001 | REQ-WF-001 | `submit_正常系_下書きを申請中にする` | 申請者本人、対象は `DRAFT` | `submit` を実行 | `SUBMITTED` を返し、`updateStatusToSubmitted` が呼ばれる | 実装済み |
| UT-EXP-002 | REQ-WF-002 | `approve_正常系_承認者が申請中を承認する` | APPROVER、対象は他人の `SUBMITTED` | `approve` を実行 | `APPROVED` を返し、`updateStatusToApproved` が呼ばれる | 実装済み |
| UT-EXP-003 | REQ-WF-004 | `approve_異常系_自分の申請は承認できない` | APPROVER、対象は自分の `SUBMITTED` | `approve` を実行 | `400 BAD_REQUEST` が発生し、更新処理は呼ばれない | 実装済み |
| UT-EXP-004 | REQ-WF-003 | `returnApplication_異常系_USERは差戻しできない` | USER、対象は他人の `SUBMITTED` | `returnApplication` を実行 | `403 FORBIDDEN` が発生し、更新処理は呼ばれない | 実装済み |
| UT-EXP-005 | REQ-EXP-004 | `update_正常系_下書きのヘッダと明細を更新する` | 申請者本人、対象は `DRAFT` | `update` を実行 | ヘッダと明細が更新される | 実装済み |
| UT-EXP-006 | REQ-EXP-004 | `update_異常系_申請中は更新できない` | 申請者本人、対象は `SUBMITTED` | `update` を実行 | `400 BAD_REQUEST` が発生する | 実装済み |
| UT-EXP-007 | REQ-EXP-003 | `getById_異常系_他人の申請は参照できない` | 対象は他人の申請 | `getById` を実行 | `403 FORBIDDEN` が発生する | 実装済み |
| UT-EXP-008 | REQ-WF-002 | `approve_異常系_下書きは承認できない` | APPROVER、対象は `DRAFT` | `approve` を実行 | `400 BAD_REQUEST` が発生する | 実装済み |
| UT-EXP-009 | REQ-WF-003 | `returnApplication_正常系_承認者が申請中を差戻す` | APPROVER、対象は他人の `SUBMITTED` | `returnApplication` を実行 | `RETURNED` を返し、差戻し理由が保存される | 実装済み |
| UT-ADM-001 | REQ-ADM-001 | `search_正常系_ADMINは全件検索できる` | ADMIN、検索条件に `applicantId` 指定なし | `search` を実行 | `applicantId` が強制設定されず検索される | 実装済み |
| UT-ADM-002 | REQ-ADM-001 | `getById_正常系_ADMINは他人の申請詳細を参照できる` | ADMIN、対象は他人の申請 | `getById` を実行 | 詳細レスポンスを返す | 実装済み |
| UT-REV-001 | REQ-REV-001 | `searchReviews_正常系_APPROVERは他人の申請中を検索できる` | APPROVER | Review Search を実行 | 申請中一覧と表示名を返す | 実装済み |
| UT-REV-002 | REQ-REV-001 | `searchReviews_異常系_USERは検索できない` | USER | Review Search を実行 | `403 FORBIDDEN` が発生する | 実装済み |
| UT-REV-003 | REQ-REV-002 | `getReviewById_正常系_APPROVERは他人の申請中詳細を参照できる` | APPROVER、他人の `SUBMITTED` | Review Detail を実行 | 詳細レスポンスを返す | 実装済み |
| UT-REV-004 | REQ-WF-004 | `getReviewById_異常系_自分の申請は参照できない` | APPROVER、自分の `SUBMITTED` | Review Detail を実行 | `400 BAD_REQUEST` が発生する | 実装済み |
| UT-AMT-001 | REQ-EXP-006 | `create_異常系_合計金額がDB上限を超える` | 明細合計が 999999999999 円超 | Create を実行 | `400 BAD_REQUEST` が発生し insert しない | 実装済み |
| UT-AUD-001 | REQ-AUD-001 | `submit_正常系_監査ログを登録する` | 申請者本人、対象は `DRAFT` | `submit` を実行 | `EXPENSE_APPLICATION_SUBMIT` の監査ログが登録される | 実装済み |
| UT-AUD-002 | REQ-AUD-002 | `search_正常系_ADMINは監査ログを検索できる` | ADMIN | 監査ログ `search` を実行 | 検索結果と件数を返す | 実装済み |
| UT-AUD-003 | REQ-AUD-002 | `search_異常系_USERは監査ログを検索できない` | USER | 監査ログ `search` を実行 | `403 FORBIDDEN` が発生する | 実装済み |
| CT-ERR-001 | Phase 8 | Validation error | 必須項目が空 | 作成 API を実行 | 400 と `VALIDATION_ERROR`、項目詳細を返す | 実装済み |
| CT-ERR-002 | Phase 8 | JSON 形式不正 | 不正な JSON | 作成 API を実行 | 400 と `INVALID_REQUEST` を返す | 実装済み |
| CT-ERR-003 | Phase 8 | 業務例外 | Service が 404 を送出 | 詳細 API を実行 | 404 と `NOT_FOUND`、業務メッセージを返す | 実装済み |
| CT-ERR-004 | Phase 8 | 未認証 | Authorization header なし | 保護 API を実行 | 401 と `UNAUTHORIZED` を返す | 実装済み |
| CT-ERR-005 | Phase 8 | 権限不足 | AccessDeniedException | Security handler を実行 | 403 と `FORBIDDEN` を返す | 実装済み |
| CT-ERR-006 | Phase 8 | 予期しない例外 | 未処理例外 | 詳細 API を実行 | 500 を返し、内部メッセージを公開しない | 実装済み |
| CT-CTRL-001 | Phase 10 | `login_正常系_認証情報とユーザーを返す` | 有効なメールアドレスとパスワード | Login API を実行 | Basic 認証種別とユーザー情報を返す | 実装済み |
| CT-CTRL-002 | Phase 10 | `create_正常系_作成した経費申請を返す` | 認証済み USER、有効な申請内容 | Create API を実行 | DRAFT の経費申請と合計金額を返す | 実装済み |
| CT-CTRL-003 | Phase 10 | `search_正常系_検索条件と監査ログ一覧を返す` | 認証済み ADMIN、有効な検索条件 | AuditLog Search API を実行 | ページングされた監査ログを返す | 実装済み |
| CT-AMT-001 | Phase 14A | `create_異常系_金額の小数は許可しない` | 小数金額 | Create API を実行 | 400 と amount の validation detail を返す | 実装済み |
| CT-REV-001 | Phase 14A | `search_正常系_承認待ち申請を返す` | 認証済み APPROVER | Review Search API を実行 | ページングされた承認待ち申請を返す | 実装済み |

## 5. 実行方法

```bash
docker compose run --rm java-dev ./gradlew test
```

## 6. 判定基準

- Gradle test が `BUILD SUCCESSFUL` で終了すること。
- 異常系では期待する HTTP status を持つ `ResponseStatusException` が発生すること。
- 更新してはいけないケースでは、対象 Mapper の更新メソッドが呼ばれないこと。
