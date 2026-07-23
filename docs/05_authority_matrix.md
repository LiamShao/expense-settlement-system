# 権限マトリクス

## 1. ロール定義

| ロール | 表示名 | 概要 |
|---|---|---|
| USER | 一般社員 | 自分の経費申請を作成・管理する。 |
| APPROVER | 承認者 | 申請中の経費申請を承認・差戻しする。 |
| ADMIN | 管理者 | 全件参照、承認・差戻し、監査ログ参照が可能。 |

## 2. 操作権限

| 操作 | USER | APPROVER | ADMIN | 備考 |
|---|---|---|---|---|
| ログイン | 可 | 可 | 可 | 全ロール共通 |
| 自分のユーザー情報取得 | 可 | 可 | 可 | 全ロール共通 |
| 経費申請作成 | 可 | 可 | 可 | 現実装では認証済みユーザーが作成可能 |
| 自分の経費申請一覧取得 | 可 | 可 | 可 | 全ロール共通 |
| 他人の経費申請一覧取得 | 不可 | 不可 | 可 | ADMIN は全件参照可能 |
| 自分の経費申請詳細取得 | 可 | 可 | 可 | 全ロール共通 |
| 他人の経費申請詳細取得 | 不可 | 不可 | 可 | ADMIN は全件参照可能 |
| 他人の承認待ち一覧・詳細取得 | 不可 | 可 | 可 | Review API は他人の `SUBMITTED` のみ返す。 |
| 自分の経費申請更新 | 可 | 可 | 可 | `DRAFT` / `RETURNED` のみ |
| 他人の経費申請更新 | 不可 | 不可 | 不可 | 管理者でも更新不可想定 |
| 自分の経費申請削除 | 可 | 可 | 可 | `DRAFT` / `RETURNED` のみ |
| 他人の経費申請削除 | 不可 | 不可 | 不可 | 管理者でも削除不可想定 |
| 自分の経費申請申請 | 可 | 可 | 可 | `DRAFT` / `RETURNED` のみ |
| 他人の経費申請申請 | 不可 | 不可 | 不可 | 申請操作は申請者本人のみ |
| 他人の経費申請承認 | 不可 | 可 | 可 | `SUBMITTED` のみ |
| 自分の経費申請承認 | 不可 | 不可 | 不可 | 自己承認禁止 |
| 他人の経費申請差戻し | 不可 | 可 | 可 | `SUBMITTED` のみ |
| 自分の経費申請差戻し | 不可 | 不可 | 不可 | 自己差戻し禁止 |
| 監査ログ参照 | 不可 | 不可 | 可 | ADMIN のみ検索可能 |
| 自分の領収書 upload / replace / delete | 可 | 可 | 可 | 申請者本人かつ `DRAFT` / `RETURNED` のみ |
| 自分の領収書 preview / download | 可 | 可 | 可 | 全 status |
| 他人の承認対象領収書 preview / download | 不可 | 可 | 可 | 他人の `SUBMITTED` review のみ |
| 他人の管理対象領収書 preview / download | 不可 | 不可 | 可 | ADMIN の全件参照権限に従う |
| 他人の領収書 upload / replace / delete | 不可 | 不可 | 不可 | ADMIN も代理変更不可 |

## 3. 補足

- `APPROVER` と `ADMIN` も申請者として経費申請を作成できる。
- 承認・差戻しの権限はロールと申請者 ID の両方で判定する。
- ADMIN は他人の経費申請を参照できるが、更新・削除・申請の代理操作はできない。
- 監査ログは経費申請の作成、更新、削除、申請、承認、差戻しを対象とする。
- Phase 16 では領収書の upload、replace、delete、preview、download も監査対象とする。設計済み・未実装。

## 4. Frontend 表示制御

| 画面 / action | USER | APPROVER | ADMIN | 備考 |
|---|---|---|---|---|
| 申請一覧・新規申請 menu | 表示 | 表示 | 表示 | 一覧の参照範囲は既存 API 権限に従う。 |
| 承認待ち menu | 非表示 | 表示 | 表示 | Review API 実装済み。Frontend は Phase 14B で実装する。 |
| 監査ログ menu | 非表示 | 非表示 | 表示 | 直接 URL でも role guard を行う。 |
| 編集・削除・申請 button | 本人かつ `DRAFT` / `RETURNED` のみ | 本人かつ `DRAFT` / `RETURNED` のみ | 本人かつ `DRAFT` / `RETURNED` のみ | frontend 表示条件と backend 認可を一致させる。 |
| 承認・差戻し button | 非表示 | 他人かつ `SUBMITTED` のみ | 他人かつ `SUBMITTED` のみ | 自己操作は禁止する。 |
| 領収書 upload / replace / delete | 本人かつ `DRAFT` / `RETURNED` のみ | 本人かつ `DRAFT` / `RETURNED` のみ | 本人かつ `DRAFT` / `RETURNED` のみ | Phase 16。 |
| 領収書 preview / download | 自分の申請 | 自分、または他人の `SUBMITTED` review | 参照可能な全申請 | Phase 16。Backend が最終認可する。 |

Frontend の表示制御は usability のための補助であり、認可判断は必ず backend でも実施する。
