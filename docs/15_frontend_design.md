# フロントエンド設計書

## 1. 目的と対象範囲

本書は Phase 14 の React / TypeScript frontend の画面、navigation、項目、権限制御、API 連携、error 処理、test 方針を定義する。

Phase 14 は次の二段階で進める。

1. 本書と関連文書を整備し、既存 API との整合性と不足 API を確定する。
2. 設計承認後、不足 API を補完してから React application と frontend test を実装する。

Phase 14B で frontend application を実装済みである。AWS resource、JWT / OIDC、領収書 upload / download は引き続き対象外とする。

## 2. 前提と設計方針

- UI 表示言語は日本語とする。
- Desktop を主対象とし、tablet 幅でも一覧と入力操作が破綻しない responsive layout とする。
- API の正式契約は `docs/openapi.yaml` とし、frontend 独自に response field を仮定しない。
- 認可は backend を正とする。frontend の menu / button 制御は誤操作防止であり、security boundary としない。
- 日付は `YYYY-MM-DD`、日時は Asia/Tokyo の `YYYY-MM-DD HH:mm`、金額は `ja-JP` の円表示とする。
- API が返す `statusName`、`categoryName`、`roleName` を表示に利用し、code は条件判定に利用する。
- destructive operation と状態遷移は確認 dialog を表示し、二重送信を防止する。
- page URL を直接開いた場合も認証と権限を確認し、許可されない画面を表示しない。

## 3. 想定 frontend 構成

Phase 14A で以下を採用し、実装時の version は `frontend/package.json` と lockfile で固定する。視覚仕様と wireframe は `docs/16_ui_design.md` に定義する。

| 領域 | 採用候補 | 用途 |
|---|---|---|
| Build / application | Vite + React + TypeScript | SPA の build と型安全な UI 実装 |
| UI component | MUI Core + MUI Icons Material | 日本語業務 UI の form、table、dialog、layout |
| Routing | React Router | public / authenticated / role-based route |
| Server state | TanStack Query | API request、cache、再取得、mutation state |
| Form | React Hook Form | 入力状態と明細行の動的追加・削除 |
| Validation | Zod | client validation と form type の共有 |
| HTTP client | Fetch wrapper | Same-origin cookie、CSRF header、共通 response、error 変換 |
| Unit / component test | Vitest + React Testing Library | component、hook、権限制御の検証 |
| API mock | MSW | 正常系・異常系の UI test |
| E2E | Playwright | login から主要 workflow までの検証 |

推奨 directory 構成は feature 単位とする。

```text
frontend/src/
  app/          router、provider、layout
  features/
    auth/
    expenses/
    reviews/
    audit-logs/
  components/   共通 UI
  api/          client、generated/manual types
  utils/        format、permission 判定
  test/         test setup、MSW handler
```

## 4. 画面一覧と route

| ID | 画面 | Route | 利用可能 role | 主な目的 |
|---|---|---|---|---|
| SCR-AUTH-001 | Login | `/login` | 未認証 | email / password を検証して利用を開始する。 |
| SCR-EXP-001 | 申請一覧 | `/expenses` | USER / APPROVER / ADMIN | 権限範囲内の申請を検索、ページングする。 |
| SCR-EXP-002 | 申請詳細 | `/expenses/:id` | 参照権限を持つ利用者 | ヘッダ、明細、状態履歴項目と可能な操作を表示する。 |
| SCR-EXP-003 | 申請作成 | `/expenses/new` | USER / APPROVER / ADMIN | 明細を入力し、下書きを作成する。 |
| SCR-EXP-004 | 申請編集 | `/expenses/:id/edit` | 申請者本人 | `DRAFT` / `RETURNED` の申請を更新する。 |
| SCR-REV-001 | 承認待ち一覧 | `/reviews` | APPROVER / ADMIN | 他人の `SUBMITTED` 申請を検索する。 |
| SCR-REV-002 | 承認待ち詳細 | `/reviews/:id` | APPROVER / ADMIN | Review API で他人の `SUBMITTED` 申請を確認し、承認・差戻しを行う。 |
| SCR-AUD-001 | 監査ログ | `/audit-logs` | ADMIN | 業務操作ログを検索する。 |
| SCR-ERR-001 | Not Found | `*` | 全利用者 | 不正な URL から安全な遷移先を案内する。 |

