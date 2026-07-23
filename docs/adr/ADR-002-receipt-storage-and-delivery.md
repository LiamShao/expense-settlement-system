# ADR-002: 領収書を storage adapter と application proxy で扱う

## Status

Accepted

## Date

2026-07-23

## Context

Phase 15 までの経費明細は `receipt_object_key` 文字列だけを保持し、利用者が object key を直接入力している。File upload/download、metadata、private storage、file access audit は未実装であり、実体の存在や権限を application が保証できない。

Phase 16 では local development/test と AWS production の両方で、JPEG/PNG/PDF の upload、preview、download、replace/delete を提供する。Browser user ごとの session、role、application ownership/status に基づく認可を backend の security boundary として維持する必要がある。

## Decision drivers

- Storage key、bucket、filesystem path を利用者入力や公開 API にしない。
- File access ごとに既存の session/role/ownership authorization を適用する。
- Local/test が AWS account と network を必須としない。
- Production は private S3 と ECS task role を利用し、public object を作らない。
- File 全体を application heap に保持せず streaming できる。
- DB と object storage の途中失敗、replace、delete retry を回復可能にする。
- 将来、traffic/size が増えた場合に presigned access へ移行できる境界を残す。

## Considered options

### Option 1: Object key または public URL を client が入力・保持する

実装は単純だが、key injection、他人の object 参照、存在しない object、public exposure、storage provider への API 結合を招く。認可と監査も保証できないため不採用とする。

### Option 2: S3 SDK を business service から直接利用する

Production 実装は短くなるが、local/test も S3 API に依存し、business rule と infrastructure concern が結合する。Failure simulation と adapter contract test も難しくなるため不採用とする。

### Option 3: Storage adapter + application-proxied upload/download

Backend が multipart upload と content request を受け、認証・認可、検査、監査後に local filesystem または private S3 と stream を中継する。Access boundary が単純で、storage key を client から隠せるため Phase 16 で採用する。

### Option 4: Browser と S3 の direct transfer + presigned URL

Application bandwidth を減らし、大容量/high traffic に適する。一方、短い期限、single-use 相当の制約、Content-Type/size/checksum binding、CORS、upload 完了通知、未完了 multipart cleanup、download audit の意味を追加設計する必要がある。Phase 16 の最大 10 MiB と想定 traffic では利点が複雑性を上回らないため現時点では不採用とする。

## Decision

- `ReceiptStorage` port を application 内に定義し、local/test filesystem adapter と private S3 adapter を実装する。
- 1 経費明細につき `ACTIVE` receipt を最大 1 件とし、metadata/state を PostgreSQL の `receipt_files` で管理する。
- Storage key は application が UUID を使って生成し、original file name を key にしない。
- API は metadata と認可済み content endpoint だけを公開し、storage key、bucket、filesystem path、永続 URL を返さない。
- Browser の upload/download は backend application proxy を経由し、session、role、ownership/status authorization、CSRF、file validation、malware scan、audit を適用する。
- Backend と adapter は streaming API を利用し、file 全体を heap に読み込まない。
- Replace は新 file が検査を通過するまで旧 `ACTIVE` file を維持する。
- `UPLOADING`、`PENDING_SCAN`、`ACTIVE`、`REJECTED`、`PENDING_DELETE` state と reconciliation により DB/storage の非 atomic operation を回復する。
- S3 bucket は private、Block Public Access、Bucket owner enforced、versioning、HTTPS only とする。
- S3 の initial server-side encryption は SSE-S3 とする。Customer-managed KMS key は機密区分または compliance requirement が生じた場合に再評価する。
- Phase 16 では `ACTIVE` object の lifecycle expiry を設定しない。保存年限確定後に別 decision とする。

## Consequences

### Positive

- 既存 session/authorization model を file access にそのまま適用できる。
- Client が storage key や provider-specific URL に依存しない。
- Local/test は AWS account なしで高速かつ決定的に実行できる。
- S3 adapter に切り替えても API と business service の契約を維持できる。
- Preview/download の成功を backend で一貫して監査できる。
- Replace と途中失敗で旧 file を失わず、stale object を cleanup 対象として追跡できる。

### Negative

- File traffic が ECS/ALB を通るため、application bandwidth、connection duration、task memory/buffer、ALB timeout の影響を受ける。
- DB と object storage をまたぐ state machine、cleanup、retry が必要になる。
- Direct S3 transfer より upload/download latency と infrastructure cost が増える可能性がある。
- Malware scanner availability が upload availability に影響する。
- Versioning 下の object 削除と法務上の保存年限を別途運用する必要がある。

## Security and operational notes

- File signature と許可 Content-Type/extension を照合し、SVG/HTML/archive を許可しない。
- Maximum 10 MiB を application と reverse proxy の両方で強制する。
- Clean 判定前の file を content endpoint から返さない。Production scanner 未設定/障害時は fail closed とする。
- Response は検証済み Content-Type、sanitized `Content-Disposition`、`nosniff`、`private, no-store` を使用する。
- Original file name、storage key、checksum、session ID、binary content を application log/audit detail に含めない。
- Local root は static resource 外に置き、normalized path が root を越えないことを検証する。
- S3 permission は receipt prefix の必要操作だけを ECS task role に付与する。

## Validation

Phase 16 implementation で次を検証する。

- Local/S3 adapter contract、path/key injection、streaming、encryption option
- Multipart size/type/signature/checksum/malware validation
- USER/APPROVER/ADMIN、ownership/status、review boundary
- Replace/delete/storage/scan/DB failure と stale state reconciliation
- Binary response header と storage detail 非公開
- Upload/preview/download/delete audit
- Real DB/local storage の browser E2E

## Revisit conditions

- Receipt size 上限を 10 MiB より大きくする。
- File traffic が ECS/ALB capacity、latency、cost SLO を満たさない。
- Mobile/native client、bulk upload、high-volume API client を追加する。
- Direct-to-S3 multipart upload または CDN delivery が必要になる。
- One-time URL、offline download、external sharing が業務要件になる。
- Customer-managed KMS key、Object Lock、legal hold、確定保存年限が compliance requirement になる。
- Malware scan を asynchronous pipeline として分離する必要が生じる。

Presigned access を採用する場合は、本 ADR を書き換えず新しい ADR で supersede する。

## Related documents

- [要件定義書](../01_requirements.md)
- [基本設計書](../02_basic_design.md)
- [DB 定義書](../03_db_definition.md)
- [API 仕様書](../04_api_spec.md)
- [AWS アーキテクチャ設計書](../14_aws_architecture_design.md)
- [領収書ファイル設計書](../17_receipt_file_design.md)
