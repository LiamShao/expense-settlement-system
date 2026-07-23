# 単体テスト仕様書

## 1. 目的

Service 層の業務ルールを JUnit 5 / Mockito で検証する。DB 接続は行わず、Mapper は mock とする。

## 2. テスト対象

| 対象クラス | 概要 |
|---|---|
| `ExpenseApplicationService` | 経費申請 CRUD、申請、承認、差戻しの業務ルール |
| `AuditLogService` | 監査ログ検索の権限チェック |
| `AuthService` | DB user 認証、login failure count、temporary lock、login success reset |
| `GlobalExceptionHandler` | Controller / Service 例外の共通 JSON 変換 |
| Spring Security error handler | Security Filter の 401 / 403 / CSRF JSON 変換 |
| `ReceiptFileService`（Phase 16） | File validation、認可、metadata/state、storage/scan failure、audit |
| `LocalReceiptStorage` / `S3ReceiptStorage`（Phase 16） | Storage adapter contract、path/key、exact length streaming、cleanup。Local は Phase 16B、S3 は Phase 16E 実装済み。 |
| `MalwareScanner`（Phase 16） | Local EICAR pattern test adapter と未設定時の fail-closed behavior |

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
| CT-ERR-004 | Phase 8 / 15 | 未認証 | Session cookie なし | 保護 API を実行 | 401 と `UNAUTHORIZED` を返す | 実装済み |
| CT-ERR-005 | Phase 8 | 権限不足 | AccessDeniedException | Security handler を実行 | 403 と `FORBIDDEN` を返す | 実装済み |
| CT-ERR-006 | Phase 8 | 予期しない例外 | 未処理例外 | 詳細 API を実行 | 500 を返し、内部メッセージを公開しない | 実装済み |
| CT-CTRL-001 | Phase 10 / 15 | `login_正常系_認証情報とユーザーを返す` | 有効な CSRF token、メールアドレス、パスワード | Login API を実行 | Session 認証種別とユーザーを返し、SecurityContext を session に保存する | 実装済み |
| CT-CTRL-002 | Phase 10 | `create_正常系_作成した経費申請を返す` | 認証済み USER、有効な申請内容 | Create API を実行 | DRAFT の経費申請と合計金額を返す | 実装済み |
| CT-CTRL-003 | Phase 10 | `search_正常系_検索条件と監査ログ一覧を返す` | 認証済み ADMIN、有効な検索条件 | AuditLog Search API を実行 | ページングされた監査ログを返す | 実装済み |
| CT-AMT-001 | Phase 14A | `create_異常系_金額の小数は許可しない` | 小数金額 | Create API を実行 | 400 と amount の validation detail を返す | 実装済み |
| CT-REV-001 | Phase 14A | `search_正常系_承認待ち申請を返す` | 認証済み APPROVER | Review Search API を実行 | ページングされた承認待ち申請を返す | 実装済み |
| FE-PERM-001 | Phase 14B | `本人の各statusに対する操作を判定する` | USER / APPROVER / ADMIN と全 status | action matrix を評価 | DRAFT / RETURNED の本人だけ edit / delete / submit 可能 | 実装済み |
| FE-PERM-002 | Phase 14B | `他人の各statusに対する操作を判定する` | USER / APPROVER / ADMIN と全 status | action matrix を評価 | APPROVER / ADMIN だけ他人の SUBMITTED を approve / return 可能 | 実装済み |
| FE-AUTH-001 | Phase 14B / 15 | `ログイン後にsession認証で申請一覧を取得する` | MSW csrf / login / me / list response | Login form を送信 | Same-origin cookie mode を利用し、credential を browser storage に保存しない | 実装済み |
| FE-AUTH-002 | Phase 14B | `ログアウトは確認後に実行し、キャンセル時はセッションを維持する` | Login 済み USER | logout dialog をキャンセル後、再度開いて確認 | キャンセル時は申請一覧を維持し、確認時だけ `/login` へ遷移する | 実装済み |
| FE-FORM-001 | Phase 14B | `未保存の新規申請で必須項目を検証する` | Login 済み USER | 空の申請 form を保存 | 件名、利用日、金額、内容の field error を表示 | 実装済み |
| UT-AUTH-001 | Phase 15 | `authenticate_正常系_failure状態をresetする` | 有効 user、正しい password | Login を実行 | Authentication を返し、failure count/lock を reset、last login を更新 | 実装済み |
| UT-AUTH-002 | Phase 15 | `authenticate_異常系_password不一致を記録する` | 有効・unlocked user、誤 password | Login を実行 | 401、failure count を atomic increment | 実装済み |
| CT-CSRF-001 | Phase 15 | `unsafeRequest_異常系_CSRF不足を統一形式で返す` | Login 済み session、token なし | POST API を実行 | 403 と `CSRF_INVALID` を返す | 実装済み |
| FE-AUTH-003 | Phase 15 | `reload時にsessionを復元する` | `/api/auth/me` が user を返す | Protected route を直接表示 | Login form を経由せず業務画面を表示 | 実装済み |
| FE-AUTH-004 | Phase 15 | `unsafeRequestにCSRF headerを付与する` | CSRF endpoint が token を返す | Login / logout / mutation を実行 | 指定 header と same-origin cookie mode を利用する | 実装済み |
| FE-AUTH-005 | Phase 15 | `logout成功後だけlocal stateを破棄する` | Login 済み USER | Confirm logout | Server logout 成功後に user/query/CSRF state を破棄する | 実装済み |
| UT-RCP-001 | REQ-RCP-001 | `uploadOrReplace_正常系_新規PDFをACTIVEにして監査する` | Owner、`DRAFT`、valid PDF | Upload | Metadata/state を active にし `RECEIPT_UPLOAD` を記録 | 実装済み |
| UT-RCP-002 | REQ-RCP-001 | `uploadOrReplace_異常系_EICAR判定ならREJECTEDを回収して422` | Existing active receipt、infected new file | Replace | New file を active にせず旧 file を維持 | 実装済み |
| UT-RCP-003 | REQ-RCP-003 | `openContent_異常系_権限外USERはstorageとauditへ到達しない` | 他人の非公開申請 | Content 取得 | 403、storage open と audit を実行しない | 実装済み |
| UT-RCP-004 | NFR-FILE-001 | `validate_異常系_magicBytesが一致しない` | PDF extension/type、不正 bytes | Upload | 400、object/active metadata なし | 実装済み |
| UT-RCP-005 | NFR-FILE-004 | `cleanupNonActive_異常系_object削除失敗時はmetadataを保持する` | `PENDING_DELETE`、storage delete failure | Cleanup | Metadata を retry 用に維持 | 実装済み |
| UT-RCP-006 | NFR-FILE-003 | `uploadOrReplace_正常系_新規PDFをACTIVEにして監査する` | Active receipt response | Metadata 変換 | Storage key/path のない public metadata のみ返す | 実装済み |
| CT-RCP-001 | REQ-RCP-001 | `uploadOrReplace_正常系_CSRF付きmultipartをServiceへ渡す` | Authenticated owner、CSRF、PDF part | PUT | Public metadata と success message を返す | 実装済み |
| CT-RCP-002 | REQ-RCP-002 | `getContent_正常系_inlineでsecurityHeaderとbinaryを返す` | Authorized content | GET inline | Binary、type、filename、nosniff、private/no-store を返す | 実装済み |
| CT-RCP-003 | NFR-FILE-001 | `uploadOrReplace_異常系_malwareは専用codeを返す` | Service malware error | PUT | 422 `MALWARE_DETECTED` | 実装済み |
| CT-RCP-004 | NFR-SEC-002 | `uploadOrReplace_異常系_CSRF不足を拒否する` | Authenticated、CSRF なし | PUT | 403 `CSRF_INVALID`、Service 未呼出し | 実装済み |
| CT-RCP-005 | NFR-FILE-001 | `uploadOrReplace_異常系_multipart以外は415を返す` | JSON Content-Type | PUT | 415 `UNSUPPORTED_MEDIA_TYPE`、Service 未呼出し | 実装済み |
| CT-RCP-006 | NFR-FILE-004 | `uploadOrReplace_異常系_fileService停止は503専用codeを返す` | Storage/scanner unavailable | PUT | 503 `FILE_SERVICE_UNAVAILABLE` | 実装済み |
| UT-STO-001 | NFR-FILE-002 | `localStorage_異常系_root外pathを拒否する` | Traversal / absolute / backslash key | Put/open | Root 外へ access しない | 実装済み |
| UT-STO-002 | NFR-FILE-002 | `s3Storage_正常系_private設定でstreamする` | Generated key と stream | Put/open/delete | 固定 bucket/prefix、exact length、SSE-S3、上書き防止の SDK request | 実装済み |
| UT-STO-003 | NFR-FILE-002 | `localStorage_正常系_putOpenDeleteをstreamする` | Temporary root と generated key | Put/open/delete | Bytes 一致、delete idempotent、missing open を拒否 | 実装済み |
| UT-STO-004 | NFR-FILE-004 | `localStorage_異常系_write失敗をcleanupする` | 途中で IOException となる stream | Put | Final/temporary file を残さない | 実装済み |
| UT-STO-005 | NFR-FILE-002 | `localStorage_異常系_symlinkとoverwriteを拒否する` | Existing object / external symlink | Put/open | Existing bytes を維持し root 外を読まない | 実装済み |
| UT-STO-006 | NFR-FILE-004 | `s3Storage_異常系_errorを安全に変換する` | SDK 404 / client failure | Open/head | Not found を区別し、bucket/key/SDK detail を公開しない | 実装済み |
| UT-STO-007 | NFR-FILE-002 | `s3Storage_異常系_invalidInputをSDK前に拒否する` | Traversal key / invalid length/type | Put | S3 client を呼ばず拒否 | 実装済み |
| UT-SCN-001 | NFR-FILE-001 | `scanner_正常系_EICARpatternを検出する` | Clean content / embedded EICAR | Scan | `CLEAN` / `INFECTED` を判定 | 実装済み |
| UT-SCN-002 | NFR-FILE-001 | `scanner_異常系_unavailableをfailClosedにする` | 未設定または stream failure | Scan | Unavailable exception とし clean 扱いしない | 実装済み |
| FE-RCP-001 | REQ-FE-005 | `領収書選択時にsizeとtypeを検証する` | Edit form | Unsupported/10 MiB 超 file 選択 | Field error、upload request なし | 設計済み・未実装 |
| FE-RCP-002 | REQ-RCP-002 | `previewを閉じたときobjectURLを破棄する` | Active image/PDF | Preview open/close | Blob 表示後に URL revoke | 設計済み・未実装 |

## 5. 実行方法

```bash
docker compose run --rm java-dev ./gradlew test
cd frontend
pnpm test
```

## 6. 判定基準

- Gradle test が `BUILD SUCCESSFUL` で終了すること。
- 異常系では期待する HTTP status を持つ `ResponseStatusException` が発生すること。
- 更新してはいけないケースでは、対象 Mapper の更新メソッドが呼ばれないこと。