承認・差戻しは独立画面を作らず、申請詳細上の action と dialog で実行する。これにより対象内容を確認した状態で操作できる。

## 5. Navigation

```text
未認証
  Login
    └─ login success → 申請一覧

認証済み
  Application layout
    ├─ 申請一覧 ─┬─ 申請詳細 ─┬─ 申請編集
    │            │            ├─ 申請 / 削除
    │            │            └─ 承認 / 差戻し
    │            └─ 申請作成 → 申請詳細
    ├─ 承認待ち一覧 → 申請詳細（APPROVER / ADMIN）
    └─ 監査ログ（ADMIN）
```

Header には system name、login user name、role name、logout を表示する。Global navigation は次の条件で表示する。

| Menu | USER | APPROVER | ADMIN |
|---|---|---|---|
| 申請一覧 | 表示 | 表示 | 表示 |
| 新規申請 | 表示 | 表示 | 表示 |
| 承認待ち | 非表示 | 表示 | 表示 |
| 監査ログ | 非表示 | 非表示 | 表示 |

## 6. 認証と session

### 6.1 Login flow

1. Application 起動時に `/api/auth/me` を実行し、有効な `SESSION` cookie があれば user state を復元する。
2. Login 前の unsafe request として `/api/auth/csrf` から token/header name を取得する。
3. `/api/auth/login` に email / password と CSRF header を送信し、成功 response の user を authentication state に保持する。
4. Login 前に保護 route へ遷移していた場合は元の route、それ以外は `/expenses` へ遷移する。
5. Login 後の request は browser が同一 origin の `SESSION` cookie を自動送信し、unsafe method には memory 上の CSRF token を付与する。

### 6.2 Credential 保持

Phase 15 では HTTP Basic credential の生成・保持を削除する。Password は login request の間だけ form state に存在し、session ID は `HttpOnly` cookie のため JavaScript から参照しない。CSRF token は JavaScript memory のみに保持し、browser storage、URL、log、analytics に保存しない。Page reload 後は `/api/auth/me` で有効 session を復元する。

Logout button は確認 dialog を表示し、cancel では session と current route を維持する。Confirm 後に `/api/auth/logout` を CSRF header 付きで呼び、成功後だけ user state、CSRF token、query cache を破棄して `/login` へ遷移する。401 では local authentication state を破棄し、session expiry message を表示する。

### 6.3 Network 前提

- Local development は Vite proxy で `/api` を Spring Boot に転送し、same-origin として扱う。
- Production は ALB または reverse proxy で SPA と `/api` を同一 origin に公開する。
- 現在の backend に CORS 設定がないため、frontend と API を別 origin で公開しない。
- Production session cookie は `Secure` を必須とする。Local loopback のみ `Secure=false` を許容する。

## 7. 画面項目

### 7.1 Login

| 項目 | Type | 必須 | 制約 / 動作 |
|---|---|---|---|
| メールアドレス | email | 必須 | 255 文字以下、email format |
| パスワード | password | 必須 | 100 文字以下、表示切替可能 |
| Login | button | - | validation 成功時のみ request、送信中 disable |

認証失敗時は credential のどちらが誤っているかを区別せず、API の共通 message を form 上部に表示する。

### 7.2 申請一覧

検索条件:

| 項目 | API parameter | 制約 / 動作 |
|---|---|---|
| ステータス | `status` | 未指定、`DRAFT`、`SUBMITTED`、`APPROVED`、`RETURNED` |
| キーワード | `keyword` | 200 文字以下 |
| 利用日 From | `expenseDateFrom` | `YYYY-MM-DD` |
| 利用日 To | `expenseDateTo` | `YYYY-MM-DD`、From 以降を client でも確認 |
| 申請者 ID | `applicantId` | ADMIN のみ表示。現 API に user 候補取得機能がないため numeric exact search とする。 |
| 表示件数 | `size` | 20 / 50 / 100、default 20 |

