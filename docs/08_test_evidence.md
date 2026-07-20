# テストエビデンス

Sections 1–4 は Phase 11 時点の baseline evidence である。最新の full regression は Section 9 の Phase 15 evidence を参照する。

## 1. 実行環境

| 項目 | 内容 |
|---|---|
| 実行日 | 2026-07-18 |
| 実行環境 | Docker Compose / Testcontainers |
| Java | Java 17 |
| Gradle | Gradle 8.10.2 |
| DB | PostgreSQL 16 Testcontainer |

## 2. 実行コマンド

```bash
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon test --rerun-tasks
```

## 3. 実行結果

```text
BUILD SUCCESSFUL
42 tests, 0 failures, 0 errors
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
| UT-REV-001 | `searchReviews_正常系_APPROVERは他人の申請中を検索できる` | OK | Review 一覧と status 表示名を確認。 |
| UT-REV-002 | `searchReviews_異常系_USERは検索できない` | OK | USER の Review 検索禁止を確認。 |
| UT-REV-003 | `getReviewById_正常系_APPROVERは他人の申請中詳細を参照できる` | OK | APPROVER の承認対象詳細参照を確認。 |
| UT-REV-004 | `getReviewById_異常系_自分の申請は参照できない` | OK | Review API からの自己申請参照禁止を確認。 |
| UT-AMT-001 | `create_異常系_合計金額がDB上限を超える` | OK | 合計上限超過時に DB insert しないことを確認。 |
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
| CT-AMT-001 | `create_異常系_金額の小数は許可しない` | OK | 整数円 validation と field path を確認。 |
| CT-REV-001 | `search_正常系_承認待ち申請を返す` | OK | Review query と page response の変換を確認。 |
| IT-AUTH-001 | `login_結合テスト_DBユーザーでログインできる` | OK | Flyway seed user と BCrypt password によるログインを確認。 |
| IT-AUTH-002 | `me_結合テスト_Basic認証ユーザーをDBから取得する` | OK | Basic 認証と DB ユーザー取得を確認。 |
| IT-EXP-001 | `create_結合テスト_申請と明細と監査ログをDBへ保存する` | OK | ヘッダ、明細、合計金額、作成ログの永続化を確認。 |
| IT-EXP-002 | `search_結合テスト_USERは本人のみADMINは全件参照できる` | OK | USER の本人限定検索と ADMIN の全件検索を確認。 |
| IT-EXP-003 | `workflow_結合テスト_作成から申請と承認まで遷移する` | OK | DRAFT、SUBMITTED、APPROVED の遷移と承認者保存を確認。 |
| IT-EXP-004 | `getById_結合テスト_USERは他人の申請を参照できない` | OK | 実 DB データに対する所有者権限を確認。 |
| IT-REV-001 | `review_結合テスト_APPROVERは他人の申請中だけ参照できる` | OK | Review 一覧・詳細、自己除外、USER 禁止を実 DB で確認。 |
| IT-AUD-001 | `auditLog_結合テスト_ADMINは業務操作ログを検索できる` | OK | 作成、申請、承認ログの保存と ADMIN 検索を確認。 |
| IT-AUD-002 | `auditLog_結合テスト_USERは監査ログを参照できない` | OK | USER の監査ログ参照禁止を確認。 |

## 5. 未実施・後続確認

| 項目 | 理由 |
|---|---|
| Controller API 全 endpoint・全分岐テスト | Phase 11 では主要業務フローを対象としたため、更新、削除、差戻しの全分岐は単体テストで確認している。 |
| 性能・負荷テスト | 現フェーズは機能結合テストを対象とするため。 |

## 6. Phase 12 local verification

| 確認項目 | 結果 |
|---|---|
| production multi-stage image build | OK (`expense-settlement-system:phase12`) |
| runtime user | OK (`spring:spring`) |
| PostgreSQL / Flyway 接続 | OK |
| `GET /actuator/health` | 200 / `UP` |
| full regression | 34 tests、0 failures、0 errors、0 skipped |
| GitHub Actions workflow 構文 | YAML parse、`git diff --check` OK |

production image は local Docker Desktop で build・起動確認した。GitHub Actions workflow は後に remote から削除され、現在は ignore された local-only file のため remote run は未実施である。

## 7. Phase 14A frontend foundation verification

| 確認項目 | 結果 |
|---|---|
| ESLint | OK |
| TypeScript typecheck | OK |
| Vitest foundation test | 1 test、0 failures |
| Vite production build | OK、898 modules transformed |

実行コマンド:

```bash
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## 8. Phase 14B frontend application verification

実行日時: 2026-07-18 20:15 JST

| 確認項目 | 結果 |
|---|---|
| 実行環境 | Node.js 23.9.0 / pnpm 10.5.2 / Playwright 1.61.1 / Chromium 149 |
| ESLint | OK、warning なし |
| TypeScript typecheck | OK |
| Vitest / Testing Library / MSW | 3 files、34 tests、0 failures |
| Role / status matrix | USER / APPROVER / ADMIN × DRAFT / SUBMITTED / APPROVED / RETURNED の本人・他人操作を table-driven test で確認 |
| Authentication integration | Login → `/api/auth/me` → Basic 認証付き一覧取得、localStorage / sessionStorage 非保存を MSW で確認 |
| Form component | 新規申請の件名、利用日、金額、内容の必須 validation を確認 |
| Vite production build | OK、1069 modules transformed、business route / application layout を lazy-loaded chunk に分割 |
| Playwright E2E | Chromium 1 scenario passed、test 21.5 秒 / run 25.0 秒 |

