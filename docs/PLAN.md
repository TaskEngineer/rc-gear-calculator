# RC ギア比計算機 Android版 実装計画書

> **Status**: MVP 実装完了（Step 1〜12）/ Phase 2 未着手
> **Document Version**: 1.1
> **Last Updated**: 2026-07-02

---

## 1. プロジェクト概要

### 1.1 目的
ラジコンのギア比計算を行うAndroidアプリ。既存のWeb版（HTML/JS）をベースに、Androidネイティブ実装で再構築する。

### 1.2 前提・スタンス
- **想定ユーザー**: 基本は自分自身（個人ツール）。将来的にPlay Storeで一般公開する可能性も視野
- **位置づけ**: 個人ポートフォリオ作品としても通用するクオリティを目指す
- **完成度の方針**: MVPは「現Web版機能の完全移植 + Androidらしさ」、Phase 2以降で機能拡張
- **データ方針**: 完全オフライン動作。サーバー・クラウド同期なし

### 1.3 既存資産
- Web版（HTML/JS/JSON）が動作中
- 内部減速比データベース（chassis-db.json、9メーカー / 47エントリ）
- 計算ロジック（JavaScript版で実装済み、Kotlinへ移植）

---

## 2. 機能要件

### 2.1 MVP（最初のリリーススコープ）

#### 2.1.1 機能スコープ（Web版から完全移植）
- メーカー・シャーシのプルダウン選択（2段階）
- 内部減速比の自動セット
- ピニオン / スパー / KV / セル数 / タイヤ径のスライダー入力
- ギア表示SVG（ピニオン・スパーのリアルタイム描画）
- 1次減速比、最終減速比（FDR）、理論最高速度、ホイールRPMの算出と表示
- セッティング傾向バー（中央起点、トルク↔最高速の双方向表示）
- セッティングを名前付きで保存
- JSONによる全データのエクスポート / インポート
- 内部減速比データベースの管理（標準DB + ユーザー上書き）

#### 2.1.2 Android特有機能
- Material 3 コンポーネント採用（ただしDynamic Colorは不採用、HUD調の固定テーマ）
- Room によるセッティング永続化
- DataStore によるユーザー設定保存
- Navigation Compose による画面遷移
- ライト / ダークテーマ切替（手動 + システム追従の3択）
- エッジ・ツー・エッジ表示
- Splash Screen API（Android 12+）
- セッティングの画像エクスポート（Composeレイアウトを Bitmap 化）
- Composeアニメーション（数値変化、ギア回転など）

#### 2.1.3 MVP独自要件
- スナップショット凍結機能（保存時の内部減速比を凍結し、現在値との差分を表示）
- ユーザー上書き済みエントリの視覚的区別と「リセット」機能
- 計算履歴テーブル（テーブル定義のみ。Insertは実装、UIはPhase 2）

### 2.2 Phase 2（拡張候補）

優先度順:
1. **計算履歴UI**（最近の計算一覧、頻繁に使うシャーシのランキング表示）
2. **アプリ内DB編集UI**（独自シャーシエントリの追加機能）
3. **アダプティブアイコン**
4. **多言語対応**（日本語 / 英語）
5. **セッティング比較画面**（2件の差分表示）
6. **ロールアウト計算**（タイヤ周長 ÷ FDR）
7. **共有機能**（Share Sheet による画像 / テキスト共有）
8. **アプリショートカット**（長押しメニューから即起動）

### 2.3 Phase 3（先送り）

- ホーム画面ウィジェット（Glance API）
- KV値データベース（モーターメーカー別）
- グラフ表示（KV別の最高速曲線等）
- チューニングメモ（走行記録）
- マルチモジュール化への分割

### 2.4 非機能要件