検索 button で URL query string と API query を同期し、条件変更後の検索は page 0 に戻す。Reset は default 条件へ戻す。

一覧 columns:

| Column | Field | 備考 |
|---|---|---|
| ID | `id` | 詳細への link |
| 申請者 | `applicantName` | ADMIN で有用。全 role で同じ table を利用する。 |
| 件名 | `title` | 長文は省略し tooltip / accessible name で全体を確認可能にする。 |
| Status | `statusName` | code ごとに badge color を変え、色だけに依存しない。 |
| 合計金額 | `totalAmount` | 右寄せの円表示 |
| 申請日時 | `submittedAt` | null は `-` |
| 更新日時 | `updatedAt` | Asia/Tokyo 表示 |

### 7.3 申請詳細

Header section に ID、件名、status、申請者、合計金額、作成・更新・申請・承認・差戻し日時、承認者、差戻し理由を表示する。null の workflow 項目は非表示または `-` とする。

明細 table は利用日、カテゴリ、金額、内容、領収書 object key を表示する。領収書 API がないため object key は参考情報としてのみ表示し、download link にしない。

Action 表示条件:

| Action | 条件 |
|---|---|
| 編集 | login user が申請者本人、status が `DRAFT` / `RETURNED` |
| 削除 | login user が申請者本人、status が `DRAFT` / `RETURNED` |
| 申請 | login user が申請者本人、status が `DRAFT` / `RETURNED` |
| 承認 | role が APPROVER / ADMIN、他人の申請、status が `SUBMITTED` |
| 差戻し | role が APPROVER / ADMIN、他人の申請、status が `SUBMITTED` |

削除、申請、承認は確認 dialog を表示する。差戻し dialog は差戻し理由を必須とし、1,000 文字以下で入力する。成功後は detail cache と関連 list cache を更新または invalidate し、API の success message を通知する。

### 7.4 申請作成・編集

共通 form を利用する。

| 項目 | 必須 | 制約 / 動作 |
|---|---|---|
| 件名 | 必須 | 200 文字以下 |
| 明細 | 1 行以上 | 追加・削除可能。最後の 1 行は削除不可。 |
| 利用日 | 必須 | `YYYY-MM-DD` |
| カテゴリ | 必須 | `TRANSPORTATION`、`MEAL`、`SUPPLIES`、`ACCOMMODATION`、`OTHER` |
| 金額 | 必須 | 1 以上 `999999999999` 以下の整数円。DB の `NUMERIC(12, 0)` と一致させ、小数入力を許可しない。 |
| 内容 | 必須 | 500 文字以下 |
| 領収書 object key | 任意 | 500 文字以下。upload 未実装であることを明示する。 |
| 合計金額 | 自動 | 有効な明細金額を client で表示用に合計。保存値は backend 計算を正とする。 |

作成成功後は返却された ID の詳細へ遷移する。編集画面は詳細取得後、本人かつ編集可能 status であることを確認する。更新成功後は詳細へ戻る。未保存変更がある状態で離脱する場合は確認する。

### 7.5 承認待ち一覧

他人の `SUBMITTED` 申請だけを対象とし、申請者、件名、合計金額、申請日時を表示する。検索・pagination の基本動作は申請一覧と共通化する。

Phase 14A で追加した `/api/reviews` と `/api/reviews/{id}` を APPROVER / ADMIN 共通で利用する。

### 7.6 監査ログ

ADMIN のみ表示する。

検索条件:

| 項目 | API parameter | 制約 / 動作 |
|---|---|---|
| User ID | `userId` | numeric exact search |
| Action | `action` | 100 文字以下。既知 action は select とする。 |
| Target type | `targetType` | 100 文字以下。現行は `EXPENSE_APPLICATION`。 |
| 作成日 From | `createdDateFrom` | `YYYY-MM-DD` |
| 作成日 To | `createdDateTo` | `YYYY-MM-DD`、当日末までを backend が包含する。 |
| 表示件数 | `size` | 20 / 50 / 100 |

