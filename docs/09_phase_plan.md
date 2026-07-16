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
| Phase 12 | GitHub Actions / production Dockerfile | 完了 |
| Phase 13 | AWS architecture design | 完了 |
| Phase 14A | MUI UI design / backend prerequisite / frontend foundation | 完了 |

## 3. 進行中フェーズ

| Phase | 内容 | 文書先行タスク |
|---|---|---|
| Phase 14B | React frontend implementation | 共通基盤、業務画面、frontend test を実装する。 |

## 4. 次に実施する作業

Phase 14A では frontend 機能設計、MUI UI 仕様・wireframe、Review API、金額 validation、React / MUI foundation を完了した。Phase 14B で次を実施する。

- MUI theme、application layout、router、query、HTTP Basic authentication state、API client を実装する。
- Login、申請一覧・詳細・作成・編集、承認待ち、監査ログを実装する。
- Unit、component、API mock integration、主要 workflow の E2E test を実行し evidence を更新する。