| 項目 | 要件 |
|---|---|
| 対応 OS バージョン | Android 8.0 (API 26) 以上 |
| ターゲット SDK | Android 14 (API 34) 以上 |
| 動作要件 | 完全オフライン |
| パフォーマンス | スライダー操作のFPS 60維持、計算遅延 < 16ms |
| 永続化 | アプリ削除しない限りデータ保持 |
| 国際化 | MVPは日本語のみ、Phase 2で英語追加 |
| アクセシビリティ | TalkBack対応（最小限）、コントラスト比 WCAG AA |

---

## 3. 技術スタック

### 3.1 言語・ビルド
- Kotlin 1.9+
- Gradle Kotlin DSL
- Java Toolchain 17

### 3.2 アーキテクチャ
- **アーキテクチャパターン**: MVVM + Clean Architecture（軽量3層構成）
- **モジュール構成**: 単一モジュール、パッケージで疑似マルチモジュール構成（将来分割可能）
- **DI**: Hilt
- **非同期**: Kotlin Coroutines + Flow / StateFlow

### 3.3 UI
- Jetpack Compose
- Material 3（コンポーネントのみ採用、Dynamic Colorは不採用）
- Navigation Compose
- Compose アニメーション API

### 3.4 永続化
- Room（保存セッティング、シャーシ上書き、計算履歴）
- DataStore Preferences（ユーザー設定）
- Storage Access Framework（JSONファイル入出力）

### 3.5 シリアライゼーション
- kotlinx.serialization

### 3.6 テスト（推奨、必須ではない）
- JUnit 5 / Kotest（純粋ロジック）
- MockK（依存モック）
- Compose Test（UI、最小限）

---

## 4. アーキテクチャ設計

### 4.1 レイヤー構成

```
┌─────────────────────────────────────────────────┐
│ UI Layer                                         │
│   Composable Screens + ViewModels                │
│   依存: Domain                                   │
├─────────────────────────────────────────────────┤
│ Domain Layer                                     │
│   UseCases + Pure Domain Models                  │
│   依存: なし（Pure Kotlin）                      │
├─────────────────────────────────────────────────┤
│ Data Layer                                       │
│   Repositories + DataSources                     │
│   依存: Domain（Domain Modelを実装するため）     │
└─────────────────────────────────────────────────┘
```

依存方向は単方向（UI → Domain ← Data）。UIとDataは相互に直接知らない。

### 4.2 主要コンポーネント

#### ViewModel（4つのトップレベル画面それぞれに1つ）
- `CalcViewModel`: 計算画面の状態と入力管理
- `SetupsViewModel`: 保存セッティング一覧
- `DbViewModel`: シャーシDB管理
- `ConfigViewModel`: 設定画面

#### UseCase（軽量3層、1クラス1関数を許容）
- `CalculateGearUseCase`: 入力→計算結果（純粋関数、Web版から移植）
- `SaveSetupUseCase`: バリデーション + 保存処理
- `ResolveChassisUseCase`: JSON + Override合成（中核ロジック）
- `ExportDataUseCase` / `ImportDataUseCase`: JSON入出力
- `OverrideChassisUseCase` / `ResetChassisOverrideUseCase`: 上書き / リセット

単純なRepository呼び出しはUseCaseを介さずViewModelから直接呼ぶ判断もする（過剰なラッピングを避ける）。

#### Repository
- `ChassisRepository`: 標準DB（assets/JSON）+ Room上書きを合成して提供
- `SetupRepository`: 保存セッティングのCRUD
- `PreferencesRepository`: DataStore操作
- `CalculationHistoryRepository`: 計算履歴の記録（MVP）

### 4.3 ChassisRepository の合成ロジック（中核）

```kotlin
class ChassisRepository(
    private val jsonProvider: ChassisJsonProvider,
    private val overrideDao: ChassisOverrideDao
) {
    fun getAllMakers(): Flow<List<Maker>> {
        return overrideDao.observeAll().map { overrides ->
            val overrideMap = overrides.associateBy { it.chassisId }
            jsonProvider.getDatabase().makers.map { (makerName, entries) ->
                Maker(
                    name = makerName,
                    chassis = entries.map { entry ->
                        // 上書きがあれば優先、なければ標準値
                        val ov = overrideMap[entry.id]
                        Chassis(
                            id = entry.id,
                            name = entry.name,
                            internalRatio = ov?.internalRatio ?: entry.internalRatio,
                            defaultTireMm = ov?.defaultTireMm ?: entry.defaultTireMm,
                            note = ov?.note ?: entry.note,
                            isUserEdited = ov != null
                        )
                    }
                )
            }
        }
    }
}
```

