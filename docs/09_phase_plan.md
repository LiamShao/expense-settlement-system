# 開発フェーズ計画

## 1. 方針

後続開発は、文書中心の waterfall 形式を学習するため、以下の順序で進める。

```text
要件確認
  ↓
基本設計・詳細設計更新
  ↓
API / DB / 権限 / 状態遷移仕様更新
  ↓
テスト仕様作成
  ↓
実装
  ↓
テスト実行
  ↓
エビデンス更新
```

## 2. 完了済みフェーズ

| Phase | 内容 | 状態 |
|---|---|---|
| Phase 0 | Docker Java 開発環境 | 完了 |
| Phase 1 | Spring Boot project skeleton | 完了 |
| Phase 2 | DB schema / Flyway migration | 完了 |
| Phase 3 | Entity / DTO / Mapper | 完了 |
| Phase 4 | Spring Security login/auth | 完了 |
| Phase 5 | 経費申請 CRUD | 完了 |
| Phase 6 | 申請・承認・差戻し workflow | 完了 |
| Phase 6.5 | 現状仕様書整理 | 完了 |
| Phase 7 | ADMIN 全件参照 / 監査ログ | 完了 |
| Phase 8 | Global Exception Handler | 完了 |
| Phase 9 | OpenAPI / Swagger 詳細化 | 完了 |
| Phase 10 | テストコード拡充 | 完了 |
| Phase 11 | 結合テスト / エビデンス | 完了 |
| Phase 12 | CI design / production Dockerfile | 完了（remote workflow は削除済み） |
| Phase 13 | AWS architecture design | 完了 |
| Phase 14A | MUI UI design / backend prerequisite / frontend foundation | 完了 |
| Phase 14B | React frontend implementation / frontend test | 完了 |
| Phase 15 | Spring Session JDBC production authentication | 完了 |

## 3. 進行中フェーズ

現在、進行中のフェーズはない。

## 4. 後続ロードマップの優先順位

Phase 15 では Phase 14B の in-memory Basic authentication を廃止し、Spring Session JDBC、secure cookie policy、CSRF、session rotation/revocation、account lock、reload restore を実装した。Backend 50 tests、frontend 36 tests、production build、実 DB を利用した三 role Playwright E2E workflow が成功している。

Phase 16 以降は以下の優先順位と依存順で計画する。各 Phase の開始時に要件、設計、test specification を更新してから実装する。

| Priority | Phase | 内容 | 位置付け | 状態 |
|---|---|---|---|---|
| P0 / 必須 | Phase 15 | Production authentication | Internet 公開前に必要な security boundary | 完了 |
| P1 / 高 | Phase 16 | 領収書 file API / UI | 経費精算の中核業務を完成させる機能 | 次 Phase |
| P1 / 高 | Phase 17 | AWS Terraform IaC | 再現可能で review 可能な infrastructure 定義 | 未着手 |
| P1 / 高（条件付き） | Phase 18 | AWS deployment / production readiness | 実環境の deploy、監視、復旧を検証する Phase | 未着手 |
| P2 / 任意 | Phase 19 以降 | Product / operation enhancement | 主線完了後に価値と費用で選択する backlog | 未定義 |

Phase 15 → 16 → 17 → 18 を基本順序とする。Phase 18 は AWS account、Region、domain、traffic、RTO/RPO、data retention、月額予算、通知先が確定し、resource 作成と費用発生について明示的な承認を得た場合だけ開始する。

## 5. Phase 15: Production authentication

状態: 2026-07-20 完了。

### 5.1 重要度

P0 / 必須。現行の in-memory HTTP Basic credential は local learning environment では利用できるが、Internet-facing production へ公開する authentication としては採用しない。

### 5.2 Scope

- 現行 PostgreSQL user と role を継続利用し、password hash、account status、login failure policy を設計する。
- HTTP Basic header を廃止し、`HttpOnly`、production `Secure`、適切な `SameSite` 属性を持つ cookie authentication へ移行する。
- 第一候補は Spring Session JDBC とし、session state を ECS task memory/local disk に保持せず PostgreSQL へ外部化する。OIDC など外部 IdP が必須になった場合は実装前に再評価する。
- Login、logout、current-user、session expiry、session revocation、session fixation、CSRF を backend/frontend 共通契約として定義する。
- Password、session ID、CSRF secret、Authorization value を browser storage、URL、application log、analytics に保存・出力しない。
- Login failure、expired session、401、403 の一貫した UI/API error handling を提供する。
- Backend integration test、frontend component/integration test、real-DB Playwright role workflow を更新する。
- Requirements、basic design、API/OpenAPI、DB、security、test、evidence、AWS design を実装結果に合わせる。

### 5.3 Completion criteria

- Browser reload 後も有効 session を安全に復元でき、logout 後の session は再利用できない。
- CSRF、session fixation、unauthenticated/unauthorized access、role boundary、account disabled/locked の test が成功する。
- Frontend JavaScript が password または再利用可能な authentication token を保持し続けない。
- 複数 ECS task または task replacement を想定しても container local session state に依存しない。
- Phase 0–14B regression を含む backend、frontend、E2E test と production build が成功する。

上記 criteria は backend integration test、frontend MSW test、production build、real PostgreSQL Playwright E2E により確認済みである。

