# API仕様書

## 1. 共通仕様

本書は人間が読むための API 仕様である。前後端の機械可読な契約は [OpenAPI定義](openapi.yaml) に記載する。

### 1.1 Base URL

```text
http://localhost:8080
```

### 1.2 認証

- `/api/auth/csrf`、`/api/auth/login` と `/actuator/health` は事前 login 不要。
- Login 成功時に opaque な `SESSION` cookie を発行し、その他 API は有効な server-side session を必要とする。
- `POST` / `PUT` / `PATCH` / `DELETE` は `/api/auth/csrf` が返す token を指定された header（default `X-CSRF-TOKEN`）で送信する。Login と logout も対象とする。
- Cookie は `HttpOnly`、`SameSite=Lax`、production `Secure` とし、frontend と API は same-origin とする。
- Session idle timeout は 30 分。Login 時に session ID を rotation し、logout 時に current session と cookie を無効化する。

### 1.3 共通レスポンス

正常時は `ApiResponse<T>` 形式で返却する。

```json
{
  "success": true,
  "data": {},
  "message": "処理メッセージ",
  "timestamp": "2026-07-09T10:00:00"
}
```

エラー時は次の共通形式で返却する。`details` は入力項目ごとの Validation error がある場合のみ返却する。

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "入力内容に誤りがあります。",
  "details": [
    {
      "field": "title",
      "message": "空白は許可されていません"
    }
  ],
  "path": "/api/expense-applications",
  "timestamp": "2026-07-12T10:00:00"
}
```

主要エラーコード:

| HTTP status | code | 発生条件 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation 違反 |
| 400 | `INVALID_REQUEST` | JSON、型、列挙値などの形式不正 |
| 400 | `BAD_REQUEST` | 業務ルール違反 |
| 401 | `UNAUTHORIZED` | 未認証、認証失敗 |
| 403 | `FORBIDDEN` | 権限不足 |
| 403 | `CSRF_INVALID` | CSRF token 不足、不一致、期限切れ |
| 404 | `NOT_FOUND` | 対象データなし |
| 409 | `CONFLICT` | Concurrent update または file state 競合 |
| 413 | `FILE_TOO_LARGE` | Upload file が 10 MiB を超過 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | JPEG、PNG、PDF 以外の file |
| 422 | `MALWARE_DETECTED` | Malware scanner が危険な file と判定 |
| 503 | `FILE_SERVICE_UNAVAILABLE` | Private storage または scanner の一時障害 |
| 500 | `INTERNAL_SERVER_ERROR` | 未処理のシステムエラー |

内部例外のメッセージ、stack trace、DB 接続情報などはレスポンスに含めない。

## 2. 認証 API

### 2.1 CSRF token 取得

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/auth/csrf` |
| 認証 | 不要 |
| 概要 | Current session に紐づく CSRF token、header name、parameter name を返す。 |

Response data:

| 項目 | 型 | 概要 |
|---|---|---|
| `headerName` | string | Unsafe request で token を設定する header。 |
| `parameterName` | string | Form submit 用 parameter name。 |
| `token` | string | JavaScript memory のみに保持する CSRF token。 |

### 2.2 ログイン

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/auth/login` |
| 認証 | 不要 |
| CSRF | 必要 |
| 概要 | メールアドレスとパスワードを検証し、server-side session を開始して認証方式 `Session` とユーザー情報を返す。 |

Request:

| 項目 | 型 | 必須 | 制約 |
|---|---|---|---|
| `email` | string | YES | email, max 255 |
| `password` | string | YES | max 100 |

連続 5 回失敗した account は 15 分 lock する。存在しない、password 不一致、disabled、locked の response はいずれも 401 の一般化された message とする。

### 2.3 ログインユーザー取得

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/auth/me` |
| 認証 | 必要 |
| 概要 | 認証済みユーザー情報を返す。 |

### 2.4 ログアウト

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/auth/logout` |
| 認証 | 任意。Current authenticated session がある場合は無効化する。 |
| CSRF | 必要 |
| 概要 | Current session を server-side で無効化し、`SESSION` cookie を削除する。Session が既に失効していても成功する idempotent operation とする。 |

## 3. 経費申請 API

### 3.1 一覧検索

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/expense-applications` |
| 認証 | 必要 |
| 概要 | 経費申請を検索する。USER / APPROVER は自分の申請のみ取得できる。ADMIN は全ユーザーの申請を取得できる。 |

Query:

| 項目 | 型 | 必須 | 制約・概要 |
|---|---|---|---|
| `applicantId` | number | NO | USER / APPROVER は自分以外を指定不可。ADMIN は指定なしで全件、指定ありで対象ユーザーのみ検索する。 |
| `status` | string | NO | `DRAFT` / `SUBMITTED` / `APPROVED` / `RETURNED` |
| `keyword` | string | NO | max 200。タイトル部分一致。 |
| `expenseDateFrom` | date | NO | 明細利用日 From |
| `expenseDateTo` | date | NO | 明細利用日 To |
| `page` | number | NO | min 0, default 0 |
| `size` | number | NO | min 1, max 100, default 20 |