---

## 5. 画面仕様

### 5.1 トップレベル画面（NavigationBar 4タブ）

| タブラベル | 画面タイトル | 内容 |
|---|---|---|
| `CALC` | 計算 | メイン計算機能、シャーシ選択、入力、結果表示 |
| `SETUPS` | 保存一覧 | 保存セッティングの一覧、読込、削除 |
| `DB` | シャーシDB | 標準DB閲覧、上書き、リセット |
| `CONFIG` | 設定 | テーマ、単位、データ書出/読込 |

### 5.2 派生画面・ダイアログ

| 種別 | 画面 | アクセス元 |
|---|---|---|
| ボトムシート | シャーシ選択 | CALC のシャーシカードタップ |
| ダイアログ | セッティング保存 | CALC の保存ボタン |
| 詳細画面 | セッティング詳細 | SETUPS のカードタップ |
| 詳細画面 | シャーシ編集 | DB のエントリタップ |
| ダイアログ | テーマ選択 | CONFIG のテーマ項目 |
| ダイアログ | 基準FDR入力 | CONFIG の基準FDR項目 |

### 5.3 特殊遷移

- **SETUPS → CALC**: タップした保存セッティングの値を CALC に流し込み、NavigationBar を CALC に切替
- **CONFIG → SAF**: データ書出/読込時にシステムのファイルピッカー起動

### 5.4 デザインテイスト

- ベース背景: `#0a0e14`（黒に近い濃紺）
- サーフェス: `#0d1d2c`
- アクセント: ティール `#5dcaa5` / グリーン `#97C459` / オレンジ `#EF9F27`
- 数値表示: 等幅フォント（Roboto Mono または同等）
- 角丸: 4px（カード）、Material 3 標準（FAB等）
- TopAppBar タイトル: 日本語
- ボトムナビラベル: 英大文字 + 等幅（CALC / SETUPS / DB / CONFIG）

---

## 6. データモデル

### 6.1 同梱JSON（assets/chassis-db.json）

```json
{
  "schemaVersion": 1,
  "sources": [...],
  "makers": {
    "タミヤ": [
      {
        "id": "tamiya_tt02",
        "name": "TT-02",
        "internalRatio": 2.6,
        "defaultTireMm": 63,
        "note": "キット標準22T/70Tで8.27:1"
      }
    ]
  }
}
```

**重要**: `id` は将来も変更しないグローバル一意キー。スネークケース命名（メーカー+型番）。

### 6.2 Room Entities

#### saved_setups テーブル
```kotlin
@Entity(tableName = "saved_setups",
        indices = [Index(value = ["name"], unique = true)])
data class SavedSetupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val chassisId: String,
    val pinion: Int,
    val spur: Int,
    val internalRatioSnapshot: Double,  // 保存時点の値を凍結
    val kv: Int,
    val cells: Int,
    val tireMm: Int,
    val createdAt: Long,
    val updatedAt: Long
)
```

`internalRatioSnapshot` により、後でユーザーがDBの値を変更しても保存セッティングは凍結され続ける。読込時は現在値との差分を可視化する。

#### chassis_overrides テーブル
```kotlin
@Entity(tableName = "chassis_overrides")
data class ChassisOverrideEntity(
    @PrimaryKey val chassisId: String,
    val internalRatio: Double?,    // null = 上書きなし
    val defaultTireMm: Int?,
    val note: String?,
    val updatedAt: Long
)
```

フィールド単位で Nullable。リセット時はレコードごと削除して標準値に戻る。

