# ドキュメント一覧

## 目的

本ディレクトリは、経費精算システムを日本の業務系開発で使われる文書中心の流れに近づけるための設計・テスト資料を管理する。

## ドキュメント構成

| No | 文書 | 目的 |
|---|---|---|
| 01 | [要件定義書](01_requirements.md) | システムの目的、利用者、機能要件、非機能要件を定義する。 |
| 02 | [基本設計書](02_basic_design.md) | アーキテクチャ、機能構成、処理概要を定義する。 |
| 03 | [DB定義書](03_db_definition.md) | テーブル、主要項目、制約、インデックスを定義する。 |
| 04 | [API仕様書](04_api_spec.md) | REST API のエンドポイント、認証、リクエスト、レスポンスを定義する。 |
| 05 | [権限マトリクス](05_authority_matrix.md) | ロール別に利用可能な操作を整理する。 |
| 06 | [状態遷移表](06_status_transition.md) | 経費申請ステータスの遷移条件を定義する。 |
| 07 | [単体テスト仕様書](07_unit_test_spec.md) | Service 層を中心とした単体テスト観点を定義する。 |
| 08 | [テストエビデンス](08_test_evidence.md) | 実行したテスト結果と確認内容を記録する。 |
| 09 | [開発フェーズ計画](09_phase_plan.md) | 後続開発を文書先行で進めるための計画を定義する。 |
| 10 | [エラーハンドリング設計書](10_error_handling.md) | 共通エラーレスポンスと例外マッピングを定義する。 |
| 11 | [OpenAPI / Swagger / Mock 利用手順](11_openapi_mock.md) | 正式契約、Swagger UI、Mock Server の利用方法を定義する。 |
| 12 | [API結合テスト仕様書](12_integration_test_spec.md) | PostgreSQL を利用した API 結合テストの対象、環境、ケースを定義する。 |
| 13 | [CI / production container 設計書](13_ci_container_design.md) | GitHub Actions と production image の実行条件、成果物、検証方法を定義する。 |
| 14 | [AWS アーキテクチャ設計書](14_aws_architecture_design.md) | AWS 上の network、compute、database、storage、security、monitoring、運用責任を定義する。 |
| 15 | [フロントエンド設計書](15_frontend_design.md) | React / TypeScript frontend の画面、navigation、項目、権限、API 連携、error、test 方針を定義する。 |
| 16 | [MUI UI デザイン仕様書](16_ui_design.md) | MUI theme、layout、component 方針、responsive rule、主要画面 wireframe を定義する。 |
| 17 | [領収書ファイル設計書](17_receipt_file_design.md) | Phase 16 の file validation、metadata/state、private storage、認可、API/UI、audit、test を定義する。 |
| ADR | [Architecture Decision Records](adr/README.md) | 重要な architecture decision、その選択理由、trade-off、再検討条件を記録する。 |
| API | [OpenAPI定義](openapi.yaml) | API の機械可読な契約を定義する。未実装 operation は `x-implementation-status: planned` と明記する。 |

## 文書更新ルール

- 機能追加前に、該当する要件、設計、API、権限、状態遷移、テスト観点を更新する。
- 実装後に、テスト仕様とエビデンスを更新する。
- 仕様と実装が異なる場合は、実装を直すか、仕様変更として文書を更新する。
- 未実装の内容は `未実装` または `後続対応` と明記する。
- Accepted ADR の decision を変更する場合は既存 ADR を上書きせず、新しい ADR で supersede する。
