# API仕様書

## 1. 共通仕様

本書は人間が読むための API 仕様である。前後端の機械可読な契約は [OpenAPI定義](openapi.yaml) に記載する。

### 1.1 Base URL

```text
http://localhost:8080
```

### 1.2 認証

- `/api/auth/login` と `/actuator/health` は認証不要。
- その他 API は HTTP Basic 認証が必要。

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
| 404 | `NOT_FOUND` | 対象データなし |
| 500 | `INTERNAL_SERVER_ERROR` | 未処理のシステムエラー |

内部例外のメッセージ、stack trace、DB 接続情報などはレスポンスに含めない。

## 2. 認証 API

### 2.1 ログイン確認

| 項目 | 内容 |
|---|---|
| Method | POST |
| Path | `/api/auth/login` |
| 認証 | 不要 |
| 概要 | メールアドレスとパスワードを検証し、認証方式とユーザー情報を返す。 |

Request:

| 項目 | 型 | 必須 | 制約 |
|---|---|---|---|
| `email` | string | YES | email, max 255 |
| `password` | string | YES | max 100 |

### 2.2 ログインユーザー取得

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/api/auth/me` |
| 認証 | 必要 |
| 概要 | 認証済みユーザー情報を返す。 |

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
| `items[].receiptObjectKey` | string | NO | max 500 |

### 3.4 更新

| 項目 | 内容 |
|---|---|
| Method | PUT |
| Path | `/api/expense-applications/{id}` |
| 認証 | 必要 |
| 概要 | `DRAFT` または `RETURNED` の経費申請を更新する。更新後は `DRAFT` に戻す。 |

Request は作成 API と同一。

### 3.5 削除

| 項目 | 内容 |
|---|---|
| Method | DELETE |
| Path | `/api/expense-applications/{id}` |
| 認証 | 必要 |
| 概要 | `DRAFT` または `RETURNED` の経費申請を削除する。 |

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

## 6. ヘルスチェック

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/actuator/health` |
| 認証 | 不要 |
| 概要 | Spring Boot Actuator のヘルスチェック。 |
