# 単体テスト仕様書

## 1. 目的

Service 層の業務ルールを JUnit 5 / Mockito で検証する。DB 接続は行わず、Mapper は mock とする。

## 2. テスト対象

| 対象クラス | 概要 |
|---|---|
| `ExpenseApplicationService` | 経費申請 CRUD、申請、承認、差戻しの業務ルール |

## 3. テスト方針

| 観点 | 内容 |
|---|---|
| 正常系 | 想定される入力で正しいレスポンスと Mapper 呼び出しになること。 |
| 異常系 | 権限違反、ステータス不正、自己承認などで例外になること。 |
| 権限 | USER / APPROVER / ADMIN の操作可否を確認すること。 |
| 状態遷移 | `DRAFT`, `SUBMITTED`, `APPROVED`, `RETURNED` の遷移条件を確認すること。 |
| DB 更新 | 実 DB ではなく、Mapper の更新メソッド呼び出し有無で確認すること。 |

## 4. テストケース

| No | 要件ID | テスト名 | 前提 | 操作 | 期待結果 | 実装状況 |
|---|---|---|---|---|---|---|
| UT-EXP-001 | REQ-WF-001 | `submit_正常系_下書きを申請中にする` | 申請者本人、対象は `DRAFT` | `submit` を実行 | `SUBMITTED` を返し、`updateStatusToSubmitted` が呼ばれる | 実装済み |
| UT-EXP-002 | REQ-WF-002 | `approve_正常系_承認者が申請中を承認する` | APPROVER、対象は他人の `SUBMITTED` | `approve` を実行 | `APPROVED` を返し、`updateStatusToApproved` が呼ばれる | 実装済み |
| UT-EXP-003 | REQ-WF-004 | `approve_異常系_自分の申請は承認できない` | APPROVER、対象は自分の `SUBMITTED` | `approve` を実行 | `400 BAD_REQUEST` が発生し、更新処理は呼ばれない | 実装済み |
| UT-EXP-004 | REQ-WF-003 | `returnApplication_異常系_USERは差戻しできない` | USER、対象は他人の `SUBMITTED` | `returnApplication` を実行 | `403 FORBIDDEN` が発生し、更新処理は呼ばれない | 実装済み |
| UT-EXP-005 | REQ-EXP-004 | 更新正常系 | 申請者本人、対象は `DRAFT` | `update` を実行 | ヘッダと明細が更新される | 未実装 |
| UT-EXP-006 | REQ-EXP-004 | 更新異常系 | 申請者本人、対象は `SUBMITTED` | `update` を実行 | `400 BAD_REQUEST` が発生する | 未実装 |
| UT-EXP-007 | REQ-EXP-003 | 参照異常系 | 対象は他人の申請 | `getById` を実行 | `403 FORBIDDEN` が発生する | 未実装 |
| UT-EXP-008 | REQ-WF-002 | 承認異常系 | APPROVER、対象は `DRAFT` | `approve` を実行 | `400 BAD_REQUEST` が発生する | 未実装 |
| UT-EXP-009 | REQ-WF-003 | 差戻し正常系 | APPROVER、対象は他人の `SUBMITTED` | `returnApplication` を実行 | `RETURNED` を返し、差戻し理由が保存される | 未実装 |

## 5. 実行方法

```bash
docker compose run --rm java-dev ./gradlew test
```

## 6. 判定基準

- Gradle test が `BUILD SUCCESSFUL` で終了すること。
- 異常系では期待する HTTP status を持つ `ResponseStatusException` が発生すること。
- 更新してはいけないケースでは、対象 Mapper の更新メソッドが呼ばれないこと。