#### calculation_history テーブル（MVPはテーブル定義 + Insertのみ）
```kotlin
@Entity(tableName = "calculation_history")
data class CalculationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chassisId: String,
    val pinion: Int,
    val spur: Int,
    val topSpeedKmh: Double,
    val createdAt: Long
)
```

### 6.3 DataStore（UserPreferences）

```kotlin
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,    // DARK / LIGHT / SYSTEM
    val showMphAlongside: Boolean = true,
    val animationEnabled: Boolean = true,
    val balanceFdr: Double = 7.0,
    val lastSelectedChassisId: String? = null,
    val lastPinion: Int = 22,
    val lastSpur: Int = 84,
    val lastKv: Int = 6500,
    val lastCells: Int = 2,
    val lastTireMm: Int = 63
)
```

`lastXxx` 系は前回終了時の状態を復元するため。

### 6.4 計算ロジック

Web版のJSロジックをKotlinに移植。純粋関数として実装。

```kotlin
data class GearCalculationInput(
    val pinion: Int,
    val spur: Int,
    val internalRatio: Double,
    val kv: Int,
    val cells: Int,
    val tireMm: Int
)

data class GearCalculationResult(
    val primaryRatio: Double,         // スパー ÷ ピニオン
    val finalDriveRatio: Double,      // 1次 × 内部減速
    val voltage: Double,              // セル数 × 3.7
    val motorRpm: Double,             // KV × Voltage
    val wheelRpm: Double,             // モーターRPM ÷ FDR
    val topSpeedKmh: Double,          // π × タイヤD × ホイールRPM × 60 / 1000
    val topSpeedMph: Double,
    val balanceIndicatorPct: Double   // -100 (最高速) 〜 +100 (トルク)
)

object GearCalculator {
    fun calculate(input: GearCalculationInput, balanceFdr: Double): GearCalculationResult { ... }
}
```

---

## 7. パッケージ構成

将来のマルチモジュール化を見据えた疑似モジュール構造。

```
com.example.rcgear/
├── app/
│   ├── RcGearApplication.kt           # @HiltAndroidApp
│   ├── MainActivity.kt
│   └── navigation/
│       ├── RcGearNavHost.kt
│       └── Routes.kt
│
├── core/
│   ├── designsystem/                  # テーマ、色、タイポ、共通コンポーネント
│   │   ├── theme/
│   │   ├── component/
│   │   └── icon/
│   ├── ui/                            # 画面横断のUtil（Composable）
│   ├── common/                        # Util, Extension
│   └── domain/
│       └── GearCalculator.kt          # 純粋計算ロジック
│
├── data/
│   ├── di/                            # Hilt Module
│   ├── local/
│   │   ├── room/
│   │   │   ├── RcGearDatabase.kt
│   │   │   ├── entity/
│   │   │   ├── dao/
│   │   │   └── converter/             # TypeConverter
│   │   ├── datastore/
│   │   │   └── UserPreferencesDataSource.kt
│   │   └── asset/
│   │       └── ChassisJsonProvider.kt # assets読み取り
│   ├── repository/
│   │   ├── ChassisRepository.kt
│   │   ├── SetupRepository.kt
│   │   ├── PreferencesRepository.kt
│   │   └── CalculationHistoryRepository.kt
│   └── model/                         # データレイヤー内部のモデル
│
├── domain/
│   ├── model/                         # ドメインモデル（UI/Dataで共有）
│   │   ├── Chassis.kt
│   │   ├── Maker.kt
│   │   ├── SavedSetup.kt
│   │   ├── GearCalculationInput.kt
│   │   └── GearCalculationResult.kt
│   └── usecase/
│       ├── CalculateGearUseCase.kt
│       ├── SaveSetupUseCase.kt
│       ├── ResolveChassisUseCase.kt
│       ├── ExportDataUseCase.kt
│       ├── ImportDataUseCase.kt
│       ├── OverrideChassisUseCase.kt
│       └── ResetChassisOverrideUseCase.kt
│
└── feature/
    ├── calc/
    │   ├── CalcScreen.kt
    │   ├── CalcViewModel.kt
    │   ├── CalcUiState.kt
    │   └── component/                 # 画面固有のComposable
    ├── setups/
    │   ├── SetupsScreen.kt
    │   ├── SetupsViewModel.kt
    │   ├── SetupDetailScreen.kt
    │   └── component/
    ├── db/
    │   ├── DbScreen.kt
    │   ├── DbViewModel.kt
    │   ├── ChassisEditScreen.kt
    │   └── component/
    └── config/
        ├── ConfigScreen.kt
        ├── ConfigViewModel.kt
        └── component/
```