## 6. Phase 16: 領収書 file API / UI

### 6.1 重要度

P1 / 高。DB の `receipt_object_key` 文字列入力だけではなく、利用者が実際の領収書画像/PDFを登録し、権限のある申請者・承認者・管理者が確認できる状態にする。

### 6.2 Scope

- Application が生成する object key と file metadata を管理し、利用者入力を storage key や download URL として信用しない。
- Upload、metadata 取得、authorized download/preview、delete/replace API を定義する。
- 申請 ownership、review permission、role、application status に基づいて read/write/delete を backend で認可する。
- 許可 Content-Type/extension、maximum size、file name、checksum、malware scan、retention、audit event を要件化する。
- Local/test storage adapter と private S3 adapter を分離し、test が実 AWS account を必須としない構成にする。
- Frontend の object key 手入力を file selection、upload progress、preview/download、validation/error UI に置き換える。
- API integration test、storage adapter test、frontend test、E2E を追加する。

### 6.3 Completion criteria

- 未認証者、他人の非公開申請、権限外 role が file content または storage key を取得できない。
- 不正形式、size over、path/key injection、存在しない file、途中失敗時の orphan metadata/object を test する。
- Private storage、encryption、public access block、short-lived access の設計が local と AWS で一貫する。
- 申請作成から領収書 upload、承認者確認までの real-DB E2E が成功する。

## 7. Phase 17: AWS Terraform IaC

### 7.1 重要度

P1 / 高。Phase 13 の AWS architecture を手作業ではなく version-controlled infrastructure code として再現可能にする。Terraform を採用し、本 Phase だけでは AWS resource を作成しない。

### 7.2 Scope

- State bootstrap と environment configuration の責務を分離する。
- VPC、public/private subnet、route、security group、ALB、ECS/Fargate、ECR、RDS PostgreSQL、private S3、Secrets Manager、IAM、CloudWatch、alarm、budget guardrail を module 化する。
- Production availability と低費用の検証環境を同一設定値で混同せず、environment ごとの差を明示する。
- Secret value を Terraform source、variable default、plan output、Git history に含めない。
- Resource naming、tag、encryption、backup、deletion protection、log retention、least privilege を code 化する。
- `terraform fmt`、`validate`、lint、security scan、policy/check test と review 手順を整備する。
- Expected monthly cost と destroy 後も残る cost/data を apply 前 checkpoint として文書化する。

### 7.3 Completion criteria

- Clean environment で IaC validation が再現し、Phase 13 design との対応を review できる。
- Public RDS/S3、過剰 IAM、平文 secret、意図しない destroy を static check と review gate で検出できる。
- `terraform apply` は未実行で、AWS resource と費用が発生していないことを明記する。
- Apply、rollback、state recovery、import、destroy protection の手順が文書化される。

## 8. Phase 18: AWS deployment / production readiness

### 8.1 重要度

P1 / 高。ただし AWS account、budget、domain、production requirement と resource 作成の承認が入口条件である。

### 8.2 Scope

- 承認済み environment に Terraform plan/apply を実施し、ECR、ECS、RDS、S3、ALB、monitoring を構築する。
- Immutable image build/push、Flyway migration、ECS rolling deployment、health verification、automatic rollback を確認する。
- JDBC TLS、secret injection/rotation、graceful shutdown、structured production log、correlation ID を検証する。
- Alarm notification、task replacement、deployment failure、DB backup/restore、Multi-AZ failover、S3 access boundary を試験する。
- Deploy、rollback、incident response、backup restore、credential rotation、cost monitoring の runbook を作成する。
- Remote CI/CD は別 decision gate とし、利用者の明示的な方針変更なしに削除済み workflow を復元しない。

### 8.3 Completion criteria

- Production readiness review が security、availability、recovery、operation、cost の各観点で完了する。
- Application workflow と role/file access が HTTPS endpoint で成功する。
- Rollback と restore を手順書だけでなく実環境で検証し、evidence を残す。
- 作成 resource、継続費用、停止/削除手順、残存 data を利用者へ引き渡す。

## 9. Phase 19 以降の任意拡張 backlog

主線へ自動的に追加せず、Phase 18 完了後または明確な業務要求が出た時点で value、risk、cost を比較して選択する。

| Priority within backlog | Candidate | 採用条件 |
|---|---|---|
| P2-A | OIDC / MFA / enterprise SSO | 組織 IdP、MFA、federation requirement がある。 |
| P2-A | User administration / password reset | 実運用で account lifecycle を application 内から管理する。 |
| P2-B | Notification | 申請、承認、差戻しの email/chat 通知が業務上必要である。 |
| P2-B | Authentication / reference audit enhancement | Login failure、file access、管理操作の追跡要件を強化する。 |
| P2-C | Performance / accessibility / analytics | 実測値、利用者 feedback、SLO に基づく改善根拠がある。 |

## 10. 共通 guardrail

- 各 Phase は requirements/design/test specification → implementation → verification/evidence の順で実施する。
- Phase の完了は test、evidence、documentation alignment を含み、code 実装だけでは完了としない。
- AWS resource、external service、費用、remote workflow、secret を伴う操作は、その Phase の計画だけを根拠に自動実行しない。
- Remote CI は利用者の判断で削除済みのため、明示的な方針変更なしに復元しない。