### 3.2 詳細取得

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/expense-applications/{id}` |
| 認証 | 必要 |
| 概要 | 経費申請ヘッダと明細を取得する。USER / APPROVER は自分の申請のみ取得できる。ADMIN は全ユーザーの申請を取得できる。 |

### 3.3 作成

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/expense-applications` |
| 認証 | 必要 |
| 概要 | 経費申請を `DRAFT` として作成する。 |

Request:

| 項目 | 型 | 必須 | 制約 |
|---|---|---|---|
| `title` | string | YES | not blank, max 200 |
| `items` | array | YES | not empty |
| `items[].expenseDate` | date | YES | 利用日 |
| `items[].category` | string | YES | `TRANSPORTATION` / `MEAL` / `SUPPLIES` / `ACCOMMODATION` / `OTHER` |
| `items[].amount` | integer | YES | min 1, max 999999999999。整数円のみ。 |
| `items[].description` | string | YES | not blank, max 500 |

領収書は申請/明細作成後、Phase 16 の receipt endpoint から upload する。Phase 16 実装時に `receiptObjectKey` の client input は廃止し、server-generated storage key を JSON request で受け付けない。

### 3.4 更新

| 項目 | 内容 |
|---|---|
| Method | PUT |
| Path | `/api/expense-applications/{id}` |
| 認証 | 必要 |
| 概要 | `DRAFT` または `RETURNED` の経費申請を更新する。更新後は `DRAFT` に戻す。 |

Request の item field は作成 API と同じで、既存明細には optional `items[].id` を付ける。既存 ID は同一 application に属し、request 内で重複してはならない。ID なしは新規 insert、既存 request から省略された ID は削除とする。Receipt metadata が残る明細の削除は `409 CONFLICT` とし、先に receipt delete/cleanup を完了する。

### 3.5 削除

| 項目 | 内容 |
|---|---|
| Method | DELETE |
| Path | `/api/expense-applications/{id}` |
| 認証 | 必要 |
| 概要 | `DRAFT` または `RETURNED` の経費申請を削除する。 |

Receipt metadata が残る application は `409 CONFLICT` とし、先に receipt delete/cleanup を完了する。

### 3.6 申請

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/expense-applications/{id}/submit` |
| 認証 | 必要 |
| 概要 | `DRAFT` または `RETURNED` の経費申請を `SUBMITTED` に変更する。 |

### 3.7 承認

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/expense-applications/{id}/approve` |
| 認証 | 必要 |
| 概要 | `APPROVER` / `ADMIN` が `SUBMITTED` の経費申請を `APPROVED` に変更する。 |

制約:

- 自分の申請は承認できない。
- `SUBMITTED` 以外は承認できない。

### 3.8 差戻し

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/expense-applications/{id}/return` |
| 認証 | 必要 |
| 概要 | `APPROVER` / `ADMIN` が `SUBMITTED` の経費申請を `RETURNED` に変更する。 |

Request:

| 項目 | 型 | 必須 | 制約 |
|---|---|---|---|
| `returnReason` | string | YES | not blank, max 1000 |

制約:

- 自分の申請は差戻しできない。
- `SUBMITTED` 以外は差戻しできない。

作成・更新とも、明細合計は `999999999999` 円以下とする。合計金額は backend が算出する。

## 4. 承認待ち API

### 4.1 承認待ち一覧検索

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/reviews` |
| 認証 | 必要 |
| 概要 | APPROVER / ADMIN が他人の `SUBMITTED` 申請を検索する。自己申請は結果に含めない。 |

Query:

| 項目 | 型 | 必須 | 制約・概要 |
|---|---|---|---|
| `applicantId` | number | NO | 申請者 ID |
| `keyword` | string | NO | max 200。タイトル部分一致。 |
| `expenseDateFrom` | date | NO | 明細利用日 From |
| `expenseDateTo` | date | NO | 明細利用日 To |
| `page` | number | NO | min 0, default 0 |
| `size` | number | NO | min 1, max 100, default 20 |

### 4.2 承認待ち詳細取得

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/reviews/{id}` |
| 認証 | 必要 |
| 概要 | APPROVER / ADMIN が他人の `SUBMITTED` 申請ヘッダと明細を取得する。 |

- USER は利用できない。
- 自己申請および `SUBMITTED` 以外の申請は取得できない。
- 承認・差戻し自体は既存 workflow endpoint を利用する。

## 5. 監査ログ API

### 5.1 監査ログ検索

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/audit-logs` |
| 認証 | 必要 |
| 概要 | 監査ログを検索する。ADMIN のみ利用可能。 |

Query:

| 項目 | 型 | 必須 | 制約・概要 |
|---|---|---|---|
| `userId` | number | NO | 操作ユーザー ID |
| `action` | string | NO | max 100。操作種別完全一致。 |
| `targetType` | string | NO | max 100。対象種別完全一致。 |
| `createdDateFrom` | date | NO | ログ作成日 From |
| `createdDateTo` | date | NO | ログ作成日 To |
| `page` | number | NO | min 0, default 0 |
| `size` | number | NO | min 1, max 100, default 20 |

