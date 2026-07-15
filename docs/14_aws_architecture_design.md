# AWS アーキテクチャ設計書

## 1. 目的

経費精算システムを AWS 上で運用する場合の production architecture、network boundary、security、deployment、monitoring、backup、および運用責任を定義する。

本書は Phase 13 の設計成果物である。AWS resource の作成、Infrastructure as Code、application の AWS 連携実装、実環境への deploy は本 Phase の対象外とする。

## 2. 設計方針と前提

| 項目 | 方針 |
|---|---|
| Region | Asia Pacific (Tokyo) `ap-northeast-1` を第一候補とする。 |
| Availability Zone | production は 2 AZ を利用し、単一 AZ 障害で API 全体が停止しない構成とする。 |
| Compute | Amazon ECS on AWS Fargate を利用し、EC2 instance を管理しない。 |
| Database | Amazon RDS for PostgreSQL 16、Multi-AZ DB instance deployment を利用する。 |
| Public entry point | internet-facing Application Load Balancer のみを公開する。ECS task と RDS に public IP を付与しない。 |
| Container registry | Amazon ECR private repository を利用する。 |
| Receipt storage | Amazon S3 private bucket を利用する。DB には object key のみを保持する。 |
| Secret | DB username/password は AWS Secrets Manager で管理し、image、source code、task definition の平文環境変数へ埋め込まない。 |
| Observability | CloudWatch Logs、CloudWatch Metrics、Container Insights、CloudWatch Alarm を利用する。 |
| Infrastructure management | 実装 Phase では Terraform または AWS CloudFormation/CDK のいずれかに統一し、手作業だけで production resource を構築しない。 |

可用性と安全性を優先した production baseline を定義する。個人検証環境では費用を抑えるため、single-AZ RDS、task 1 台、NAT Gateway 1 台などへの縮退を許容するが、production と同等の可用性は保証しない。

## 3. 全体構成

```text
Internet
  |
Route 53
  |
ACM certificate / HTTPS 443
  |
Application Load Balancer
  |  public subnet A / C
  |
ECS Service on Fargate
  |  private application subnet A / C
  |-- Amazon ECR       : production image
  |-- Secrets Manager  : DB credential
  |-- CloudWatch Logs  : application stdout/stderr
  |-- Amazon S3        : receipt object (後続実装)
  |
RDS for PostgreSQL 16 Multi-AZ
     private database subnet A / C
```

管理者が ECS task や RDS に internet から直接接続する経路は設けない。通常運用は AWS API、CloudWatch Logs、RDS の監視機能で行い、緊急調査で shell access が必要になった場合は ECS Exec の採用条件と監査方法を別途決定する。

## 4. AWS resource 構成

| 分類 | Resource | 数 / 配置 | 役割 |
|---|---|---|---|
| DNS | Route 53 hosted zone / record | 1 | API domain を ALB alias record へ解決する。 |
| TLS | ACM certificate | 1 | ALB で HTTPS を終端する。 |
| Network | VPC | 1 | production network boundary。 |
| Network | Public subnet | 2 AZ | ALB、NAT Gateway を配置する。 |
| Network | Private application subnet | 2 AZ | ECS Fargate task を配置する。 |
| Network | Private database subnet | 2 AZ | RDS DB subnet group を構成する。 |
| Entry point | Application Load Balancer | Multi-AZ | HTTPS request を healthy ECS task へ転送する。 |
| Compute | ECS cluster / service | 1 / 1 | Fargate task を desired count 以上に維持する。 |
| Registry | ECR private repository | 1 | versioned production image を保管する。 |
| Database | RDS PostgreSQL | Multi-AZ | 業務データと監査ログを永続化する。 |
| Object storage | S3 bucket | 1 | 領収書 object を非公開で保管する。 |
| Secret | Secrets Manager secret | 1 以上 | DB credential を暗号化して管理する。 |
| Monitoring | CloudWatch log group / alarm | 用途別 | application log、metric、alarm を管理する。 |
| Notification | SNS topic | 1 | production alarm の通知先を集約する。 |

## 5. Network 設計

### 5.1 CIDR と subnet

CIDR は他 VPC、on-premises network、VPN との重複を確認して実装時に確定する。初期案を以下とする。