一覧は日時、user ID / user name、action、target type、target ID、detail を表示する。Target ID は対象申請を参照可能な場合に申請詳細 link とする。

## 8. API 連携

| UI operation | Method / path | 成功後の処理 |
|---|---|---|
| Login | `POST /api/auth/login` | user / credential を memory に保持する。 |
| Login user 確認 | `GET /api/auth/me` | authentication state を更新する。 |
| 申請検索 | `GET /api/expense-applications` | `data.content` と page metadata を表示する。 |
| 詳細取得 | `GET /api/expense-applications/{id}` | 詳細と action 可否を表示する。 |
| 作成 | `POST /api/expense-applications` | 作成した詳細へ遷移する。 |
| 更新 | `PUT /api/expense-applications/{id}` | 詳細へ遷移し cache を更新する。 |
| 削除 | `DELETE /api/expense-applications/{id}` | 一覧へ遷移し cache を破棄する。 |
| 申請 | `POST /api/expense-applications/{id}/submit` | 詳細と一覧を再取得する。 |
| 承認 | `POST /api/expense-applications/{id}/approve` | 詳細と承認待ち一覧を再取得する。 |
| 差戻し | `POST /api/expense-applications/{id}/return` | 詳細と承認待ち一覧を再取得する。 |
| 承認待ち検索 | `GET /api/reviews` | 他人の `SUBMITTED` 申請と page metadata を表示する。 |
| 承認待ち詳細 | `GET /api/reviews/{id}` | 承認判断用のヘッダと明細を表示する。 |
| 監査ログ検索 | `GET /api/audit-logs` | `data.content` と page metadata を表示する。 |

API client は `ApiResponse<T>` を unwrap し、`success=false` または non-2xx response を共通 error として扱う。request ごとに `AbortSignal` を渡し、画面遷移や検索条件変更で不要になった取得を中止できるようにする。

## 9. Error、loading、empty state

| HTTP / 状態 | UI 動作 |
|---|---|
| 400 `VALIDATION_ERROR` | field path を対応する form 項目へ割り当て、未対応 detail は form 上部に表示する。 |
| 400 business error | API message を action dialog または page alert に表示し、対象を再取得する。 |
| 401 | authentication state と cache を破棄し、session expiry message 付きで Login へ遷移する。 |
| 403 | 操作時は message を表示して再取得、route 初期表示時は権限不足 page を表示する。 |
| 404 | 申請が存在しない旨と一覧への link を表示する。 |
| 409 相当 | 現 API は主に 400 を返す。将来の競合 response も再取得を案内する。 |
| 500 / network error | retry 操作と共通 message を表示し、内部情報を表示しない。 |
| Loading | 初回取得は skeleton、mutation は対象 button を disable して進行状態を示す。 |
| Empty | 検索結果 0 件と未作成を区別し、条件 reset または新規作成を案内する。 |

Toast だけに重要な error を依存させず、form / page 内にも残る message を表示する。focus は error summary または dialog title へ移動し、keyboard 操作を維持する。

## 10. Pagination と URL state

- API page は 0 始まり、UI 表示は 1 始まりとする。
- `page`、`size`、検索条件を URL query string に保持し、再読込・back/forward で復元する。
- `totalPages=0` では page control を無効化する。
- 現在 page が削除などで範囲外になった場合は最終 page を再取得する。
- 検索 condition は空値を query に送らず、日付は timezone 変換せず `YYYY-MM-DD` を送る。

## 11. Accessibility と security

- Form control は visible label と error association を持たせる。
- Dialog は focus trap、Escape、focus return を提供する。
- Status と error は色だけで区別しない。
- Table は header と caption / accessible name を持たせ、狭い画面では重要項目を維持する。
- API の text は React の通常描画で escape し、HTML として挿入しない。
- Credential、password、Authorization header、個人情報を console、analytics、error report に記録しない。
- `receiptObjectKey` を任意 URL として解釈しない。
- Backend の 401 / 403 を最終判断として扱い、UI の可否判定だけを信用しない。

## 12. Test 方針

