# MUI UI デザイン仕様書

## 1. 目的

本書は Phase 14A の視覚設計成果物として、React frontend の MUI theme、layout、component 利用方針、responsive rule、主要画面 wireframe を定義する。

対象は社内向け経費申請・承認業務である。華美な dashboard ではなく、検索、一覧、詳細確認、入力、承認判断を短い導線で正確に行える日本語業務 UI を目標とする。

## 2. UI 技術決定

| 領域 | 決定 |
|---|---|
| Component framework | MUI Core |
| Icon | MUI Icons Material |
| Styling | MUI theme、`sx`、必要最小限の reusable styled component |
| Data list | MUI `Table` + server-side `TablePagination` |
| Form | MUI form component + React Hook Form |
| Date input | `TextField` の date input。MUI X Date Pickers は初期 scope に含めない。 |
| Dialog | MUI `Dialog`。削除、申請、承認、差戻し確認に利用する。 |
| Notification | page 内 `Alert` を基本とし、成功通知のみ `Snackbar` を併用する。 |

MUI X Data Grid は Phase 14 の一覧要件に必須ではなく、free / Pro feature の境界と独自操作を増やさないため採用しない。列固定、virtualization、Excel 相当の編集が必要になった場合に再検討する。

## 3. Design principle

- 1 画面 1 主目的とし、primary action を増やしすぎない。
- Role と status は文字と色の両方で示す。
- 一覧は検索条件、結果件数、table、pagination の順に統一する。
- Form は上から下へ自然に入力でき、明細合計を常に確認できる構造にする。
- 承認・差戻しは必ず申請詳細を確認してから行う。
- API error は操作箇所の近くに残し、Snackbar だけで消さない。
- Japanese label を優先し、技術 code は補助表示または非表示とする。

## 4. Theme token

### 4.1 Color

| Token | Value | 用途 |
|---|---|---|
| `primary.main` | `#1D4ED8` | Primary button、active navigation、link |
| `primary.dark` | `#1E3A8A` | Hover / selected emphasis |
| `primary.light` | `#DBEAFE` | Selected row、soft background |
| `secondary.main` | `#475569` | Secondary action |
| `background.default` | `#F6F8FB` | Application background |
| `background.paper` | `#FFFFFF` | Card、table、dialog |
| `text.primary` | `#172033` | Main text |
| `text.secondary` | `#5F6B7A` | Supporting text |
| `divider` | `#D8DEE8` | Border / separator |
| `success.main` | `#15803D` | Approved / success |
| `warning.main` | `#B45309` | Returned / caution |
| `error.main` | `#B42318` | Validation / destructive action |
| `info.main` | `#0369A1` | Submitted / information |

Status chip:

| Status | Foreground | Background | Label |
|---|---|---|---|
| `DRAFT` | `#475569` | `#EEF2F6` | 下書き |
| `SUBMITTED` | `#075985` | `#E0F2FE` | 申請中 |
| `APPROVED` | `#166534` | `#DCFCE7` | 承認済み |
| `RETURNED` | `#9A3412` | `#FFEDD5` | 差戻し |

### 4.2 Typography

```text
font-family:
  -apple-system, BlinkMacSystemFont, "Noto Sans JP", "Yu Gothic UI",
  "Hiragino Kaku Gothic ProN", Meiryo, sans-serif
```

| Usage | Size / weight |
|---|---|
| Page title | 24px / 700 |
| Section title | 18px / 700 |
| Body / form | 14px / 400 |
| Table header | 13px / 700 |
| Supporting text | 12px / 400 |
| Button | 14px / 600、text transform 無効 |

### 4.3 Spacing、shape、elevation

- Spacing unit は MUI default の 8px とする。
- Page section 間は 24px、form field 間は 16px、inline action 間は 8px とする。
- Card / dialog radius は 8px、button / input は 6px、Chip は pill shape とする。
- Shadow は dialog、temporary drawer、sticky header に限定し、通常 card は border を基本とする。
- Control height は原則 40px、主要 button は 40px 以上とする。

## 5. Application layout

