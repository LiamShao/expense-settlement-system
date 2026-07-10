# テストエビデンス

## 1. 実行環境

| 項目 | 内容 |
|---|---|
| 実行日 | 2026-07-10 |
| 実行環境 | Docker Compose |
| Java | Java 17 |
| Gradle | Gradle 8.10.2 |
| DB | PostgreSQL 16 container |

## 2. 実行コマンド

```bash
docker compose run --rm java-dev ./gradlew test
```

## 3. 実行結果

```text
BUILD SUCCESSFUL
```

## 4. 確認済みテスト

| No | テスト名 | 結果 | 備考 |
|---|---|---|---|
| UT-EXP-001 | `submit_正常系_下書きを申請中にする` | OK | 下書きから申請中への遷移を確認。 |
| UT-EXP-002 | `approve_正常系_承認者が申請中を承認する` | OK | 承認者による承認処理を確認。 |
| UT-EXP-003 | `approve_異常系_自分の申請は承認できない` | OK | 自己承認禁止を確認。 |
| UT-EXP-004 | `returnApplication_異常系_USERは差戻しできない` | OK | USER の差戻し禁止を確認。 |
| UT-ADM-001 | `search_正常系_ADMINは全件検索できる` | OK | ADMIN は applicantId を強制設定されず検索できることを確認。 |
| UT-ADM-002 | `getById_正常系_ADMINは他人の申請詳細を参照できる` | OK | ADMIN の他人申請詳細参照を確認。 |
| UT-AUD-001 | `submit_正常系_監査ログを登録する` | OK | 申請操作時の監査ログ登録を確認。 |
| UT-AUD-002 | `search_正常系_ADMINは監査ログを検索できる` | OK | ADMIN の監査ログ検索を確認。 |
| UT-AUD-003 | `search_異常系_USERは監査ログを検索できない` | OK | USER の監査ログ検索禁止を確認。 |
| APP-001 | `main_正常系_アプリケーションクラスを参照できる` | OK | アプリケーションクラス参照を確認。 |

## 5. 未実施・後続確認

| 項目 | 理由 |
|---|---|
| Controller API テスト | MockMvc テスト未作成のため。 |
| DB integration test | Testcontainers または実 DB を利用したテスト未作成のため。 |
| Global Exception Handler のレスポンス形式確認 | 機能未実装のため。 |

## 6. 補足

本エビデンスは自動テスト実行結果の要約である。SIer 形式の詳細エビデンスとして提出する場合は、対象テスト、入力値、期待値、実行結果、ログまたはスクリーンショットをケース単位で追加する。
