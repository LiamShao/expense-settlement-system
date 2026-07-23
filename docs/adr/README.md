# Architecture Decision Records

本ディレクトリは、architecture に長期的な影響を与える決定、その背景、比較した選択肢、結果、再検討条件を ADR として管理する。

## Index

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-001](ADR-001-production-authentication.md) | Production authentication に Spring Session JDBC を採用する | Accepted | 2026-07-20 |
| [ADR-002](ADR-002-receipt-storage-and-delivery.md) | 領収書を storage adapter と application proxy で扱う | Accepted | 2026-07-23 |

## Status

- `Proposed`: 検討中であり、実装の前提にしない。
- `Accepted`: 採用済みであり、現行設計の判断根拠とする。
- `Superseded`: 後続 ADR により置き換えられた。
- `Deprecated`: 新規利用を推奨しないが、置換 ADR が未確定である。

## Update rules

- Accepted ADR の過去の判断理由は、後から現状に合わせて書き換えない。
- Decision を変更する場合は新しい ADR を追加し、旧 ADR を `Superseded by ADR-xxx` とする。
- 実装詳細だけの変更は通常の設計書で管理し、architecture の選択や重要な trade-off が変わる場合だけ ADR を追加する。