| Network | CIDR example | AZ | Default route |
|---|---|---|---|
| VPC | `10.20.0.0/16` | Region | - |
| Public subnet A | `10.20.0.0/24` | AZ-A | Internet Gateway |
| Public subnet C | `10.20.1.0/24` | AZ-C | Internet Gateway |
| Private application subnet A | `10.20.10.0/24` | AZ-A | NAT Gateway A |
| Private application subnet C | `10.20.11.0/24` | AZ-C | NAT Gateway C |
| Private database subnet A | `10.20.20.0/24` | AZ-A | internet default route なし |
| Private database subnet C | `10.20.21.0/24` | AZ-C | internet default route なし |

ECS task は private application subnet に配置し、`assignPublicIp=DISABLED` とする。production は AZ ごとに NAT Gateway を置き、application subnet は同一 AZ の NAT Gateway を利用する。これにより image pull、secret 取得、log 送信などの outbound access を確保し、単一 NAT/AZ 障害の影響を限定する。

実装前に、NAT Gateway の処理量と固定費を次の VPC Endpoint と比較する。

- ECR API interface endpoint
- ECR Docker interface endpoint
- CloudWatch Logs interface endpoint
- Secrets Manager interface endpoint
- S3 gateway endpoint

VPC Endpoint を採用する場合も endpoint policy と security group を最小権限にし、Fargate platform version と必要 endpoint の組合せを implementation test で確認する。

### 5.2 Security group

| Security group | Inbound | Outbound |
|---|---|---|
| `sg-alb` | internet から TCP 443。TCP 80 を利用する場合は HTTPS redirect のみ。 | `sg-ecs` の TCP 8080。 |
| `sg-ecs` | `sg-alb` から TCP 8080 のみ。 | `sg-rds` の TCP 5432、および AWS service access 用 TCP 443。 |
| `sg-rds` | `sg-ecs` から TCP 5432 のみ。 | DB が必要とする範囲に制限する。 |
| `sg-vpce` | `sg-ecs` から TCP 443。 | endpoint service 通信に必要な範囲。 |

Security group reference を利用し、固定 IP address への依存を避ける。SSH、RDP、PostgreSQL port を internet に公開しない。Network ACL は stateless rule が必要な特別要件がない限り default を維持し、主な通信制御は security group で行う。

### 5.3 DNS / TLS / HTTP

- Route 53 alias record で API domain を ALB に向ける。
- ACM certificate を ALB listener へ設定し、外部通信は TLS 1.2 以上を許可する security policy とする。
- HTTP 80 を開ける場合は HTTPS 443 へ redirect し、application request を HTTP のまま処理しない。
- ALB target group は IP target type とし、ECS task の port 8080 へ転送する。
- health check path は認証不要の `GET /actuator/health`、success code は `200` とする。

## 6. ECS Fargate / ECR 設計

### 6.1 ECS service

| 項目 | Initial value | 方針 |
|---|---|---|
| Launch type | Fargate | Server management を不要にする。 |
| OS / architecture | Linux / `X86_64` | 現行 image との互換性を優先する。ARM64 は image build と性能検証後に判断する。 |
| Network mode | `awsvpc` | task ごとに ENI と security group を割り当てる。 |
| Desired count | 2 | 2 AZ に task を分散する。 |
| CPU / memory | 0.5 vCPU / 1 GiB（仮値） | load test と CloudWatch metric を基に調整する。 |
| Container port | 8080 | ALB target group からのみ受け付ける。 |
| Health check | `/actuator/health` | ALB health check と ECS deployment 判定に利用する。 |
| Auto Scaling | min 2 / max 6（仮値） | CPU 60% を初期 target とし、memory と request pattern を確認して調整する。 |

Spring Boot process は既存 production `Dockerfile` の非 root user `spring:spring` で実行する。application log は file へ永続化せず stdout/stderr へ出力し、`awslogs` log driver で CloudWatch Logs へ送信する。

### 6.2 Deployment

