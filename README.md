# RcGear - RC ギア比計算機 (Android)

ラジコンのギア比・最高速計算を行う Android ネイティブアプリ。

## ステータス

**Step 1: プロジェクト初期化** 完了

## 必要環境

- Android Studio Ladybug (2024.2.1) 以降推奨
- JDK 17（Android Studio に同梱）
- Android SDK Platform 34
- 動作対象: Android 8.0 (API 26) 以上

## 技術スタック

- Kotlin 2.0.20
- Jetpack Compose + Material 3
- Hilt (DI)
- Room + DataStore
- Navigation Compose
- kotlinx.serialization

詳細は [docs/PLAN.md](docs/PLAN.md) を参照。

## ビルド方法

```bash
# プロジェクトルートで Android Studio から開く
# または CLI から:
./gradlew :app:assembleDebug
```

## ディレクトリ構成

```
app/
└── src/main/
    ├── java/io/github/taskengineer/rcgear/   # Kotlin ソース
    ├── res/                                  # リソース
    └── assets/                               # 同梱 JSON（chassis-db.json 配置予定）
docs/
└── PLAN.md                                   # 実装計画書
```
