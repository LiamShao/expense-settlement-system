# 領収書ファイル設計書

## 1. 目的と状態

本書は Phase 16 の領収書画像/PDF upload、metadata、認可済み preview/download、replace/delete、private storage、audit の設計を定義する。

- 設計状態: Phase 16A 完了
- 実装状態: Phase 16E S3 adapter 完了。DB/storage/scanner foundation、明細 ID reconciliation、Receipt Service、HTTP API、private S3 adapter と offline contract test を実装済み。Frontend は未実装。
- 対象: backend、frontend、PostgreSQL、local filesystem、private Amazon S3
- 対象外: AWS resource 作成、法務上の最終保存年限確定、production malware scanner の運用構築

## 2. 基本方針

- 1 経費明細につき `ACTIVE` な領収書を最大 1 件とする。
- 経費申請を先に保存して明細 ID を確定し、その後に明細単位で領収書を upload する。
- 元ファイル名、client の Content-Type、path、object key を信用しない。Storage key は application が UUID を含む形式で生成する。
- Browser には storage key、bucket name、filesystem path、永続的な download URL を返さない。
- File content は backend の認証・認可を毎回通す application proxy 方式で streaming する。
- Local/test と S3 は同じ `ReceiptStorage` port の adapter とし、Service と API の契約を storage 実装から分離する。
- File と DB を単一 transaction にできないため、内部 state と cleanup/reconciliation により途中失敗を回復可能にする。
- File は untrusted content として扱い、形式検査と malware scan を通過するまで参照不可とする。

Storage と配信方式の判断理由は [ADR-002](adr/ADR-002-receipt-storage-and-delivery.md) に記録する。

## 3. 機能範囲

### 3.1 ファイル制約

| 項目 | Phase 16 の制約 |
|---|---|
| 許可形式 | JPEG、PNG、PDF |
| 許可 Content-Type | `image/jpeg`、`image/png`、`application/pdf` |
| 最大サイズ | 1 file 10 MiB（10,485,760 bytes） |
| File name | 表示用のみ。UTF-8 で 255 文字以内、path separator と制御文字を除去する。 |
| File signature | JPEG / PNG / PDF の magic bytes と宣言 Content-Type / extension の整合を検証する。 |
| Checksum | Upload stream から SHA-256 を計算し、小文字 64 桁 hex で metadata に保存する。 |
| 件数 | 1 経費明細につき `ACTIVE` 1 件まで。Replace 中は旧 file を維持する。 |

Spring multipart limit も 10 MiB に合わせ、request 全体の上限は multipart overhead を考慮して 11 MiB とする。圧縮展開、画像変換、OCR は Phase 16 では行わない。SVG、HTML、Office document、archive、password 付きか否かを解析できない形式は許可しない。

### 3.2 Upload / replace

1. Backend が application、item、申請者、status を検証する。
2. Server-generated key と `UPLOADING` metadata を作成する。
3. Size、file name、Content-Type、magic bytes を検証しながら private storage へ streaming し、SHA-256 を計算する。
4. Metadata を `PENDING_SCAN` にして malware scanner port を同期実行する。
5. Clean の場合だけ DB transaction で新 file を `ACTIVE`、旧 file を `PENDING_DELETE` に切り替える。
6. 旧 object を削除し、成功した metadata を削除する。削除失敗は `PENDING_DELETE` のまま retry 対象とする。
7. 途中失敗時は新 object/metadata を cleanup し、旧 `ACTIVE` file は維持する。

同一 file の再送を自動 deduplicate しない。Concurrent replace は item 単位の DB lock と partial unique index により `ACTIVE` 競合を防止し、競合時は 409 を返して画面を再取得させる。

Phase 16 の local/test scanner は EICAR test pattern を拒否できる deterministic adapter とする。Production profile は scanner 未設定時に fail closed とし、scan unavailable の file を `ACTIVE` にしない。Production scanner service の製品選定、signature update、availability、timeout、隔離物の運用は Phase 18 の deployment gate で確定する。

### 3.3 Preview / download

- Metadata は申請詳細に埋め込むほか、個別 metadata endpoint でも取得できる。
- Content endpoint は `disposition=inline` を preview、`disposition=attachment` を download として扱う。
- `inline` は JPEG、PNG、PDF のみ許可し、それ以外は attachment に固定する。
- `Content-Type` は検証済み metadata から設定する。
- `Content-Disposition` は `inline` / `attachment` と RFC 5987 形式の sanitized filename を設定する。
- `X-Content-Type-Options: nosniff`、`Cache-Control: private, no-store` を設定する。
- PDF preview は sandboxed iframe / new tab とし、file content を application DOM の HTML として解釈しない。
- Backend と storage adapter は file 全体を heap に読み込まず stream として扱う。

