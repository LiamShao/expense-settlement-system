# エラーハンドリング設計書

## 1. 目的

Controller、Service、Spring Security で発生するエラーを同一の JSON 契約で返し、クライアントが HTTP status と機械可読な `code` によって処理を判断できるようにする。

## 2. レスポンス設計

共通項目は以下とする。

| 項目 | 型 | 必須 | 内容 |
|---|---|---|---|
| `success` | boolean | YES | エラー時は常に `false` |
| `code` | string | YES | 機械判定用エラーコード |
| `message` | string | YES | 利用者向けメッセージ |
| `details` | array | NO | Validation の項目名とメッセージ |
| `path` | string | YES | エラーが発生したリクエスト URI |
| `timestamp` | datetime | YES | エラー応答の生成日時 |

## 3. 例外マッピング

| 例外・発生箇所 | HTTP status | code | 処理 |
|---|---:|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | Request body の項目エラーを `details` に設定 |
| `BindException` | 400 | `VALIDATION_ERROR` | Query / ModelAttribute の項目エラーを `details` に設定 |
| `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | 制約違反を `details` に設定 |
| `HttpMessageNotReadableException` | 400 | `INVALID_REQUEST` | 不正 JSON、日付、列挙値を共通メッセージ化 |
| `MethodArgumentTypeMismatchException` | 400 | `INVALID_REQUEST` | Path / Query の型不正を共通メッセージ化 |
| `ResponseStatusException` | 指定値 | HTTP status 名 | Service の status と業務メッセージを維持 |
| `AuthenticationException` | 401 | `UNAUTHORIZED` | 認証失敗を共通メッセージ化 |
| `AccessDeniedException` | 403 | `FORBIDDEN` | 権限不足を共通メッセージ化 |
| その他の `Exception` | 500 | `INTERNAL_SERVER_ERROR` | 詳細はログだけに出力し、レスポンスへ公開しない |

## 4. Spring Security

Security Filter 内の例外は `@RestControllerAdvice` の対象外であるため、以下を `SecurityFilterChain` に設定する。

- `RestAuthenticationEntryPoint`: 未認証時の 401 JSON を生成する。
- `RestAccessDeniedHandler`: 認証済みユーザーの権限不足時の 403 JSON を生成する。

## 5. ログ・セキュリティ

- 予期しない例外は URI と stack trace をサーバーログへ記録する。
- 500 response には例外クラス、stack trace、SQL、credential を含めない。
- Validation や業務エラーはクライアント起因のため stack trace をログ出力しない。