Desktop (`lg` 以上):

```text
┌────────────── 240px ──────────────┬────────────────────────────────────┐
│ 経費精算システム                   │ Header 64px       User / Role / Log │
│                                    ├────────────────────────────────────┤
│  申請一覧                          │ Breadcrumb                         │
│  新規申請                          │ Page title              Main action│
│  承認待ち                          │                                    │
│  監査ログ                          │ Content (max 1440px)                │
│                                    │                                    │
└────────────────────────────────────┴────────────────────────────────────┘
```

- Permanent sidebar 240px、top header 64px とする。
- Content は padding 24px、最大幅 1440px とし、広すぎる form は 960px に制限する。
- Sidebar menu は role により表示制御し、active item は左 border と soft blue background で示す。
- Header 右側に user name、role chip、logout を配置する。logout は確認 dialog で cancel / confirm を選択できる。

Tablet / narrow desktop (`md` 未満):

- Sidebar は menu button から開く temporary drawer とする。
- Content padding は 16px とする。
- Search field は 2 column から 1 column へ折り返す。
- Table は重要 column を維持して横 scroll とし、意味の異なる card list へ変形させない。
- Action button は page 下部または menu にまとめ、touch target 44px を確保する。

## 6. 共通 component

| Component | MUI base | Rule |
|---|---|---|
| `PageHeader` | `Stack`, `Typography`, `Breadcrumbs` | title、description、primary action を統一する。 |
| `SearchPanel` | `Paper`, `Grid`, `TextField` | default collapsed にせず、検索条件を常時確認可能にする。 |
| `StatusChip` | `Chip` | Status token を一元管理する。 |
| `DataTable` | `Table`, `TableContainer` | Header sticky、amount 右寄せ、row hover を提供する。 |
| `EmptyState` | `Box`, `Typography`, `Button` | 0 件理由と次 action を示す。 |
| `ErrorAlert` | `Alert` | API message と retry / navigation を表示する。 |
| `ConfirmDialog` | `Dialog` | 対象名と結果を具体的に説明する。 |
| `FormActions` | `Stack`, `Button` | Cancel は左、save / transition は右。狭幅では縦配置する。 |
| `LoadingSkeleton` | `Skeleton` | Page ごとの最終 layout に近い形を表示する。 |

## 7. Screen wireframe

### 7.1 Login

```text
┌──────────────────────────────────────────────────────────────┐
│                    経費精算システム                          │
│             ┌────────────────────────────┐                   │
│             │ Login                      │                   │
│             │ 業務アカウントでログイン   │                   │
│             │                            │                   │
│             │ メールアドレス [________] │                   │
│             │ パスワード     [______ 👁]│                   │
│             │ [ error alert ]            │                   │
│             │        [ ログインする ]    │                   │
│             └────────────────────────────┘                   │
└──────────────────────────────────────────────────────────────┘
```

- Login card は最大 420px、page 中央配置とする。
- Password 表示切替は accessible label を持つ icon button とする。

### 7.2 申請一覧

```text
申請一覧                                      [＋ 新規申請]
自分の経費申請を検索・確認します。
┌ 検索条件 ───────────────────────────────────────────────┐
│ Status [すべて▼] Keyword [________] 利用日 [from]-[to] │
│ ADMIN: 申請者ID [____]      [条件をクリア] [検索]      │
└─────────────────────────────────────────────────────────┘
検索結果 24件
┌────┬────────┬──────────────┬──────┬─────────┬────────┐
│ ID │ 申請者 │ 件名         │Status│ 合計金額│ 更新日時│
├────┼────────┼──────────────┼──────┼─────────┼────────┤
│ 31 │ 山田   │ 大阪出張費   │申請中│ ¥13,820 │ ...    │
└────┴────────┴──────────────┴──────┴─────────┴────────┘
                                      < 1 2 3 >  20件
```

### 7.3 申請詳細

