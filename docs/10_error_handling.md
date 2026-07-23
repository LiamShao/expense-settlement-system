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
| `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | Endpoint が受理しない request Content-Type を共通化 |
| `ReceiptFileException` | 指定値 | File 専用 code | File validation/malware/storage failure を安定 code に変換 |
| `ResponseStatusException` | 指定値 | HTTP status 名 | Service の status と業務メッセージを維持 |
| `AuthenticationException` | 401 | `UNAUTHORIZED` | 認証失敗を共通メッセージ化 |
| `InvalidCsrfTokenException` / `MissingCsrfTokenException` | 403 | `CSRF_INVALID` | CSRF token の不足、不一致、期限切れを共通メッセージ化 |
| `AccessDeniedException` | 403 | `FORBIDDEN` | 権限不足を共通メッセージ化 |
| `MaxUploadSizeExceededException` | 413 | `FILE_TOO_LARGE` | Multipart limit 超過を file 用 message に変換 |
| Receipt file type/signature error | 400 / 415 | `INVALID_FILE` / `UNSUPPORTED_MEDIA_TYPE` | File 内容と allowlist を区別して返す |
| Receipt concurrent state error | 409 | `CONFLICT` | 最新 metadata の再取得を促す |
| Malware detected | 422 | `MALWARE_DETECTED` | File を active にせず一般化 message を返す |
| Storage/scanner unavailable | 503 | `FILE_SERVICE_UNAVAILABLE` | Provider detail を隠し retryable error とする |
| その他の `Exception` | 500 | `INTERNAL_SERVER_ERROR` | 詳細はログだけに出力し、レスポンスへ公開しない |

## 4. Spring Security

Security Filter 内の例外は `@RestControllerAdvice` の対象外であるため、以下を `SecurityFilterChain` に設定する。

- `RestAuthenticationEntryPoint`: 未認証時の 401 JSON を生成する。
- `RestAccessDeniedHandler`: CSRF error は `CSRF_INVALID`、その他の権限不足は `FORBIDDEN` の 403 JSON を生成する。
- `RestLogoutSuccessHandler`: Logout 成功時に共通 success JSON を生成する。

## 5. ログ・セキュリティ

- 予期しない例外は URI と stack trace をサーバーログへ記録する。
- 500 response には例外クラス、stack trace、SQL、credential を含めない。
- Application log には password、session ID、CSRF token を含めない。
- Receipt の original file name、storage key/path、checksum、binary content、scanner 内部 detail を application log に含めない。
- Validation や業務エラーはクライアント起因のため stack trace をログ出力しない。
