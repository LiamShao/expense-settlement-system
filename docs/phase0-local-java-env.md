# Phase 0: ローカル Java 開発環境

## 目的

Java 17 / Gradle をローカル PC に直接インストールせず、Docker 上で同一の開発環境を利用できるようにする。

## 使用イメージ

- `gradle:8.10.2-jdk17-jammy`
- Java 17
- Gradle 8.10.2
- Timezone: `Asia/Tokyo`
- Encoding: `UTF-8`

## 起動方法

```bash
docker compose build java-dev
docker compose run --rm java-dev
```

## バージョン確認

コンテナ内で以下を実行する。

```bash
java -version
gradle -version
```

## Spring Boot プロジェクト作成後の利用例

```bash
docker compose run --rm java-dev ./gradlew test
docker compose run --rm java-dev ./gradlew bootRun
```

## 補足

後続 Phase でアプリケーション実行用の本番向け `Dockerfile` を追加するため、開発環境用 Dockerfile は `Dockerfile.dev` として分離している。