成功した preview/download は監査ログに記録する。Session ID、storage key、checksum、file binary は log に含めない。

### 3.4 Delete と retention

- 申請者本人が `DRAFT` / `RETURNED` のときだけ delete/replace できる。
- `SUBMITTED` / `APPROVED` の領収書は業務画面から変更・削除できない。
- `DRAFT` / `RETURNED` の申請削除時は関連 file を `PENDING_DELETE` にし、DB commit 後に object を削除する。
- Object 削除失敗は retry/reconciliation 対象とし、利用者向け API からは参照不可にする。
- 会計・法務上の保存年限が未確定のため、S3 lifecycle による `ACTIVE` file の自動削除は Phase 16 では設定しない。
- S3 versioning 下の旧 version と delete marker の完全消去方針は Phase 18 で保存年限と合わせて確定する。

## 4. 認可

Backend は role、applicant ID、application status、item の親 application を一度に検証する。Item ID だけで file を取得しない。

| 操作 | 申請者本人 | APPROVER | ADMIN |
|---|---|---|---|
| Upload / replace / delete | `DRAFT` / `RETURNED` のみ可 | 他人は不可 | 他人は不可 |
| 自分の file metadata/content 参照 | 全 status で可 | 全 status で可 | 全 status で可 |
| 他人の承認対象 file 参照 | 不可 | 他人の `SUBMITTED` のみ可 | 他人の `SUBMITTED` のみ可 |
| 管理目的の他人の file 参照 | 不可 | 不可 | 全 status で可 |

APPROVER / ADMIN の自己承認禁止は継続する。Metadata/content authorization は expense detail/review detail と同じ境界を使い、frontend の button 非表示を security boundary としない。

## 5. API 契約

Base path:

```text
/api/expense-applications/{applicationId}/items/{itemId}/receipt
```

| Method / path | 用途 | 成功 |
|---|---|---|
| `GET .../receipt` | Metadata 取得 | `200 ApiResponse<ReceiptFileResponse>` |
| `PUT .../receipt` | `multipart/form-data` の `file` part で upload/replace | `200 ApiResponse<ReceiptFileResponse>` |
| `DELETE .../receipt` | Active file の論理切離しと object cleanup 登録 | `200 ApiResponse<void>` |
| `GET .../receipt/content?disposition=inline|attachment` | 認可済み preview/download | `200` binary stream |

Upload/delete は CSRF token を必須とする。Binary response は共通 `ApiResponse` wrapper を使用しない。正式な parameter、response、error は [OpenAPI](openapi.yaml) を正とする。

### 5.1 ReceiptFileResponse

| Field | 型 | 内容 |
|---|---|---|
| `id` | int64 | Receipt metadata ID |
| `originalFileName` | string | Sanitize 済み表示名 |
| `contentType` | string | 検証済み Content-Type |
| `sizeBytes` | int64 | File size |
| `sha256Checksum` | string | SHA-256 hex |
| `uploadedAt` | datetime | Upload 完了日時 |
| `previewAvailable` | boolean | Inline preview 可否 |

Storage key、bucket、local path、scanner detail は response に含めない。Expense item response は既存 `receiptObjectKey` を廃止し、nullable な `receipt` metadata を返す。Create/update JSON request からも `receiptObjectKey` を廃止する。

### 5.2 File error

| HTTP | code | 条件 |
|---:|---|---|
| 400 | `INVALID_FILE` | Empty file、file name、signature/extension 不整合 |
| 401 | `UNAUTHORIZED` | Session なし/失効 |
| 403 | `FORBIDDEN` | Role、owner、status による権限不足 |
| 403 | `CSRF_INVALID` | Upload/delete の CSRF 不正 |
| 404 | `NOT_FOUND` | Application、item、active receipt、object が存在しない |
| 409 | `CONFLICT` | Concurrent replace/delete または state 競合 |
| 413 | `FILE_TOO_LARGE` | 10 MiB 超過 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | JPEG/PNG/PDF 以外 |
| 422 | `MALWARE_DETECTED` | Malware scanner が危険と判定 |
| 503 | `FILE_SERVICE_UNAVAILABLE` | Storage/scanner 一時障害 |