監査ログ対象操作:

| action | targetType | 概要 |
|---|---|---|
| `EXPENSE_APPLICATION_CREATE` | `EXPENSE_APPLICATION` | 経費申請作成 |
| `EXPENSE_APPLICATION_UPDATE` | `EXPENSE_APPLICATION` | 経費申請更新 |
| `EXPENSE_APPLICATION_DELETE` | `EXPENSE_APPLICATION` | 経費申請削除 |
| `EXPENSE_APPLICATION_SUBMIT` | `EXPENSE_APPLICATION` | 経費申請申請 |
| `EXPENSE_APPLICATION_APPROVE` | `EXPENSE_APPLICATION` | 経費申請承認 |
| `EXPENSE_APPLICATION_RETURN` | `EXPENSE_APPLICATION` | 経費申請差戻し |
| `RECEIPT_UPLOAD` | `RECEIPT_FILE` | 領収書初回 upload |
| `RECEIPT_REPLACE` | `RECEIPT_FILE` | 領収書 replace |
| `RECEIPT_DELETE` | `RECEIPT_FILE` | 領収書 delete |
| `RECEIPT_PREVIEW` | `RECEIPT_FILE` | 領収書 inline preview |
| `RECEIPT_DOWNLOAD` | `RECEIPT_FILE` | 領収書 attachment download |

Phase 16D で receipt multipart/metadata/content/delete Controller、専用 file error code、binary response header を実装済みである。Audit detail に original file name、storage key、checksum、session ID、binary content を含めない。

## 6. 領収書ファイル API（Phase 16D 実装済み）

共通 base path:

```text
/api/expense-applications/{applicationId}/items/{itemId}/receipt
```

`applicationId` と `itemId` の親子関係を backend で検証する。Item ID だけで file を認可しない。

### 6.1 Metadata 取得

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/expense-applications/{applicationId}/items/{itemId}/receipt` |
| 認証 | 必要 |
| 概要 | Active receipt の公開可能 metadata を返す。Storage key/path は返さない。 |

申請者本人は全 status、APPROVER / ADMIN は他人の `SUBMITTED` review、ADMIN は管理参照可能な全申請で取得できる。

### 6.2 Upload / replace

| 項目 | 内容 |
|---|---|
| Method | PUT |
| Path | `/api/expense-applications/{applicationId}/items/{itemId}/receipt` |
| 認証 | 必要 |
| CSRF | 必要 |
| Content-Type | `multipart/form-data` |
| 概要 | `file` part を upload し、既存 active receipt がある場合は安全に replace する。 |

Request:

| Part | 型 | 必須 | 制約 |
|---|---|---|---|
| `file` | binary | YES | JPEG / PNG / PDF、1～10,485,760 bytes |

申請者本人かつ application が `DRAFT` / `RETURNED` の場合だけ許可する。New file が検査/scanを通過するまで旧 active file を維持する。

### 6.3 Delete

| 項目 | 内容 |
|---|---|
| Method | DELETE |
| Path | `/api/expense-applications/{applicationId}/items/{itemId}/receipt` |
| 認証 | 必要 |
| CSRF | 必要 |
| 概要 | Active receipt を利用者から参照不可にし、private object cleanup を登録する。 |

申請者本人かつ application が `DRAFT` / `RETURNED` の場合だけ許可する。

### 6.4 Preview / download

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/expense-applications/{applicationId}/items/{itemId}/receipt/content` |
| 認証 | 必要 |
| Response | 検証済み `image/jpeg` / `image/png` / `application/pdf` binary |
| 概要 | 認可後に private storage から file content を streaming する。 |

Query:

| 項目 | 型 | 必須 | 制約・概要 |
|---|---|---|---|
| `disposition` | string | NO | `inline` / `attachment`。default `inline`。 |

Response header は sanitized `Content-Disposition`、`X-Content-Type-Options: nosniff`、`Cache-Control: private, no-store` を含む。Binary response は `ApiResponse` wrapper を使用しない。

### 6.5 Receipt metadata response

| 項目 | 型 | 概要 |
|---|---|---|
| `id` | integer(int64) | Receipt metadata ID |
| `originalFileName` | string | Sanitize 済み表示名 |
| `contentType` | string | 検証済み Content-Type |
| `sizeBytes` | integer(int64) | File size |
| `sha256Checksum` | string | Lowercase SHA-256 hex |
| `uploadedAt` | datetime | Upload 完了日時 |
| `previewAvailable` | boolean | Inline preview 可否 |

詳細な file validation、storage state、cleanup、frontend、test 方針は [領収書ファイル設計書](17_receipt_file_design.md) を参照する。

## 7. ヘルスチェック

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/actuator/health` |
| 認証 | 不要 |
| 概要 | Spring Boot Actuator のヘルスチェック。 |
