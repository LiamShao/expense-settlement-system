# テストエビデンス

## 1. 実行環境

| 項目 | 内容 |
|---|---|
| 実行日 | 2026-07-13 |
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
26 tests, 0 failures, 0 errors
```

## 4. 確認済みテスト

| No | テスト名 | 結果 | 備考 |
|---|---|---|---|
| UT-EXP-001 | `submit_正常系_下書きを申請中にする` | OK | 下書きから申請中への遷移を確認。 |
| UT-EXP-002 | `approve_正常系_承認者が申請中を承認する` | OK | 承認者による承認処理を確認。 |
| UT-EXP-003 | `approve_異常系_自分の申請は承認できない` | OK | 自己承認禁止を確認。 |
| UT-EXP-004 | `returnApplication_異常系_USERは差戻しできない` | OK | USER の差戻し禁止を確認。 |
| UT-EXP-005 | `update_正常系_下書きのヘッダと明細を更新する` | OK | ヘッダ、合計金額、明細の更新を確認。 |
| UT-EXP-006 | `update_異常系_申請中は更新できない` | OK | 申請中データの更新禁止を確認。 |
| UT-EXP-007 | `getById_異常系_他人の申請は参照できない` | OK | 他人の申請に対する参照禁止を確認。 |
| UT-EXP-008 | `approve_異常系_下書きは承認できない` | OK | 下書き状態の承認禁止を確認。 |
| UT-EXP-009 | `returnApplication_正常系_承認者が申請中を差戻す` | OK | 差戻し状態、理由、監査ログ登録を確認。 |
| UT-ADM-001 | `search_正常系_ADMINは全件検索できる` | OK | ADMIN は applicantId を強制設定されず検索できることを確認。 |
| UT-ADM-002 | `getById_正常系_ADMINは他人の申請詳細を参照できる` | OK | ADMIN の他人申請詳細参照を確認。 |
| UT-AUD-001 | `submit_正常系_監査ログを登録する` | OK | 申請操作時の監査ログ登録を確認。 |
| UT-AUD-002 | `search_正常系_ADMINは監査ログを検索できる` | OK | ADMIN の監査ログ検索を確認。 |
| UT-AUD-003 | `search_異常系_USERは監査ログを検索できない` | OK | USER の監査ログ検索禁止を確認。 |
| APP-001 | `main_正常系_アプリケーションクラスを参照できる` | OK | アプリケーションクラス参照を確認。 |
| CT-ERR-001 | `create_異常系_Validationエラーを統一形式で返す` | OK | 400、エラーコード、項目詳細を確認。 |
| CT-ERR-002 | `create_異常系_不正なJSONを統一形式で返す` | OK | 不正 JSON の共通レスポンスを確認。 |
| CT-ERR-003 | `getById_異常系_ResponseStatusExceptionを統一形式で返す` | OK | Service の status と業務メッセージ維持を確認。 |
| CT-ERR-004 | `search_異常系_未認証は統一形式で返す` | OK | Security Filter の 401 JSON を確認。 |
| CT-ERR-005 | `handle_異常系_権限不足を統一形式で返す` | OK | Security の 403 JSON を確認。 |
| CT-ERR-006 | `getById_異常系_未処理例外の詳細を公開しない` | OK | 500 response に内部メッセージを含めないことを確認。 |
| CT-API-001 | `openApi_正常系_正式YAMLが静的Resourceへコピーされる` | OK | 正式 YAML と build artifact が一致することを確認。 |
| CT-API-002 | `openApi_正常系_実装Endpointと共通契約を定義する` | OK | 全 path、Basic Auth、共通エラー schema を確認。 |
| CT-API-003 | `openApi_正常系_operationIdと認証と共通500Responseを定義する` | OK | operationId の一意性、認証、500 response を確認。 |
| CT-CTRL-001 | `login_正常系_認証情報とユーザーを返す` | OK | Login API の正常レスポンスを確認。 |
| CT-CTRL-002 | `create_正常系_作成した経費申請を返す` | OK | Create API の入力変換と正常レスポンスを確認。 |
| CT-CTRL-003 | `search_正常系_検索条件と監査ログ一覧を返す` | OK | 監査ログ検索条件とページングレスポンスを確認。 |

## 5. 未実施・後続確認

| 項目 | 理由 |
|---|---|
| Controller API 全 endpoint テスト | Phase 10 で主要 Controller の正常系を追加済み。残りの endpoint は Phase 11 の結合テストで確認する。 |
| DB integration test | Testcontainers または実 DB を利用したテスト未作成のため。 |

## 6. Phase 9 実行時確認

| 確認項目 | 結果 |
|---|---|
| `GET /openapi.yaml` | 200 OK |
| `GET /swagger-ui.html` | 200 OK |
| Swagger config の契約 URL | `/openapi.yaml` |
| Docker Compose Mock profile 構文 | OK |
| Prism Mock Server 起動 | 12 operations を認識して起動 |
| Mock 未認証リクエスト | 401 Unauthorized |
| Mock Basic Auth リクエスト | 200 OK |

## 7. 補足

本エビデンスは自動テスト実行結果の要約である。SIer 形式の詳細エビデンスとして提出する場合は、対象テスト、入力値、期待値、実行結果、ログまたはスクリーンショットをケース単位で追加する。