Scanner/storage の内部 endpoint、bucket、key、例外 message は client へ返さない。

## 6. DB 設計

Phase 16B の Flyway V5 で `receipt_files` を追加済みである。`expense_items.receipt_object_key` は Phase 16 API 実装時に client writable ではなくし、既存 seed/legacy 値の移行確認後に別 migration で削除する。実体のない legacy key を download URL として扱わない。

| Column | 型 | 必須 | 内容 |
|---|---|---|---|
| `id` | BIGSERIAL | YES | 主キー |
| `expense_item_id` | BIGINT | YES | `expense_items.id` FK |
| `storage_key` | VARCHAR(500) | YES | Server-generated private key、unique |
| `original_file_name` | VARCHAR(255) | YES | Sanitize 済み表示名 |
| `content_type` | VARCHAR(100) | 条件付き | 検証済み Content-Type。`UPLOADING` では NULL 可。 |
| `size_bytes` | BIGINT | 条件付き | 1～10,485,760。`UPLOADING` では NULL 可。 |
| `sha256_checksum` | CHAR(64) | 条件付き | Lowercase SHA-256 hex。`UPLOADING` では NULL 可。 |
| `state` | VARCHAR(30) | YES | `UPLOADING` / `PENDING_SCAN` / `ACTIVE` / `REJECTED` / `PENDING_DELETE` |
| `uploaded_by` | BIGINT | YES | `users.id` FK |
| `activated_at` | TIMESTAMP | NO | Scan 完了後に `ACTIVE` になった日時 |
| `created_at` | TIMESTAMP | YES | 作成日時 |
| `updated_at` | TIMESTAMP | YES | 更新日時 |

制約と index:

- `storage_key` unique
- `size_bytes BETWEEN 1 AND 10485760`
- `content_type IN ('image/jpeg', 'image/png', 'application/pdf')`
- `sha256_checksum` は lowercase hex 64 文字
- `state` は定義値のみ
- `PENDING_SCAN` / `ACTIVE` は Content-Type、size、checksum を必須とし、`ACTIVE` は `activated_at` を必須とする
- `expense_item_id WHERE state = 'ACTIVE'` の partial unique index
- `state, updated_at` index を cleanup/reconciliation に使用
- `expense_item_id` と `uploaded_by` の FK

`expense_item_id` FK は storage object の cleanup を通さない silent cascade delete を避けるため `ON DELETE CASCADE` を付けない。現行の経費申請 update は全明細を delete/insert するため、receipt upload を有効化する前に update request へ既存 item ID を引き継ぎ、既存明細の update、新規明細の insert、削除明細の receipt cleanup を明示的に reconcile する方式へ変更する。

## 7. Storage adapter

```text
ReceiptFileService
  ├─ ReceiptFileMapper / AuditLogService
  ├─ ReceiptStorage
  │    ├─ LocalReceiptStorage
  │    └─ S3ReceiptStorage
  └─ MalwareScanner
```

`ReceiptStorage` は `put(key, stream, exactLength, verifiedContentType)`, `open(key)`, `delete(key)`, `exists(key)` を提供する。Application が key を生成し、adapter は任意の絶対 path や bucket を request から受け取らない。

Phase 16B で port、`LocalReceiptStorage`、`UnavailableReceiptStorage`、`MalwareScanner`、EICAR pattern 用 deterministic adapter、未設定時の fail-closed adapter を実装済みである。Default profile は storage/scanner とも unavailable、`local` profile だけ local storage と test scanner を明示的に選択する。

Phase 16E で port に exact content length と verified Content-Type を追加し、local adapter は temporary file の実 byte 数が一致した場合だけ final object を公開するよう強化した。`s3` storage type のときだけ AWS client と S3 adapter を構築する。

Local adapter:

- Static resources 配下ではない `.local/receipts` を default local root とし、Git 管理対象外にする。Persistent volume/bind mount の最終 Compose 構成は file API 実装時に確定する。
- Normalize 後の path が root 配下であることを確認する。
- Temporary file へ書き、同一 filesystem の atomic move を優先する。Filesystem が未対応の場合だけ通常 move へ fallback する。
- Existing object を上書きせず、symbolic link と root escape を拒否する。
- Test は一時 directory を使用し、test 間で分離する。

S3 adapter:

