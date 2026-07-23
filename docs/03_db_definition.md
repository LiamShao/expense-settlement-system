# DB定義書

## 1. 概要

DB は PostgreSQL を利用する。スキーマ管理は Flyway migration で行う。

## 2. テーブル一覧

| テーブル | 概要 |
|---|---|
| `users` | ユーザー、社員番号、メールアドレス、ロールを管理する。 |
| `spring_session` | Spring Session JDBC の session header、principal、期限を管理する。 |
| `spring_session_attributes` | Session attribute を管理する。 |
| `expense_applications` | 経費申請ヘッダを管理する。 |
| `expense_items` | 経費申請明細を管理する。 |
| `receipt_files` | 領収書 metadata、private storage key、lifecycle state を管理する。 |
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
| `failed_login_attempts` | INTEGER | YES | 連続 login failure 回数。default 0。 |
| `locked_until` | TIMESTAMP | NO | 一時 lock 期限。現在時刻以前または NULL は unlock。 |
| `last_login_at` | TIMESTAMP | NO | 最終 login 成功日時 |
| `created_at` | TIMESTAMP | YES | 作成日時 |
| `updated_at` | TIMESTAMP | YES | 更新日時 |

## 4. Spring Session JDBC

`spring_session` と `spring_session_attributes` は Spring Session JDBC の PostgreSQL schema に従い、Flyway で作成する。

| テーブル | 主な項目 | 概要 |
|---|---|---|
| `spring_session` | `primary_id`, `session_id`, `creation_time`, `last_access_time`, `max_inactive_interval`, `expiry_time`, `principal_name` | Opaque session ID、idle timeout、principal index を管理する。 |
| `spring_session_attributes` | `session_primary_id`, `attribute_name`, `attribute_bytes` | Spring Security context などの session attribute を server-side に保存する。 |

`spring_session_attributes.session_primary_id` は `spring_session.primary_id` を参照し、session 削除時に cascade delete する。Password plaintext、request body、CSRF header value を独自 column として保存しない。

## 5. expense_applications

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

## 6. expense_items

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

## 7. audit_logs

| カラム | 型 | 必須 | 概要 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `user_id` | BIGINT | YES | 操作ユーザー ID |
| `action` | VARCHAR(100) | YES | 操作種別 |
| `target_type` | VARCHAR(100) | YES | 操作対象種別 |
| `target_id` | BIGINT | YES | 操作対象 ID |
| `detail` | TEXT | NO | 詳細 |
| `created_at` | TIMESTAMP | YES | 作成日時 |

## 8. 制約

| テーブル | 制約 | 内容 |
|---|---|---|
| `users` | UNIQUE | `employee_code`, `email` は一意。 |
| `users` | CHECK | `role` は `USER`, `APPROVER`, `ADMIN` のいずれか。 |
| `users` | CHECK | `failed_login_attempts >= 0` |
| `expense_applications` | CHECK | `status` は `DRAFT`, `SUBMITTED`, `APPROVED`, `RETURNED` のいずれか。 |
| `expense_applications` | CHECK | `total_amount >= 0` |
| `expense_items` | CHECK | `amount > 0` |
| `expense_items` | CHECK | `category` は定義済みカテゴリのいずれか。 |
| `receipt_files` | UNIQUE / partial UNIQUE | `storage_key` は一意、`ACTIVE` は 1 `expense_item_id` につき最大 1 件。 |
| `receipt_files` | CHECK | State、許可 Content-Type、1～10,485,760 bytes、lowercase SHA-256 を制約する。 |
| `receipt_files` | CHECK | `PENDING_SCAN` / `ACTIVE` は metadata 必須、`ACTIVE` は `activated_at` 必須。 |

Application validation では `NUMERIC(12, 0)` に合わせ、明細金額を 1 以上 999999999999 以下の整数円、申請合計を 999999999999 円以下に制限する。

## 9. インデックス

| インデックス | 対象 |
|---|---|
| `idx_expense_applications_applicant_id` | 申請者別検索 |
| `idx_expense_applications_status` | ステータス別検索 |
| `idx_expense_applications_created_at` | 作成日時順検索 |
| `idx_expense_applications_applicant_status` | 申請者 + ステータス検索 |
| `idx_expense_items_application_id` | 申請明細取得 |
| `idx_audit_logs_user_id` | ユーザー別監査ログ検索 |
| `idx_audit_logs_created_at` | 監査ログ日時検索 |
| `spring_session_ix1` | Session ID lookup |
| `spring_session_ix2` | Session expiry cleanup |
| `spring_session_ix3` | Principal ごとの session lookup |
| `uk_receipt_files_active_expense_item` | 明細ごとの `ACTIVE` receipt 一意性 |
| `idx_receipt_files_expense_item_id` | 明細別 receipt 検索 |
| `idx_receipt_files_state_updated_at` | Cleanup / reconciliation 対象検索 |

## 10. receipt_files

Phase 16B の Flyway V5 で追加済みである。`UPLOADING` insert 時点では content の最終 metadata が未確定なため、`content_type`、`size_bytes`、`sha256_checksum` は一時的に NULL を許可する。`PENDING_SCAN` / `ACTIVE` では CHECK 制約により全項目を必須とする。

| カラム | 型 | 必須 | 概要 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `expense_item_id` | BIGINT | YES | 経費明細 ID |
| `storage_key` | VARCHAR(500) | YES | Application-generated private storage key |
| `original_file_name` | VARCHAR(255) | YES | Sanitize 済み表示用 file name |
| `content_type` | VARCHAR(100) | 条件付き | 検証済み Content-Type |
| `size_bytes` | BIGINT | 条件付き | 1～10,485,760 bytes |
| `sha256_checksum` | CHAR(64) | 条件付き | Lowercase SHA-256 hex |
| `state` | VARCHAR(30) | YES | `UPLOADING` / `PENDING_SCAN` / `ACTIVE` / `REJECTED` / `PENDING_DELETE` |
| `uploaded_by` | BIGINT | YES | Upload user ID |
| `activated_at` | TIMESTAMP | NO | `ACTIVE` へ切り替えた日時 |
| `created_at` | TIMESTAMP | YES | 作成日時 |
| `updated_at` | TIMESTAMP | YES | 更新日時 |

`storage_key` を unique、`ACTIVE` の `expense_item_id` を partial unique とし、1 明細の active receipt を最大 1 件にする。`state, updated_at` index は cleanup/reconciliation に利用する。

`expense_item_id` FK は cascade delete しない。Application Service が receipt object cleanup と metadata state transition を行わずに明細を削除すると DB が拒否し、private storage の orphan を防ぐ。

既存 `expense_items.receipt_object_key` は Phase 16 で client writable ではなくする。実体のない seed/legacy key を URL として扱わず、移行確認後に別 migration で column を削除する。