- ECR tag は Git commit SHA を利用し、`latest` を deployment identifier として使用しない。
- ECR repository は tag immutability、image scanning、lifecycle policy を有効にする。
- ECS task definition revision には image digest または immutable tag を記録する。
- 初期 deployment は ECS rolling update、`minimumHealthyPercent=100`、`maximumPercent=200` とする。
- ECS deployment circuit breaker と automatic rollback を有効にする。
- migration は現状どおり application startup 時の Flyway が実行するため、複数 task から同時実行しても安全な migration のみを release する。破壊的 schema change は expand-and-contract pattern と事前 backup を採用する。
- CI/CD 実装後は `test -> image build -> scan -> ECR push -> task definition update -> ECS deploy -> health verification` の順で実行する。

現在 `.github/workflows/ci.yml` は local で ignore されており、remote CI/CD は未稼働である。Phase 13 では deployment workflow を実装済みとは扱わない。

## 7. RDS PostgreSQL 設計

| 項目 | 方針 |
|---|---|
| Engine | PostgreSQL 16。minor version は maintenance window で検証後に更新する。 |
| Placement | Private database subnet、public access 無効。 |
| Availability | production は Multi-AZ DB instance deployment。 |
| Storage | General Purpose SSD、storage autoscaling を有効化し、上限値を設定する。初期容量は load test 後に確定する。 |
| Encryption at rest | RDS encryption を有効化する。KMS key は実装時の key management policy に従う。 |
| Encryption in transit | JDBC SSL を必須化する。certificate 検証方法と trust store を implementation test で確認する。 |
| Credential | Secrets Manager で管理し、ECS task definition の `secrets` から注入する。 |
| Backup | Automated backup retention 7 日を初期値とし、point-in-time recovery を有効にする。release 前など必要時に manual snapshot を取得する。 |
| Protection | deletion protection を有効化し、削除時は final snapshot を必須とする。 |
| Maintenance | backup window と重複しない maintenance window を業務時間外に設定する。 |

RDS Multi-AZ は可用性と failover を目的とし、read scaling 用途として扱わない。read traffic 増加が確認された場合は read replica または Multi-AZ DB cluster を別途評価する。

初期の復旧目標は `RPO 5 分以内、RTO 4 時間以内` の暫定値とする。業務要件の合意後、backup retention、restore test、Multi-AZ failover test の結果を基に確定する。backup が存在することだけで復旧可能とは判断せず、定期的に別環境への restore test を行う。

## 8. S3 領収書保管設計

現行 application は `receipt_object_key` の保存のみを実装しており、file upload/download と S3 API 連携は未実装である。後続実装では以下を満たす。

- 専用 private bucket を使用し、account level と bucket level の S3 Block Public Access をすべて有効にする。
- Object Ownership は bucket owner enforced とし、ACL による公開制御を使用しない。
- Server-side encryption と bucket versioning を有効にする。KMS key を利用するかは機密区分と運用負荷を基に決定する。
- Object key は application が生成し、元 file name や利用者入力をそのまま key にしない。例: `receipts/{yyyy}/{mm}/{applicationId}/{uuid}`。
- ECS task role には対象 bucket/prefix の必要な `GetObject`、`PutObject` のみを許可し、bucket policy で HTTPS を強制する。
- Browser から直接 upload/download する場合は短時間の presigned URL を application が発行し、bucket 自体は公開しない。
- Content-Type、file size、許可拡張子、malware scan、保管期間、削除保留を業務・法務要件として実装前に確定する。
- Lifecycle rule による削除は会計証憑の保存要件確定後に設定し、未確定のまま自動削除しない。

## 9. Secret / IAM 設計

### 9.1 Secret

DB endpoint、database name など秘密でない設定は task definition environment または Systems Manager Parameter Store を利用できる。DB password は Secrets Manager に保存する。

Secret を ECS task の environment variable として注入した場合、secret rotation 後は新しい task を deployment して値を再取得する。automatic rotation の採用時期、rotation Lambda、DB user 切替手順は implementation Phase で検証する。

### 9.2 IAM role 分離

| Role | Principal / 用途 | 主な permission |
|---|---|---|
| ECS task execution role | ECS/Fargate agent | ECR image pull、CloudWatch Logs 送信、指定 secret の取得、必要時の KMS decrypt。 |
| ECS task role | Spring Boot application | 領収書 S3 prefix の必要操作。application が直接利用しない AWS API は許可しない。 |
| Deployment role | CI/CD | 対象 ECR push、task definition 登録、対象 ECS service 更新、必要な `iam:PassRole`。 |
| Operator role | 運用担当 | log/metric 参照、定義済み runbook の操作。恒常的な AdministratorAccess を利用しない。 |

