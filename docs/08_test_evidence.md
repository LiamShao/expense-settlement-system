# テストエビデンス

## 1. 実行環境

| 項目 | 内容 |
|---|---|
| 実行日 | 2026-07-14 |
| 実行環境 | Docker Compose / Testcontainers |
| Java | Java 17 |
| Gradle | Gradle 8.10.2 |
| DB | PostgreSQL 16 Testcontainer |

## 2. 実行コマンド

```bash
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test
```

## 3. 実行結果

```text
BUILD SUCCESSFUL
34 tests, 0 failures, 0 errors
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
| IT-AUTH-001 | `login_結合テスト_DBユーザーでログインできる` | OK | Flyway seed user と BCrypt password によるログインを確認。 |
| IT-AUTH-002 | `me_結合テスト_Basic認証ユーザーをDBから取得する` | OK | Basic 認証と DB ユーザー取得を確認。 |
| IT-EXP-001 | `create_結合テスト_申請と明細と監査ログをDBへ保存する` | OK | ヘッダ、明細、合計金額、作成ログの永続化を確認。 |
| IT-EXP-002 | `search_結合テスト_USERは本人のみADMINは全件参照できる` | OK | USER の本人限定検索と ADMIN の全件検索を確認。 |
| IT-EXP-003 | `workflow_結合テスト_作成から申請と承認まで遷移する` | OK | DRAFT、SUBMITTED、APPROVED の遷移と承認者保存を確認。 |
| IT-EXP-004 | `getById_結合テスト_USERは他人の申請を参照できない` | OK | 実 DB データに対する所有者権限を確認。 |
| IT-AUD-001 | `auditLog_結合テスト_ADMINは業務操作ログを検索できる` | OK | 作成、申請、承認ログの保存と ADMIN 検索を確認。 |
| IT-AUD-002 | `auditLog_結合テスト_USERは監査ログを参照できない` | OK | USER の監査ログ参照禁止を確認。 |

## 5. 未実施・後続確認

| 項目 | 理由 |
|---|---|
| Controller API 全 endpoint・全分岐テスト | Phase 11 では主要業務フローを対象としたため、更新、削除、差戻しの全分岐は単体テストで確認している。 |
| 性能・負荷テスト | 現フェーズは機能結合テストを対象とするため。 |

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