| Level | 主な対象 |
|---|---|
| Unit | format、query 変換、role / status action 判定、API error 変換 |
| Component | Login、検索 form、pagination、明細 form、確認 / 差戻し dialog |
| Integration | MSW を利用した各 route の loading、success、validation、401、403、empty state |
| E2E | USER の作成→編集→申請、APPROVER の承認 / 差戻し、ADMIN の全件・監査ログ検索 |

最低限、全 role と全 status の action visibility を table-driven test で網羅する。E2E は不足 API 対応後、実 DB を利用して自己承認禁止と role boundary も検証する。

Phase 14B では Vitest / Testing Library / MSW で 35 tests を実装した。Phase 15 では Basic credential を削除し、36 tests で CSRF header、same-origin cookie mode、reload 時の session 復元、server logout 後の local state 破棄を含めて検証した。Playwright では実 PostgreSQL API に対して reload 後の session 継続、USER の作成・編集・申請、APPROVER の承認・差戻し、USER の差戻し理由確認、ADMIN の監査ログ検索・logout を 1 本の serial workflow として検証した。

## 13. Phase 14A backend 対応

### 13.1 承認対象の参照 API

設計時点では以下の不整合があった。

- `APPROVER` は approve / return endpoint を実行できる。
- 一方、申請一覧と詳細は `APPROVER` を本人申請だけに制限する。
- そのため `APPROVER` は他人の承認対象を発見・確認できず、実用的な承認 UI を構築できない。

Phase 14A で、APPROVER / ADMIN が他人の `SUBMITTED` 申請だけを検索・詳細参照できる review 用 API を追加した。

```text
GET /api/reviews?keyword=&applicantId=&expenseDateFrom=&expenseDateTo=&page=0&size=20
GET /api/reviews/{id}
```

既存の本人申請一覧の認可を広げず、review use case を分離する。自己申請は review 結果から除外し、backend でも approve / return 時の自己操作禁止を継続する。

### 13.2 その他の確認事項

- User 一覧 API がないため、申請者検索は当面 user ID 入力となる。select / autocomplete が必要なら参照専用 user API を追加する。
- 金額は DB の `NUMERIC(12, 0)` に合わせた整数円とし、明細と合計の上限を `999999999999` 円として backend と OpenAPI に定義した。
- Login credential を安全に永続化できないため、production authentication の刷新は Phase 14 の SPA 公開とは分離して計画する。
- 領収書 upload / download は API 未実装のため、Phase 14 では object key の表示・入力に限定する。

## 14. Phase 14 完了条件

- 本書の画面、権限、API、error、pagination 方針が承認されている。
- Review API と金額上限が文書化・実装・backend test 済みである。
- React / TypeScript application が Login、申請 CRUD / submit、review、監査ログを提供する。
- Unit / component / integration test が成功し、主要 role workflow の E2E evidence が残っている。
- README、要件、基本設計、権限、OpenAPI、test 仕様、phase plan が実装結果と一致している。

## 15. Phase 14B 実装結果

Phase 14B で本書の application layout、route、in-memory authentication、API client、共通 component、全業務画面を実装した。Password と Authorization value は React memory のみに保持し、logout、401、reload で破棄する。Business page は route 単位で lazy load し、search condition と pagination は URL query と同期する。

承認待ち詳細は `/api/reviews/{id}` と対応する `/reviews/:id` を利用する。これにより APPROVER が既存の本人限定 `/api/expense-applications/{id}` を経由せず、直 URL と reload を含めて承認対象を参照できる。

## 16. Phase 15 実装結果

Phase 15 で frontend の Basic credential state と `Authorization` header を削除し、Spring Session JDBC の opaque cookie を browser に委ねる方式へ移行した。Application 起動時は `/api/auth/me` の完了まで route guard を loading state とし、有効 session があれば reload 後も元の業務画面を表示する。

API client は unsafe method の直前に `/api/auth/csrf` から token を取得し、module memory のみに保持する。`CSRF_INVALID` では token を破棄して 1 回だけ再取得・再送し、無限 retry は行わない。Logout は server response 成功後に user state、TanStack Query cache、CSRF token を破棄する。