Resource ARN と condition を指定し、`Action: *` / `Resource: *` を避ける。task execution role と task role を兼用しない。人のアクセスは IAM Identity Center と MFA を基本とし、CloudTrail で management event を記録する。

## 10. Logging / Monitoring 設計

### 10.1 Log

| Log | Destination | Initial retention | 内容 |
|---|---|---|---|
| Application log | CloudWatch Logs | 30 日（仮値） | Spring Boot stdout/stderr、request correlation ID、error。credential と個人情報は出力しない。 |
| ECS deployment event | EventBridge / CloudWatch | 運用要件で決定 | deployment failure、task停止。 |
| ALB access log | S3 | 90 日（仮値） | request、status、latency。bucket policy と lifecycle を設定する。 |
| RDS log | CloudWatch Logs | 30 日（仮値） | PostgreSQL error/slow query。出力対象は性能検証後に決定する。 |
| AWS API audit | CloudTrail | 組織方針で決定 | resource configuration と運用操作。 |

### 10.2 Metric / Alarm

| 対象 | Metric / condition | 初期対応 |
|---|---|---|
| ALB | `HTTPCode_Target_5XX_Count`、target response time | application log と dependency status を確認する。 |
| ALB target | `UnHealthyHostCount >= 1` | ECS task event、health endpoint、startup log を確認する。 |
| ECS | CPU / memory utilization、running task count | scaling 状況、OOM、task stop reason を確認する。 |
| ECS deployment | `SERVICE_DEPLOYMENT_FAILED` | automatic rollback 結果を確認し、新 revision の rollout を停止する。 |
| RDS | CPU、database connections、free storage、freeable memory | slow query、connection leak、storage growth を確認する。 |
| RDS | failover / backup failure | application recovery と backup status を確認する。 |

CloudWatch Alarm は SNS topic へ通知する。通知先、severity、対応時間、escalation path は production 運用開始前に決定する。Container Insights は task/resource 単位の調査に利用するが、追加料金を確認して環境別に有効化する。

## 11. Security 設計

- AWS account の root user は日常利用せず、MFA を有効にする。
- ALB、ECS、RDS、S3、ECR、Secrets Manager の暗号化と access log を有効化する。
- WAF は公開範囲、想定 traffic、rate limit 要件を確認して production 公開前に採否を決定する。
- ECR image scan で critical/high vulnerability を確認し、許容基準を満たさない image は deploy しない。
- dependency、base image、RDS minor version の patch 方針を定める。
- Security Hub、GuardDuty、AWS Config は account 全体の security baseline と費用を確認して採用する。
- application の HTTP Basic 認証は internet-facing production の長期運用には不十分である。JWT/OIDC、refresh token、account lock、password policy を frontend/authentication Phase で設計する。
- ALB health endpoint は最小情報のみ返し、DB credential、stack trace、build secret を公開しない。

## 12. Availability / Disaster Recovery

| Failure | 設計上の動作 | 運用対応 |
|---|---|---|
| ECS task failure | ECS service が task を再作成し、ALB は unhealthy target へ転送しない。 | task stop reason と application log を確認する。 |
| Single AZ failure | ALB と ECS task は別 AZ で継続し、RDS Multi-AZ は standby へ failover する。 | capacity、DB reconnect、error rate を確認する。 |
| Failed deployment | circuit breaker が deployment を失敗扱いにし、直前の completed revision へ rollback する。 | migration compatibility を含め原因を調査する。 |
| Data corruption / accidental deletion | RDS point-in-time restore または snapshot restore を行う。 | 別 DB instance へ restore し、検証後に接続先を切り替える。 |
| Region-wide failure | 初期 Phase では自動 cross-region failover を提供しない。 | backup export/cross-region copy の要件を確認し、別 DR Phase で設計する。 |

Application は session state を container local storage に保持しない。rolling update と task replacement に耐えられる stateless API とする。

## 13. Environment / Naming / Tagging