- Dedicated private bucket、Block Public Access、Bucket owner enforced、versioning、HTTPS only を利用する。
- ECS task role は receipt prefix の `GetObject` / `PutObject` / `DeleteObject` と必要最小限の bucket access だけを持つ。
- Initial encryption は S3 managed key（SSE-S3）とし、customer-managed KMS key が必要になった場合は IAM、rotation、cost と合わせて ADR を再評価する。
- Browser に S3 URL と credential を渡さない。
- AWS SDK v2 の synchronous streaming request に exact Content-Length を渡し、unknown-length buffering を避ける。
- Put request は verified Content-Type、SSE-S3（AES256）、`If-None-Match: *` を指定し、ACL は使用しない。
- SDK request/error contract は mocked `S3Client` で検証し、実 bucket/IAM/network test は Phase 18 まで行わない。

## 8. Audit / logging

| action | targetType | 記録タイミング |
|---|---|---|
| `RECEIPT_UPLOAD` | `RECEIPT_FILE` | 初回 file が `ACTIVE` になったとき |
| `RECEIPT_REPLACE` | `RECEIPT_FILE` | Replace が `ACTIVE` へ切り替わったとき |
| `RECEIPT_DELETE` | `RECEIPT_FILE` | 利用者から参照不可になったとき |
| `RECEIPT_PREVIEW` | `RECEIPT_FILE` | Inline content stream を開始したとき |
| `RECEIPT_DOWNLOAD` | `RECEIPT_FILE` | Attachment content stream を開始したとき |

Audit detail は application ID、item ID、size、Content-Type 程度に限定し、original file name、storage key、checksum、session ID、file content を含めない。失敗した unauthorized access は業務監査ログではなく security/access log と metric の対象とし、機微情報を記録しない。

## 9. Frontend 設計

- Create 時は application/item 保存後に upload 可能であることを明示する。
- Edit form の object key input を file picker、選択 file 情報、upload/replace/delete action に置き換える。
- `accept="image/jpeg,image/png,application/pdf"` は usability 補助とし、backend validation を必須とする。
- 10 MiB 超過と未対応形式は client でも早期表示する。
- Upload 中は対象明細の action を disable し、progress、cancel、retry を表示する。Server result を最終状態とする。
- Existing receipt は file name、size、upload 日時、preview/download、編集可能時だけ replace/delete を表示する。
- Preview dialog は image を object URL、PDF を sandboxed iframe で表示し、close/unmount 時に object URL を revoke する。
- Content は認証付き `fetch` で Blob として取得し、storage URL を DOM や browser storage に保存しない。
- 401/403/404/409/413/415/422/503 を file action に対応した日本語 message と recovery action へ変換する。
- Upload/replace/delete 後は detail query を invalidate し、最新 metadata と status を再取得する。

## 10. Verification plan

### Backend unit / adapter

- Ownership、role、status、application-item parent mismatch
- File name、empty、size boundary、Content-Type、magic bytes、SHA-256
- Upload/replace/delete state transition と旧 active file 保護
- Storage/scan/DB failure cleanup、stale state reconciliation、delete retry
- Local root escape 防止、atomic write、S3 request/key/encryption option
- Metadata response に storage key/path が含まれないこと

### API integration

- Session + CSRF multipart upload と metadata/content/delete
- USER の他人 file、APPROVER review boundary、ADMIN read、self-review boundary
- 10 MiB 境界、不正 signature、unsupported type、EICAR pattern
- Response header、binary body、content streaming、audit log
- Concurrent replace と transaction failure 後の active file

### Frontend / E2E

- File selection、client validation、progress/error/retry、replace/delete confirmation
- Image/PDF preview、download、object URL cleanup、401/403/404/409/413/415/422/503
- Real DB/storage で USER が作成・upload・申請し、APPROVER が preview/download して判断する workflow
- 全 role logout と reload session restore の Phase 15 regression

## 11. Implementation order

1. Flyway V5、entity/mapper、storage/scanner ports と local adapter（Phase 16B 完了）
2. Expense item ID を維持する update reconciliation と、削除明細/application の receipt cleanup boundary（Phase 16C 完了）
3. Receipt service、authorization、state cleanup、audit（Phase 16C 完了）
4. Controller、error handling、OpenAPI contract test、integration test（Phase 16D 完了）
5. S3 adapter と adapter contract test（Phase 16E 完了、実 AWS account 不要）
6. Frontend type/API/component、form/detail/review integration
7. Real DB/local storage E2E、full regression、test evidence 更新

Phase 16 実装では AWS resource、remote CI、production scanner service を作成しない。
