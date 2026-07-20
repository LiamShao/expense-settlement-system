# ADR-001: Production authentication に Spring Session JDBC を採用する

## Status

Accepted

## Date

2026-07-20

## Context

Phase 14B までの frontend は、メールアドレスとパスワードから HTTP Basic の `Authorization` header を生成し、React memory に保持して各 API request へ付与していた。この方式は学習用の local application としては単純だが、次の理由から internet-facing production の認証方式として採用しない。

- Password 相当の credential を request ごとに送信し続ける。
- Server-side logout、session expiry、session revocation を自然に扱えない。
- Browser reload 後に安全な認証状態を復元できない。
- ECS task replacement や複数 task 構成で、container local session state に依存できない。
- Browser application では CSRF、session fixation、cookie policy を含む一貫した security boundary が必要である。

本システムは React SPA と Spring Boot API を same-origin で提供し、主な利用者は社内の browser user とする。User、role、BCrypt password hash は既存 PostgreSQL の `users` table で管理している。

## Decision drivers

- Browser JavaScript と browser storage に password または再利用可能な session ID を保持しない。
- Login、current-user、logout、idle timeout、revocation を server が制御できる。
- ECS の複数 task と task replacement に対応できる。
- 既存の Spring Security、PostgreSQL、Flyway、MyBatis 構成と整合する。
- Role/ownership authorization を既存 backend の security boundary として維持する。
- Local development と production で同じ authentication model を利用する。
- OIDC provider を必須とせず、Phase 15 の範囲と運用コストを抑える。

## Considered options

### Option 1: HTTP Basic を継続する

実装は最も単純だが、credential を各 request で再送し、明示的な session revocation や安全な reload restore を提供しにくい。Production 要件を満たさないため不採用とする。

### Option 2: Access token / refresh token を用いた JWT

Stateless API や複数種類の API client には適するが、本システムは same-origin browser application が中心である。JWT を採用すると、token 保管、refresh rotation、reuse detection、revocation、key rotation、logout semantics が追加で必要になる。

`HttpOnly` cookie に JWT を格納しても CSRF 対策は必要であり、即時 logout/revocation のために denylist や token version を導入すると、stateless の利点も小さくなる。Phase 15 では複雑性に対する業務上の利点がないため不採用とする。

### Option 3: Spring Session JDBC

Opaque session ID だけを cookie に保持し、認証状態を PostgreSQL に外部化できる。Spring Security の session fixation protection、CSRF、logout と統合しやすく、既存技術スタックにも合うため採用する。

### Option 4: OIDC / 社内 IdP

SSO、MFA、central account lifecycle が必須の場合は有力である。ただし現時点では IdP、tenant、claim mapping、運用責任が未確定であり、Phase 15 の必須条件ではないため後続判断とする。

## Decision

Production authentication は Spring Security と Spring Session JDBC による server-side session 方式とする。

- Session state は PostgreSQL の `spring_session` と `spring_session_attributes` に保存し、ECS task memory/local disk に保持しない。
- Browser は opaque な `SESSION` cookie のみを保持する。
- Cookie は `HttpOnly`、`SameSite=Lax` とし、default は `Secure=true` とする。Loopback HTTP を使う `local` profile だけ `Secure=false` を許容する。
- Session idle timeout は 30 分とする。
- Login 時に session ID を rotation し、SecurityContext を新しい session に保存する。
- Logout は current server-side session を無効化し、cookie を削除する。既に session が失効している場合も成功する idempotent operation とする。
- Unsafe method は session に紐づく CSRF token と `X-CSRF-TOKEN` header を必須とする。
- Frontend は CSRF token だけを module memory に保持し、password、session ID、Authorization value を browser storage、URL、log、analytics に保存しない。
- Application 起動時に `/api/auth/me` を呼び、有効期限内の session を復元する。
- HTTP Basic と form login は無効化する。
- 既存 PostgreSQL user と BCrypt password hash を継続利用する。
- 連続 5 回の login failure で account を 15 分間 lock し、login success 時に failure state を reset する。
- Account self-registration、password reset、MFA、OIDC は Phase 15 の対象外とする。

## Consequences

### Positive

- Password と session ID を frontend JavaScript から分離できる。
- Logout と server-side revocation を即時に反映できる。
- Browser reload 後も有効 session を復元できる。
- 複数 ECS task が同じ PostgreSQL session state を利用できる。
- Spring Security 標準の CSRF、session fixation、authorization と統合できる。
- JWT refresh/revocation infrastructure を追加せずに browser application の要件を満たせる。

### Negative

- 認証済み request は PostgreSQL session store に依存するため、DB availability と latency の影響を受ける。
- Session read/write と cleanup による DB load が増える。
- Session table の migration、index、expiry cleanup、backup/restore を運用対象に含める必要がある。
- Same-origin を前提とするため、frontend/API を別 origin に分離する場合は cookie、CORS、CSRF policy の再設計が必要になる。
- Current logout は current session のみを失効させる。管理者による全 device logout や user 単位の一括 revoke が必要な場合は追加設計が必要になる。

## Security and operational notes

- Production deploy では HTTPS を必須とし、`Secure=true` を解除しない。
- Session ID、CSRF token、password を application log、trace、analytics に出力しない。
- `spring_session` の cleanup と DB capacity を監視し、session row 増加、cleanup failure、DB latency を運用指標に含める。
- RDS backup/restore では business data と session data の性質が異なる。Restore 後に古い session を再有効化しない運用手順を Phase 18 までに定義する。
- Role と ownership の最終認可は引き続き backend で実施し、frontend route/button guard を security boundary としない。

## Validation

Phase 15 で以下を確認した。

- Backend 50 tests: Session persistence、cookie、CSRF、session rotation、logout、旧 session 拒否、HTTP Basic 拒否、account lock、role boundary。
- Frontend 36 tests: CSRF header、same-origin cookie mode、reload restore、server logout 後の state clear。
- Real PostgreSQL Playwright E2E: reload 後の session 継続と USER / APPROVER / ADMIN workflow。
- Production build、Flyway V4 migration、Docker Compose health。

詳細は [テストエビデンス](../08_test_evidence.md) を参照する。

## Revisit conditions

次のいずれかが発生した場合、この決定を再評価する。

- 社内 IdP、SSO、MFA、SCIM または central account lifecycle が必須になる。
- Mobile app、third-party client、public API など browser cookie 以外の client が追加される。
- Frontend と API を別 origin で公開する必要が生じる。
- Session DB load または availability が production SLO を満たさない。
- 複数 service 間で delegated authorization または standard token exchange が必要になる。
- 全 device logout、管理者による一括 revoke、concurrent session policy が業務要件になる。

## Related documents

- [要件定義書](../01_requirements.md)
- [基本設計書](../02_basic_design.md)
- [DB 定義書](../03_db_definition.md)
- [API 仕様書](../04_api_spec.md)
- [開発フェーズ計画](../09_phase_plan.md)
- [AWS アーキテクチャ設計書](../14_aws_architecture_design.md)
- [フロントエンド設計書](../15_frontend_design.md)
