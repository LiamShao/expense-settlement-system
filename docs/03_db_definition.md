# DB定義書

## 1. 概要

DB は PostgreSQL を利用する。スキーマ管理は Flyway migration で行う。

## 2. テーブル一覧

| テーブル | 概要 |
|---|---|
| `users` | ユーザー、社員番号、メールアドレス、ロールを管理する。 |
| `expense_applications` | 経費申請ヘッダを管理する。 |
| `expense_items` | 経費申請明細を管理する。 |
| `audit_logs` | 経費申請の作成・更新・削除・申請・承認・差戻しの操作ログを管理する。 |

## 3. users

| カラム | 型 | 必須 | 概要 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `employee_code` | VARCHAR(20) | YES | 社員番号 |
| `name` | VARCHAR(100) | YES | 氏名 |
| `email` | VARCHAR(255) | YES | メールアドレス |
| `password` | VARCHAR(255) | YES | BCrypt ハッシュ化パスワード |
| `role` | VARCHAR(20) | YES | `USER` / `APPROVER` / `ADMIN` |
| `department` | VARCHAR(100) | YES | 部署 |
| `enabled` | BOOLEAN | YES | 有効フラグ |
| `created_at` | TIMESTAMP | YES | 作成日時 |
| `updated_at` | TIMESTAMP | YES | 更新日時 |

## 4. expense_applications

| カラム | 型 | 必須 | 概要 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `applicant_id` | BIGINT | YES | 申請者ユーザー ID |
| `title` | VARCHAR(200) | YES | 件名 |
| `status` | VARCHAR(20) | YES | 経費申請ステータス |
| `total_amount` | NUMERIC(12, 0) | YES | 合計金額 |
| `submitted_at` | TIMESTAMP | NO | 申請日時 |
| `approved_at` | TIMESTAMP | NO | 承認日時 |
| `approved_by` | BIGINT | NO | 承認・差戻し実行者ユーザー ID |
| `returned_at` | TIMESTAMP | NO | 差戻し日時 |
| `return_reason` | TEXT | NO | 差戻し理由 |
| `created_at` | TIMESTAMP | YES | 作成日時 |
| `updated_at` | TIMESTAMP | YES | 更新日時 |

## 5. expense_items

| カラム | 型 | 必須 | 概要 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `expense_application_id` | BIGINT | YES | 経費申請 ID |
| `expense_date` | DATE | YES | 利用日 |
| `category` | VARCHAR(30) | YES | 経費カテゴリ |
| `amount` | NUMERIC(12, 0) | YES | 金額 |
| `description` | VARCHAR(500) | YES | 内容 |
| `receipt_object_key` | VARCHAR(500) | NO | 領収書 object key |
| `created_at` | TIMESTAMP | YES | 作成日時 |
| `updated_at` | TIMESTAMP | YES | 更新日時 |

## 6. audit_logs

| カラム | 型 | 必須 | 概要 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `user_id` | BIGINT | YES | 操作ユーザー ID |
| `action` | VARCHAR(100) | YES | 操作種別 |
| `target_type` | VARCHAR(100) | YES | 操作対象種別 |
| `target_id` | BIGINT | YES | 操作対象 ID |
| `detail` | TEXT | NO | 詳細 |
| `created_at` | TIMESTAMP | YES | 作成日時 |

## 7. 制約

| テーブル | 制約 | 内容 |
|---|---|---|
| `users` | UNIQUE | `employee_code`, `email` は一意。 |
| `users` | CHECK | `role` は `USER`, `APPROVER`, `ADMIN` のいずれか。 |
| `expense_applications` | CHECK | `status` は `DRAFT`, `SUBMITTED`, `APPROVED`, `RETURNED` のいずれか。 |
| `expense_applications` | CHECK | `total_amount >= 0` |
| `expense_items` | CHECK | `amount > 0` |
| `expense_items` | CHECK | `category` は定義済みカテゴリのいずれか。 |

Application validation では `NUMERIC(12, 0)` に合わせ、明細金額を 1 以上 999999999999 以下の整数円、申請合計を 999999999999 円以下に制限する。

## 8. インデックス

| インデックス | 対象 |
|---|---|
| `idx_expense_applications_applicant_id` | 申請者別検索 |
| `idx_expense_applications_status` | ステータス別検索 |
| `idx_expense_applications_created_at` | 作成日時順検索 |
| `idx_expense_applications_applicant_status` | 申請者 + ステータス検索 |
| `idx_expense_items_application_id` | 申請明細取得 |
| `idx_audit_logs_user_id` | ユーザー別監査ログ検索 |
| `idx_audit_logs_created_at` | 監査ログ日時検索 |