初期は local と production を分離し、AWS 上の staging environment は予算と検証要件を確認して追加する。production data を staging へ複製する場合は匿名化する。

Resource name は `{system}-{environment}-{resource}` を基本とする。例: `expense-prod-cluster`、`expense-prod-api`。すべての対応 resource に少なくとも以下の tag を設定する。

| Tag | Example |
|---|---|
| `System` | `expense-settlement` |
| `Environment` | `prod` |
| `ManagedBy` | `terraform` または `cloudformation` |
| `Owner` | 運用責任者名または team name |
| `CostCenter` | project/accounting identifier |

## 14. 責任分界

| 領域 | AWS | Project / application team |
|---|---|---|
| Physical infrastructure | data center、physical host、managed service 基盤を運用する。 | Region/AZ と service を選択する。 |
| ECS Fargate | Fargate infrastructure を管理する。 | image、task definition、IAM、network、scaling、application を管理する。 |
| RDS | DB host、managed backup/failover 機能を提供する。 | schema、query、credential、retention、restore test、version policy を管理する。 |
| S3 | durable object storage service を提供する。 | bucket policy、object key、retention、access control、data classification を管理する。 |
| Security | AWS infrastructure を保護する。 | IAM、secret、patch、application vulnerability、log review、incident response を管理する。 |
| Availability | 各 service の SLA と managed mechanism を提供する。 | Multi-AZ 設計、capacity、alarm、runbook、復旧試験を管理する。 |

Managed service を利用しても security、data、configuration、backup restore verification の責任は project 側に残る。

## 15. Cost 方針

- Cost allocation tag と AWS Budget alert を設定する。
- 主な固定費である ALB、NAT Gateway、Multi-AZ RDS、interface VPC Endpoint を見積もってから resource を作成する。
- production 可用性を保ったまま、Fargate CPU/memory、RDS instance/storage、log retention を実測値で right-size する。
- ECR lifecycle policy と S3 lifecycle policy で不要データを整理する。ただし監査・会計上必要なデータを費用だけを理由に削除しない。
- 開発検証 resource は利用時間を限定し、停止しても課金が続く NAT Gateway、ALB、RDS storage などを把握する。

本 Phase では料金見積りを確定しない。Region、traffic、storage、availability requirement が確定した時点で AWS Pricing Calculator により月額概算を作成する。

## 16. 未実装項目と次の実施順序

| Order | Task | Completion criteria |
|---|---|---|
| 1 | 非機能要件確定 | domain、traffic、RTO/RPO、retention、予算、通知先が合意される。 |
| 2 | IaC 選定・実装 | VPC、ALB、ECS、RDS、ECR、S3、IAM、monitoring が code review 可能な状態になる。 |
| 3 | Application 対応 | JDBC SSL、S3 receipt API、production log、graceful shutdown を実装・試験する。 |
| 4 | CI/CD 実装 | immutable image を ECR へ push し、承認された revision を ECS へ deploy できる。 |
| 5 | Security / failure test | access control、secret rotation、task replacement、Multi-AZ failover、DB restore を確認する。 |
| 6 | Runbook / release | deploy、rollback、incident、backup restore 手順を作成し、production readiness review を通過する。 |

## 17. Phase 13 完了条件

- Network、ECS、RDS、S3、Secrets Manager、IAM、monitoring の production design が文書化されている。
- Container registry、tag、deployment、rollback 方針が定義されている。
- AWS と project team の責任分界が定義されている。
- 未実装項目と implementation Phase の入口条件が明示されている。
- AWS resource は作成されておらず、費用は発生していない。

## 18. 参考資料

- [Amazon ECS task networking options for Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html)
- [Connect Amazon ECS applications to the internet](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-outbound.html)
- [Amazon ECR interface VPC endpoints](https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html)
- [Amazon ECS task execution IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html)
- [Amazon ECS deployment circuit breaker](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-circuit-breaker.html)
- [Amazon RDS Multi-AZ deployments](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.MultiAZ.html)
- [Encrypting Amazon RDS resources](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html)
- [Blocking public access to Amazon S3 storage](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
- [Amazon ECS Container Insights metrics](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html)
- [Security groups for Application Load Balancers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-update-security-groups.html)
