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

## 3. 後続フェーズ

| Phase | 内容 | 文書先行タスク |
|---|---|---|
| Phase 12 | GitHub Actions / production Dockerfile | CI 実行条件、成果物、失敗時対応を設計する。 |
| Phase 13 | AWS architecture design | ネットワーク、ECS、RDS、S3、Secrets、監視設計を文書化する。 |
| Phase 14 | React frontend | 画面一覧、画面遷移、項目定義、API 連携仕様を先に作成する。 |

## 4. 次に実施する作業

Phase 12 では、以下を実施する。

- pull request と `main` push を対象とする CI 実行条件を定義する。
- 単体テストと Testcontainers 結合テストを GitHub Actions で実行する。
- production 用 multi-stage Dockerfile を作成する。
- CI の実行結果と container image build 結果を記録する。
