# RcGear - RC ギア比計算機 (Android)

ラジコンのギア比・理論最高速を計算する Android ネイティブアプリ。
既存の Web 版（HTML/JS）を Modern Android Development 構成で再構築したものです。

**完全オフライン動作**・サーバー/クラウド同期なし。

## 主な機能

### CALC（計算）
- メーカー・シャーシ選択（ボトムシート、9メーカー / 45シャーシ超の内蔵DB）
- 内部減速比の自動セット
- ピニオン / スパー / モーターKV / セル数 / タイヤ径のスライダー入力
- 理論最高速度（km/h・mph）の大型HUD表示
- 1次減速比 / 最終減速比 (FDR) / 電圧 / モーターRPM / ホイールRPM の算出
- セッティング傾向バー（基準FDRを中央に、トルク↔最高速の双方向表示）
- ギア構成のリアルタイム描画（Compose Canvas、ギア比連動の回転アニメーション）
- セッティングの名前付き保存
- 計算結果の画像エクスポート（PNG）
- 前回終了時の入力状態を自動復元

### SETUPS（保存一覧）
- 保存セッティングの一覧・詳細表示・削除
- **スナップショット凍結**: 保存時の内部減速比を凍結し、後からシャーシDBを
  変更しても保存値は不変。差分があれば「保存時 / 現在」を並べて表示
- 「CALC に流し込む」ワンタップ遷移

### DB（シャーシDB）
- メーカーごとのグルーピング表示、フィルタータブ（すべて / 編集済み）
- 内部減速比・タイヤ径・備考のフィールド単位上書き
  （標準DBは保持したまま差分のみ保存、リセットで標準値に復帰）
- ユーザー編集済みエントリの視覚的識別

### CONFIG（設定）
- テーマ切替（ダーク / ライト / システム追従）
- mph 併記・アニメーションの ON/OFF
- 基準 FDR の変更
- 全データの JSON エクスポート / インポート（Storage Access Framework）
- 全データ削除

## スクリーンショット

<!-- TODO: 実機スクリーンショットを docs/screenshots/ に配置して差し替える -->
| CALC | SETUPS | DB | CONFIG |
|---|---|---|---|
| （準備中） | （準備中） | （準備中） | （準備中） |

## 技術スタック

| 分類 | 採用技術 |
|---|---|
| 言語 | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3（Dynamic Color 不採用、HUD調固定テーマ） |
| アーキテクチャ | MVVM + 軽量 Clean Architecture（UI / Domain / Data 3層） |
| DI | Hilt |
| 非同期 | Kotlin Coroutines + Flow / StateFlow |
| 永続化 | Room（セッティング・上書き・履歴）+ DataStore Preferences（設定） |
| ナビゲーション | Navigation Compose |
| シリアライゼーション | kotlinx.serialization |
| その他 | Splash Screen API、エッジ・ツー・エッジ、アダプティブアイコン |

## アーキテクチャ

```
UI Layer      Composable Screens + ViewModels（feature/ 配下、画面ごと）
   ↓
Domain Layer  UseCases + Pure Domain Models + GearCalculator（純粋関数）
   ↑
Data Layer    Repositories + Room / DataStore / assets JSON / SAF
```

- 依存方向は単方向（UI → Domain ← Data）
- シャーシDBは「同梱JSON（読み取り専用）+ Room 上書きテーブル（差分）」を
  `ChassisRepository` が Flow で合成して提供する
- 単一モジュール・パッケージによる疑似マルチモジュール構成（将来分割可能）

詳細な設計・開発ステップは [docs/PLAN.md](docs/PLAN.md) を参照。

## 必要環境

- Android Studio Ladybug (2024.2.1) 以降推奨
- JDK 17 以降（Android Studio 同梱の JBR で可）
- 動作対象: Android 8.0 (API 26) 以上 / ターゲット SDK 35

## ビルド方法

```bash
# Android Studio でプロジェクトを開く、または CLI から:
./gradlew :app:assembleDebug     # デバッグビルド
./gradlew :app:testDebugUnitTest # 単体テスト
./gradlew :app:assembleRelease   # リリースビルド（R8 有効、署名は未設定）
```

## ディレクトリ構成

```
app/src/main/
├── java/io/github/taskengineer/rcgear/
│   ├── core/          # designsystem（テーマ）、domain（計算ロジック）、common
│   ├── data/          # Repository、Room、DataStore、assets/SAF データソース
│   ├── domain/        # ドメインモデル、UseCase
│   ├── feature/       # calc / setups / db / config（画面 + ViewModel）
│   └── navigation/    # NavHost、ルート定義、アプリシェル
├── res/               # テーマ、アイコン、文字列
└── assets/
    └── chassis-db.json  # 内蔵シャーシDB（内部減速比データ）
app/schemas/           # Room スキーマ（マイグレーションテスト用）
docs/
└── PLAN.md            # 実装計画書
```

## データについて

- 内部減速比データベース（chassis-db.json）は各メーカー公式マニュアル・
  RCフォーラム等の公開情報を基に作成（出典は PLAN.md 参照）
- `id` はグローバル一意キーとして将来も不変

## ライセンス

TBD（オープンソース化を検討中）
