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

## 3. 進行中フェーズ

現在、進行中のフェーズはない。

## 4. 次に実施する作業

Phase 14B では MUI application、in-memory Basic authentication、全業務画面、logout confirmation を含む 35 frontend tests、実 DB を利用した三 role Playwright E2E workflow を完了し、最終 regression と local commit まで実施した。

次の実装 phase は未定義である。後続候補は production authentication（JWT / OIDC / secure cookie）、領収書 file API、AWS IaC / deployment 実装であり、開始前に要件と scope を確定する。Remote CI は利用者の判断で削除済みのため、明示的な方針変更なしに復元しない。