```text
申請一覧 / #31
経費申請詳細                         [編集] [削除] [申請]
                                      または [差戻し] [承認]
┌ 基本情報 ───────────────────────────────────────────────┐
│ [申請中]  大阪出張交通費             合計 ¥13,820      │
│ 申請者 山田 太郎 / 営業部   申請日時 2026-07-16 10:30 │
└─────────────────────────────────────────────────────────┘
┌ 明細 ───────────────────────────────────────────────────┐
│ 利用日     Category      内容             金額           │
│ 2026-07-01 交通費         新幹線代         ¥13,820       │
└─────────────────────────────────────────────────────────┘
┌ Workflow 情報 / 差戻し理由（該当時）────────────────────┐
└─────────────────────────────────────────────────────────┘
```

- Status と owner 条件により header action を切り替える。
- 承認を primary、差戻しを outlined warning action とする。削除のみ error color とする。

### 7.4 申請作成・編集

```text
申請一覧 / 新規申請
経費申請を作成
┌ 基本情報 ───────────────────────────────────────────────┐
│ 件名 [_______________________________________________]  │
└─────────────────────────────────────────────────────────┘
┌ 明細 ───────────────────────────────────── [＋明細追加] ┐
│ #1 利用日 [____] Category [____▼] 金額 [________]       │
│    内容   [____________________________________] [削除] │
│    領収書 object key [______________________________]   │
├─────────────────────────────────────────────────────────┤
│                                      合計 ¥13,820       │
└─────────────────────────────────────────────────────────┘
[キャンセル]                                  [保存する]
```

- 明細は desktop でも横長 table edit にせず、field label が残る row panel とする。
- Total は明細 section 下部で sticky にせず常時再計算する。

### 7.5 承認待ち

```text
承認待ち
他の申請者から提出された申請を確認します。
┌ 検索条件 ───────────────────────────────────────────────┐
│ 申請者ID [____] Keyword [________] 利用日 [from]-[to]  │
│                                      [クリア] [検索]    │
└─────────────────────────────────────────────────────────┘
┌────┬────────┬──────────────┬─────────┬──────────────┐
│ ID │ 申請者 │ 件名         │ 合計金額│ 申請日時     │
└────┴────────┴──────────────┴─────────┴──────────────┘
```

- Row click / ID link から review detail を開き、内容確認後に承認・差戻しする。
- 自己申請および `SUBMITTED` 以外は backend が返さない。

### 7.6 監査ログ

```text
監査ログ                                      [ADMIN]
┌ 検索条件 ───────────────────────────────────────────────┐
│ User ID [___] Action [________▼] Target [________▼]    │
│ 作成日 [from]-[to]                         [検索]       │
└─────────────────────────────────────────────────────────┘
┌──────────────┬────────┬─────────────┬──────┬─────────┐
│ 日時         │ User   │ Action      │Target│ Detail  │
└──────────────┴────────┴─────────────┴──────┴─────────┘
```

## 8. Interaction rule

- Search は button 実行とし、入力のたびに API request しない。
- Row 内に多数の icon action を置かず、詳細 page に workflow action を集約する。
- Mutation 中は対象 action を disable し、progress indicator を表示する。
- Confirm dialog の primary label は「実行」ではなく「申請する」「承認する」など具体的にする。
- Delete dialog は ID と件名を表示し、取り消せないことを説明する。
- Return dialog は理由入力と文字数表示を持ち、未入力では送信できない。
- 未保存 form からの離脱には確認を表示する。

## 9. Accessibility check

- Text と背景、interactive component は WCAG 2.1 AA 相当の contrast を目標とする。
- Keyboard focus indicator を theme で消さない。
- Icon-only button は必ず `aria-label` を持つ。
- Form error は `aria-describedby` で field と関連付ける。
- Dialog open 時は title へ focus context を移し、close 後は trigger へ戻す。
- Table row 全体だけを click target にせず、keyboard 操作可能な detail link を提供する。
- Mobile / tablet で horizontal scroll がある場合は視覚的に示す。

## 10. Phase 14B への引継ぎ

Phase 14B は本書の token と wireframe を MUI theme / component に実装する。実装中に visual rule を変更した場合は Storybook 相当の独立 catalog を新規導入するのではなく、まず本書と component test を更新する。