Playwright は Docker PostgreSQL 16.14 と Spring Boot API を実際に起動し、次の serial workflow を確認した。

1. USER が経費申請を作成、編集、申請する。
2. USER が別申請を作成、申請する。
3. APPROVER が 1 件を承認し、1 件を理由付きで差戻す。
4. USER が差戻し状態と差戻し理由を確認する。
5. ADMIN が承認・差戻し action の監査ログを検索する。

E2E の初回調整では deep link login と logout の navigation race を検出し、元 route 復帰と logout 順序を修正した。最終 regression は修正後の source で成功した。E2E は一意な件名を使用するため再実行可能だが、local `postgres-data` volume に申請と監査ログを追加する。

実行コマンド:

```bash
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm e2e
```

Phase 14B closure verification（2026-07-18 21:01 JST）:

| 確認項目 | 結果 |
|---|---|
| ESLint | OK、warning なし |
| TypeScript typecheck | OK |
| Vitest / Testing Library / MSW | 3 files、35 tests、0 failures |
| Logout confirmation | cancel では session / current route を維持し、confirm で `/login` へ遷移することを確認 |
| Vite production build | OK、1069 modules transformed |
| Backend full regression | 42 tests、0 failures、0 errors、0 skipped、`BUILD SUCCESSFUL` |
| Docker Compose smoke | PostgreSQL health 完了後に Spring Boot が自動起動し、backend health が `UP` |
| Playwright E2E | Chromium 1 scenario passed、test 24.5 秒 / run 29.3 秒。logout confirmation を含む全 role workflow を確認 |
| Local E2E data | `E2E%` 申請 12 → 14 件、関連監査ログ 41 → 48 件。既存 data は削除していない |

Backend full regression の初回実行は、起動中の `backend` と test container が共有 Gradle cache を使用したため lock timeout で build 開始前に失敗した。`backend` を停止して同一 test command を再実行し、42 tests が成功した。これは code / test failure ではなく local process contention であり、README の test 手順にも backend を先に停止する前提を記載している。

## 9. Phase 15 production authentication verification

実行日時: 2026-07-20 19:35–19:57 JST

| 確認項目 | 結果 |
|---|---|
| Backend full regression | 50 tests、0 failures、0 errors、`BUILD SUCCESSFUL` |
| Authentication integration | Spring Session JDBC persistence、`HttpOnly` / `SameSite=Lax` cookie、login 時 session ID rotation、logout 後の旧 session 拒否を確認 |
| Cookie profile | Default は `Secure=true`、`local` profile だけ `Secure=false`。Local response は cookie 値を伏せた header で `HttpOnly; SameSite=Lax` を確認 |
| Security integration | HTTP Basic 拒否、CSRF 不足時の `CSRF_INVALID`、5 回失敗時の 15 分 lock、role boundary を確認 |
| ESLint | OK、warning なし |
| TypeScript typecheck | OK |
| Vitest / Testing Library / MSW | 3 files、36 tests、0 failures |
| Frontend authentication | CSRF header、same-origin cookie mode、reload session restore、server logout 後の state clear を確認 |
| Vite production build | OK、1069 modules transformed |
| Docker Compose / Flyway | PostgreSQL と backend が healthy、V4 migration `add production authentication` 適用成功 |
| `GET /actuator/health` | 200 / `UP` |
| Playwright E2E | Chromium 1 scenario passed、test 22.6 秒 / run 28.4 秒 |
| E2E authentication | USER login 後の browser reload、各 role の server logout、三 role workflow を real PostgreSQL API で確認 |
| Local E2E data | Phase 15 の二回の最終確認で `E2E%` 申請 14 → 18 件、関連監査ログ 48 → 62 件。既存 data は削除していない |

Backend test command:

```bash
docker compose run --rm --no-deps --user 1000:0 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e 'JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dhttps.protocols=TLSv1.2 -Djdk.tls.client.protocols=TLSv1.2' \
  -v /var/run/docker.sock:/var/run/docker.sock \
  java-dev ./gradlew --no-daemon --console=plain test --rerun-tasks
```

Local の JDK 17.0.13 image では Gradle dependency download が TLS handshake error になったため、この実行では TLS 1.2 を明示した。Dependency 解決後の compile、50 tests、Testcontainers PostgreSQL は成功しており、code/test failure ではない。

Frontend / real API verification:

```bash
cd frontend
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm e2e
```

最初の E2E 確認では末尾の ADMIN logout を追加前だったため、その session record だけが idle timeout まで local DB に残った。最終 E2E は全 role を logout し、旧 session の再利用拒否は backend integration test でも確認した。

## 10. Phase 9 実行時確認

| 確認項目 | 結果 |
|---|---|
| `GET /openapi.yaml` | 200 OK |
| `GET /swagger-ui.html` | 200 OK |
| Swagger config の契約 URL | `/openapi.yaml` |
| Docker Compose Mock profile 構文 | OK |
| Prism Mock Server 起動 | 12 operations を認識して起動 |
| Mock 未認証リクエスト | 401 Unauthorized |
| Mock Basic Auth リクエスト | 200 OK |

## 11. 補足

本エビデンスは自動テスト実行結果の要約である。SIer 形式の詳細エビデンスとして提出する場合は、対象テスト、入力値、期待値、実行結果、ログまたはスクリーンショットをケース単位で追加する。