将来の分割時、各ディレクトリをそのままGradleモジュールに昇格できる構成。

---

## 8. 開発ステップ（推奨順序）

各ステップは独立してコミット可能。順序通りに進めれば動くアプリが段階的にできあがる。

### Step 1: プロジェクト初期化（1〜2日）
- Android Studioで新規プロジェクト作成（Empty Activity, Compose）
- Gradle: Kotlin 1.9+, Compose BOM, Hilt, Room, DataStore, kotlinx.serialization, Navigation Compose
- パッケージ構成のスケルトンを作成
- `RcGearApplication`、`MainActivity` 設定
- Splash Screen API 設定
- エッジ・ツー・エッジ設定

### Step 2: テーマシステム（半日〜1日）
- `core/designsystem/theme/` に色定義（Color.kt）
- ライト / ダークの ColorScheme
- Typography（等幅フォント含む）
- `RcGearTheme` Composable
- DataStore `themeMode` 連動

### Step 3: 計算ロジックの移植（半日）
- `core/domain/GearCalculator.kt` にWeb版のロジックを完全移植
- 入出力データクラス定義
- 単体テスト作成（テスト推奨）

### Step 4: 同梱JSON配置とパース（1日）
- `assets/chassis-db.json` 配置
- kotlinx.serializationでパースする`ChassisJsonProvider`実装
- ドメインモデル `Chassis`, `Maker` 定義
- Hiltモジュール `DataModule` でJsonProvider提供

### Step 5: Roomデータベース構築（1〜2日）
- `RcGearDatabase` 定義（Entity 3つ、DAO 3つ）
- TypeConverter（必要に応じて）
- Hiltモジュール `DatabaseModule`
- `ChassisRepository` の合成ロジック実装
- `SetupRepository` 実装

### Step 6: DataStore実装（半日）
- `UserPreferencesDataSource` 実装
- `PreferencesRepository` 実装
- Hilt連携

### Step 7: ナビゲーション骨組み（1日）
- `RcGearNavHost` 実装
- `Routes` 定義
- `Scaffold + NavigationBar` のレイアウト
- 各画面の空Composable配置

### Step 8: CALC画面（2〜3日、最重要）
- `CalcViewModel` + `CalcUiState`
- シャーシ選択ボトムシート
- スライダー入力（ピニオン、スパー、KV、セル、タイヤ）
- メインHUD（最高速大型表示）
- 派生メトリック表示
- セッティング傾向バー
- 保存ダイアログ
- ギアSVG描画（Compose Canvas）

### Step 9: SETUPS画面（1〜2日）
- 保存セッティング一覧表示
- カードタップで詳細画面
- 詳細画面でスナップショット差分表示
- 「CALCに流し込む」遷移
- 削除機能

### Step 10: DB画面（1〜2日）
- メーカーごとにグルーピング表示
- フィルタータブ
- ユーザー編集済みエントリの視覚的識別
- シャーシ編集画面
- 上書き / リセット機能

### Step 11: CONFIG画面（1日）
- セクション分け（DISPLAY / DATA / CALC_TUNING / ABOUT）
- テーマ選択ダイアログ
- データ書出/読込（SAF連携）
- 全データ削除（確認ダイアログ）
- 基準FDR入力ダイアログ

