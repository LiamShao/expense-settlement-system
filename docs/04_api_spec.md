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

エラーレスポンスの統一は Global Exception Handler 実装時に詳細化する。

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
| 概要 | 経費申請を検索する。USER は自分の申請のみ取得できる。 |

Query:

| 項目 | 型 | 必須 | 制約・概要 |
|---|---|---|---|
| `applicantId` | number | NO | USER は自分以外を指定不可。現実装ではログインユーザー ID に上書きされる。 |
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
| 概要 | 経費申請ヘッダと明細を取得する。USER は自分の申請のみ取得できる。 |

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
| `items[].amount` | number | YES | min 1 |
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

## 4. ヘルスチェック

| 項目 | 内容 |
|---|---|
| Method | GET |
| Path | `/actuator/health` |
| 認証 | 不要 |
| 概要 | Spring Boot Actuator のヘルスチェック。 |