### Step 12: 仕上げ（2〜3日）
- アニメーション（数値変化、画面遷移、ギア回転）
- 画像エクスポート機能（Bitmap化）
- アプリアイコン
- スプラッシュアニメーション調整
- README、スクリーンショット
- リリースビルド設定（ProGuard等）

### 想定総工数
**実装13〜18日**程度（実務時間ベース）。学習時間込みなら×1.5〜2倍を見込む。

---

## 9. 実装時の注意事項

### 9.1 計算精度
- `Double`で十分（誤差は表示桁数の範囲内に収まる）
- 表示時は必ず `Math.round` または `String.format("%.2f", x)` で丸める
- ホイールRPMは整数、最高速は小数1桁、FDRは小数2桁

### 9.2 状態管理
- `CalcUiState` は immutable な data class
- スライダー操作は `onValueChange` で頻繁に発火するため、計算は debounce 不要だが描画はComposeに任せる
- Configuration変更（画面回転）対応のため、ViewModelに状態を保持

### 9.3 SAF（Storage Access Framework）
- `ActivityResultContract.CreateDocument` でファイル作成
- `ActivityResultContract.OpenDocument` で読み込み
- mime-type は `application/json`

### 9.4 Roomマイグレーション
- MVP は schemaVersion 1 で開始
- Phase 2 で `calculation_history` のUI追加時にカラム追加など発生する可能性あり
- マイグレーションテストは早めに整備

### 9.5 スナップショット差分表示
- `SetupDetailScreen` で `internalRatioSnapshot` と現在の `internalRatio` を比較
- 差があれば「保存時 2.60 / 現在 2.70」のように表示
- 「現在値で再計算する」ボタンも提供（ユーザーが選択可能）

### 9.6 ライセンス
- アプリ本体はオープンソース化想定（MIT等）
- 利用ライブラリのライセンス表示はOSSライセンス画面を Phase 2 で

---

## 10. 用語集

| 用語 | 意味 |
|---|---|
| **FDR** | Final Drive Ratio。最終減速比。1次減速比 × 内部減速比。 |
| **1次減速比** | スパー歯数 ÷ ピニオン歯数。モーター直後の減速。 |
| **内部減速比** | シャーシ内部のギア / プーリーによる減速。シャーシ固有値。 |
| **KV値** | ブラシレスモーターの定格回転数 / Volt。1V当たりのRPM。 |
| **ロールアウト** | ホイール1回転で進む距離。タイヤ周長 ÷ FDR。Phase 2で実装。 |
| **MAD** | Modern Android Development。Google推奨の現代的Android開発手法。 |
| **MVVM** | Model-View-ViewModel。Androidの標準アーキテクチャパターン。 |
| **Hilt** | Googleが推奨するDIライブラリ。Daggerをラップ。 |
| **Compose** | Jetpack Compose。Kotlin の宣言的UIフレームワーク。 |
| **Material 3** | Googleの最新デザインシステム。Material Youの基盤。 |
| **SAF** | Storage Access Framework。Androidのファイルピッカー機構。 |
| **DataStore** | SharedPreferencesの後継。Coroutines/Flow対応の永続化API。 |

---

## 11. 参考資料

### 公式ドキュメント
- [Modern Android Development](https://developer.android.com/modern-android-development)
- [Now in Android（Googleの参考実装）](https://github.com/android/nowinandroid)
- [Architecture Guide](https://developer.android.com/topic/architecture)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3 for Compose](https://developer.android.com/jetpack/compose/designsystems/material3)

### 内部減速比データの出典
- RCUForums: Internal Gear Ratios Explained
- So Dialed: Transmission Ratio
- R/C Tech Forums: HB Cyclone TC FDR
- 各メーカー公式マニュアル

---

## 12. 次のアクション

1. この実装計画書をリポジトリに `docs/PLAN.md` として配置
2. プロトタイピングのためのリポジトリを作成
3. Step 1（プロジェクト初期化）から着手

実装中に判断に迷う点が出たら、本ドキュメントの該当セクションを更新する。
