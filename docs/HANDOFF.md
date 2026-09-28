# 引き継ぎ書（HANDOFF）— RcGear Android

> **Status**: MVP 完了 + **セッティングシート化 Phase 0 / 1 / 2 / 3 完了**（Phase 3 = UI 構築）。
> **実機確認と instrumented テストは 2026-09-28 に実施済み**（§7.1）。
> そこで出た **BUG-6（クラッシュ）と BUG-7 は同日に修正済み**。残りは UI の粗さ 4 件（`ROADMAP.md` Phase 3.5）。
> **Last Updated**: 2026-09-28（実機確認 + instrumented テストの結果、および BUG-6 / BUG-7 の修正を反映）
> **対象読者**: 次にこのリポジトリを扱う AI エージェントと、その指示を出す本人。
>
> 運用ルールは `AGENTS.md`、機能ロードマップは `ROADMAP.md`、当初計画は `PLAN.md`（凍結）。
>
> **このアプリは「ギア比計算機」から「ギア比も計算できるセッティングシート管理アプリ」に
> なった。** ダンパーオイル・スプリング・キャンバー等、実物のセッティングシートに
> 書く内容を GARAGE タブ（車 → シート）で扱える。設計判断（別アプリに分けない／手動移行／
> フィールド定義は Kotlin レジストリ）は §5 に記録してある。

---

## 1. 現状サマリ

| 項目 | 状態 |
|---|---|
| 実装範囲 | PLAN Step 1〜12 + Phase 0 / 1 の土台 + Phase 2（ドメイン再構築）+ **Phase 3（UI 構築）**。タブは GARAGE / CALC / DB / CONFIG の 4 つ |
| 画面 | GARAGE（車一覧 → 車詳細 → シート閲覧 → セクション編集 / ヘッダ編集 / 比較）、CALC、DB（同梱シャーシ + 自作シャーシ）、CONFIG |
| 共有 UI | `core/designsystem/component/` に 13 部品（全てに `@Preview`）+ `feature/sheet/component/`（`FieldEditor` / `SheetSectionCard`。どちらも `@Preview` 付き）。文言は `strings.xml`（約 330 件） |
| モジュール | `:app`（Android）＋ `:core:domain`（純 Kotlin JVM） |
| ビルド | `:app:assembleDebug` / `:app:lintDebug` / `ktlintCheck` 成功（2026-09-26 確認） |
| 単体テスト | **351 件成功**（`:core:domain` 127 / `:app` 224）。レジストリ・値の検証・差分・v1→v2 変換・エクスポート往復・Repository 契約・シャーシ DB の妥当性 + Phase 3 の ViewModel 9 本とテキスト整形 + BUG-6 の回帰 5 件 + BUG-7 と §5.9 の横展開（編集画面 5 つの検証）|
| Instrumented / UI テスト | **24 件、全件成功**（2026-09-28）。DAO テスト 17 件 + `SetupSheetRepositoryImpl` 7 件（BUG-6）。DAO 側はエミュレータ Pixel_8（API 34）と実機 SO-53C（Android 14 / API 34 / 720x1496）の両方で、Repository 側は Pixel_8 で実行。手順は §6.1 |
| Lint / 静的解析 | **ktlint 導入済み**（`ktlintCheck` 緑）。Android Lint も CI で実行 |
| CI | 設定済み（`.github/workflows/ci.yml`: ktlint → domain test → test → assemble → lint）。**2026-09-26 の PR #1 で初回実行、全ステップ緑**（5m14s）。instrumented テストは CI に入っていない（エミュレータが要るため。§6 参照） |
| リリース署名 | 未設定。`versionCode = 1`、`versionName = 0.1.0` |
| スクリーンショット | README に TODO のまま（`docs/screenshots/` 未作成） |
| ライセンス | TBD |
| リモート | `https://github.com/TaskEngineer/rc-gear-calculator.git` |
| データ | シャーシ 45 エントリ / 9 メーカー、`id` 重複なし（`ChassisDbValidityTest` が固定） |
| Room | **version 2**（`cars` / `setup_sheets` / `setup_values` / `user_chassis` / `chassis_overrides`）。Phase 3 でスキーマは変えていない |
| エクスポート JSON | **schemaVersion 2**（cars / sheets / overrides / **userChassis**）。v1 の読み込みは永久に残す。`userChassis` は F-5 で足したキーで、版は上げていない（§5.8） |

### 1.1 ツールチェイン（実際の値。PLAN.md の記述より新しい）

| | 値 |
|---|---|
| Gradle | 9.0.0（wrapper） |
| AGP | 8.7.3 |
| Kotlin / KSP | 2.0.20 / 2.0.20-1.0.25 |
| JDK | 21（開発機は Android Studio 同梱 JBR。**絶対パスはコミットしない**。`JAVA_HOME` か `~/.gradle/gradle.properties`） |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Compose BOM | 2025.05.00 |
| Hilt / Room / DataStore | 2.52 / 2.6.1 / 1.1.1 |
| ktlint (Gradle plugin) | 12.1.1（設定は `.editorconfig`） |

Gradle 9 上で「Gradle 10 で非互換になる非推奨機能」の警告が出ている（原因は AGP 8.7 側の可能性が高い）。
AGP を上げると解消する見込みだが、Gradle と AGP の互換表に従って組で更新すること。

---

## 2. 既知の不具合

**BUG-1 〜 BUG-5 はすべて Phase 0 で決着済み**（BUG-4 のみ「誤記だった」という決着）。
再現手順と原因は、同じ壊れ方を再び作らないための記録として残す。

**BUG-6 / BUG-7 は 2026-09-28 の実機確認（§7.1）で見つかった不具合。どちらも同日に修正済み。**
現時点で未修正の不具合は無い（残っているのは UI の粗さ 4 件で、`ROADMAP.md` の Phase 3.5）。

### BUG-1: タイヤ径の上書きが範囲外だと CALC がクラッシュする（重要度: 高）

**[解消: S-1' / 45cc235]** `GearCalculationInput` に範囲・クランプ関数を集約し、`CalcViewModel.recalculate()` で一箇所クランプする形にした。

- 再現: DB 画面 → 任意シャーシ → タイヤ径に `200` を入力して保存 → CALC でそのシャーシを選択。
- 原因: `ChassisEditViewModel.onSave` は「正の整数」しか検証しない。
  `CalcViewModel.onChassisSelected` が `tireMm = chassis.defaultTireMm` をそのままセットし、
  `GearCalculationInput` の `init` にある `require(tireMm <= 120)` が `IllegalArgumentException` を投げる。
- 修正方針: 編集画面で `GearCalculationInput.MIN_TIRE_MM..MAX_TIRE_MM` に制限する。
  さらに防御として `CalcViewModel` 側でも `coerceIn` する。

### BUG-2: インポート JSON の値が範囲外だと後でクラッシュする（重要度: 高）

**[解消: S-1' / 45cc235]** `ImportDataUseCase` が 1 行ずつ検証して棄却し、件数で報告する。`SetupDetailViewModel` も例外ではなく「結果を伏せる」に変更（削除操作に到達できるようにするため）。

- 再現: エクスポート JSON の `pinion` を `5` に書き換えてインポート → SETUPS でその項目を開く。
- 原因: `ImportDataUseCase` は JSON の構文と `schemaVersion` しか検証せず、
  `SavedSetup` を無検証で Room に入れる。`SetupDetailViewModel` が `GearCalculationInput` を組み立てた時点で例外。
  同様に `CalcRequestBus` 経由で CALC に流し込んでも落ちる。
- 修正方針: `ImportDataUseCase` で 1 件ずつ範囲検証し、不正な行は `skippedInvalid` としてカウントして返す。
  既存の `Result.Success` にフィールドを足す。

### BUG-3: インポートがトランザクションではない（重要度: 中）

**[解消: S-1' / 45cc235 → M-8 / 994fafb]** まず各種を一括 insert にし（Room の @Insert(List) は 1 トランザクション）、M-8 で `TransactionRunner` を入れて**ファイル全体を 1 トランザクション**にした。

- `ImportDataUseCase` はセッティングと上書きを 1 件ずつ `insert` する。途中で失敗すると半端に取り込まれる。
- 修正方針: `RcGearDatabase.withTransaction { }` で囲む。Repository に `restoreAll(List)` を追加して DAO の `@Transaction` を使う。

### BUG-4: `String.format` の Locale 未指定が 3 箇所（重要度: 低）

**[誤記。不具合ではなかった]** 調査したところ、指摘の 3 箇所を含む **全 20 箇所すべてが `Locale.US` を指定済み**だった。
`Locale.US` が引数の次の行にあるため、1 行だけを見る grep では未指定に見えていたもの。

ただし「同じ値が画面ごとに違う桁数で出る」という別の問題は実在したので、
REF-6 / S-7（b624b8c）は DRY 目的で実施し、`core/ui/Format.kt` に集約した。
**新しく `String.format` を書くときに Locale を忘れる余地は残る**ので、整形関数を使うこと。

### BUG-5: `gradle.properties` に開発機の絶対パスがコミットされている（重要度: 中）

**[解消: S-2 / 7a3516b]** 行を削除し、`~/.gradle/gradle.properties` に退避。Android Studio の `gradleJvm=#GRADLE_LOCAL_JAVA_HOME` はそこから解決される。

- `org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr`。他の環境・CI では即失敗する。
- 修正方針: この行を削除し、`JAVA_HOME` または Gradle Toolchain（`kotlin { jvmToolchain(21) }` は既にある）に任せる。
  ローカルで必要なら `~/.gradle/gradle.properties` に移す。

### BUG-6: 消えたシートに CALC から「反映」するとクラッシュする（重要度: 高）

**[解消: 2026-09-28。発見と修正は同日]**

- 再現（エミュレータ Pixel_8 API34・実機 SO-53C Android 14 の両方で再現）:
  1. GARAGE → 車 → シート → 「CALC で調整する」（CALC が `Calc(sheetId)` ルートになる）
  2. GARAGE に戻り、同じシートを開いてゴミ箱アイコン → 「削除」
     （CONFIG の「全データを削除」でも同じ）
  3. CALC タブへ戻ると **消えたシートの「◯◯ から読み込み／このシートに反映」バナーが残っている**
  4. 「このシートに反映」をタップ → **FATAL EXCEPTION: main / アプリが落ちる**

```
android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed (code 787)
  at SetupValueDao_Impl.upsert(SetupValueDao_Impl.java:101)
  at SetupSheetRepositoryImpl$setValue$2.invokeSuspend(SetupSheetRepositoryImpl.kt:100)
```

- 原因: `CalcViewModel` はルート引数 `Calc(sheetId)` から `sheetContext` を **init で 1 回だけ**
  読み込み、以後 UiState に持ち続ける（`CalcViewModel.kt:66` / `loadSheetValues`）。
  シート行が消えても誰も無効化しないので、`onApplyToSheet()` が
  `sheetRepository.setValue(context.sheetId, ...)` を呼び、`setup_values.sheetId` の
  外部キー（`setup_sheets` への CASCADE）に違反する。Room の例外は握られていないのでそのまま落ちる。
  同じ状態のまま CALC タブを踏むと、稀に画面が丸ごと空になる（描画が出ない）症状も出る。
- 修正: 挙げていた 2 案を**両方**入れた。片方だけでは不足だったため。
  1. **データ層で落ちなくする。** `SetupSheetRepository.setValue` / `replaceValues` の
     戻り値を `Boolean` にし、`SetupSheetRepositoryImpl` は**書き込みと同じトランザクションの中で**
     `sheetDao.getById` を引いて、無ければ何も書かずに `false` を返す。
     存在確認と書き込みが 1 トランザクションなので、確認と書き込みの隙間で消える余地は無い。
  2. **UI 層でそもそも押させない。** `CalcViewModel` が `observeSheet(sheetId)` を購読し、
     null が来たら `sheetContext = null` にしてバナーを畳む。押し込まれた場合の最後の関所として
     `onApplyToSheet()` は `false` を受けたら文脈を畳み、`calc_sheet_gone` を
     `message` に載せる（AGENTS.md §5「例外を握りつぶさない」）。
- **なぜ JVM テストで捕まえられなかったか**: `FakeSetupSheetRepository.setValue` が
  「シートが無ければ黙って return」だったため。本物なら落ちるコードが Fake の上では素通りしていた。
  Fake を `false` を返す形に直し、`RepositoryContractTest` に契約として固定した
  （Fake は本物の制約を再現する ＝ AGENTS.md §5。この一件がその実例）。
- テスト（全て緑）:
  - `RepositoryContractTest`: 消えたシートには書けない / 書けたら true（2 件）
  - `CalcViewModelTest`: シート削除でバナーが畳まれる・全データ削除でも畳まれる・
    消えたシートには書かず理由を出す（3 件）
  - `SetupSheetRepositoryImplTest`（**新規 androidTest**）: 本物の SQLite に対して 7 件。
    Fake が緩かったのが原因なので、本物側を直接押さえる場所を作った
  - `RcGearDatabaseTest`: `value_消えたシートには値を書けない`（外部キーが実在することの確認）
- 確認: 上記の再現手順 2 経路（シート個別削除 / CONFIG の全データ削除）を
  修正後の APK でエミュレータ Pixel_8 に対して再演し、**どちらもバナーが消え、落ちない**ことを確認。
  `adb logcat -b crash` も空。「CALC が丸ごと空になる」症状も併せて消えた。

### BUG-7: シャーシ上書きの編集画面は範囲外を入力させてから弾く（重要度: 低）

**[解消: 2026-09-28。発見と修正は同日]**

- 再現: DB → 任意シャーシ → 内部減速比に `0`、タイヤ径に `200` を入力。
  入力中はエラーも出ず「保存」も押せる。押した瞬間に初めて
  「内部減速比は正の数値で入力してください」→（直すと）「タイヤ径は 40〜120mm の整数で入力してください」
  と 1 件ずつ出る。
- 影響: BUG-1 の再発には**至らない**（保存は拒否されるので不正値は DB に入らない）。
  UX だけの問題。`SheetEditScreen` は入力のたびに検証してエラーを出し保存ボタンを無効化するので、
  こちらだけ挙動が違う。
- 修正: `ChassisEditViewModel` を `SheetEditViewModel` と同じ形に揃えた。
  - `ChassisEditUiState` の単一の `errorMessage` を **項目ごとの `ratioError` / `tireError`** に分け、
    `onRatioChange` / `onTireChange` が入力のたびに検証して埋める。
    画面は `RcNumberField(error = ...)` で欄の下に出す（この部品は元から `error` を取れる）。
  - `canSave` を足し、エラーが残っている間は保存ボタンを無効にする（`SheetEditUiState.canSave` と同じ規約）。
  - **空欄はエラーにしない。** 消して打ち直している最中に赤くなるのは煩わしいだけで、
    「まだ入力していない」は違反ではない。空のまま保存されないことは `canSave` が担保する
    （内部減速比とタイヤ径は必須。空で保存すると「上書きを外す」＝リセットと区別が付かない）。
  - **読み込んだ値も init で検証に通す。** 古いデータや手で書いた JSON から範囲外の値が
    入っていた場合、開いた時点でエラーが出ていないと「触っていないのに保存できる」状態が残る。
- テスト: `ChassisEditViewModelTest` を新設（11 件）。この画面はテストが 1 本も無かったので、
  「標準値と同じ値は上書きにしない」という元からの仕様も併せて固定した。
- 確認: エミュレータ Pixel_8 で DB → TT-02 → タイヤ径に `200` を入力 →
  **その場で欄が赤くなり「タイヤ径は 40〜120mm の整数で入力してください」が出て、保存が押せない**。
  `65` に直すとエラーが消えて保存が有効に戻ることまで確認。
- **同型だった他の 3 画面も同日に揃えた**: `UserChassisEditViewModel` / `CarEditViewModel` /
  `SheetHeaderEditViewModel`。編集画面が 6 つある中で検証のやり方が 2 通りに割れていたのが
  問題の本体なので、規約として §5.9 に書き出した。**新しい編集画面もこの形に従うこと。**

---

## 3. 技術的負債（設計上のズレ）

PLAN.md が掲げた「Domain は Pure Kotlin、依存方向は UI → Domain ← Data」は
**Phase 0（S-5 / S-9）で実現した**。`:core:domain` は Android プラグインを適用していない
純 Kotlin JVM モジュールなので、逆依存を書いた瞬間にコンパイルエラーになる。

| ID | 内容 | 状態 |
|---|---|---|
| DEBT-1 | `domain/usecase` が `data.repository.*`（具象クラス）と `data.local.file.dto.ExportDataDto` を直接 import。Domain → Data の逆依存 | **解消（S-5）** `domain/repository/` に interface、`data/repository/*Impl` を `@Binds` で束ねた。エクスポート DTO への依存は `BackupCodec` で断った |
| DEBT-2 | `domain/model/UserPreferences` が `core.designsystem.theme.ThemeMode`（UI 層の enum）を参照 | **解消（S-5）** `ThemeMode` を `domain/model/` へ移動 |
| DEBT-3 | 計算ロジックが `core/domain/GearCalculator` にあり、`domain/` と二重パッケージ。テストも `core/domain` 配下 | **解消（S-5 / S-9）** `domain/calculator/GearCalculator` に統合し、`:core:domain` モジュールへ |
| DEBT-4 | UI 文言・エラー文言が Composable と ViewModel にハードコード（約 130 箇所）。`strings.xml` は `app_name` のみ。ViewModel が日本語文字列を組み立てている（`CalcViewModel.savedMessage`、`ConfigViewModel.message` など） | **解消（S-11 / c8fa274）** 全て `strings.xml` へ。ViewModel は `UiText`（リソース ID + 引数）を持つ。残る日本語リテラルは `@Preview` のサンプルのみ |
| DEBT-5 | Repository が具象クラスで interface が無い。ViewModel / UseCase のテストで MockK に頼ることになる（MockK は依存に入っている） | **解消（S-5 / S-6）** interface 化し、`app/src/test/**/fake/` に Fake 4 種を用意。Fake は本物の制約（ユニーク名・並び順）を再現する |
| DEBT-6 | `CalcRequestBus` はアプリスコープの可変グローバル状態。「流し込み」以外の用途が増えると追跡困難 | **解消（U-3 / 8bd116d）** ルート引数 `Calc(setupId)` に置換しクラスを削除。プロセス death で値が消える未記載の不具合も同時に解消（§5.6） |
| DEBT-7 | ルートが文字列（`"setups/$setupId"`）。Navigation 2.8 の型安全ルート（`@Serializable` data class）に移行可能 | **解消（S-12 / acbd038）** `@Serializable` ルート + `hasRoute()` 判定。引数の読み出しだけ自前（§5.6） |
| DEBT-8 | `calculation_history` テーブルは Insert のみで読み出し経路が無い。UI（ROADMAP F-1）を作るか、テーブルごと削除するか決める | **解消（M-3 / 0c94124）** テーブル・DAO・Entity・Repository ごと削除した |
| DEBT-9 | `PreferencesRepository` は `UserPreferencesDataSource` の透過的ラッパー。層を揃える以外の価値が無い | **そのまま**。interface 化で層は揃った。実害が無いので放置 |
| DEBT-10 | `CalcViewModel.init` に 3 本の `collect` が並び、`recalculate` は例外を投げうる（BUG-1/2 の受け口）。状態遷移がテストしづらい | **部分解消**。`recalculate` が例外を投げなくなり（S-1'）、CalcViewModel のテストも入った（S-6）。`update {}` 内の再計算は **解消（U-4 / 28c437b）** `setState()` に集約。`first { !it.isLoading }` は U-3 でバスごと消えた |
| DEBT-11 | `ExportDataUseCase` / `ImportDataUseCase` が `System.currentTimeMillis()` を直接呼ぶ。Repository も同様。時刻をテストで固定できない | **解消（S-6 / M-4）** `TimeProvider` を注入。`IdGenerator` は採番する行（UUID 主キー）が出た M-4 で追加した |
| DEBT-12 | `libs.versions.toml` にコメント「既存の行はそのまま、以下を追加」が残っている（作業メモの残骸） | **解消（S-10 の編集で消えた）** |
| DEBT-13 | `res/font/` が空。Google Fonts（`ui-text-google-fonts`）経由で Roboto Mono を取得している可能性があり、**完全オフライン** の方針と矛盾しうる。ネットワーク無し・初回起動の端末で等幅フォントが出るか確認する | **解消（S-8）** Roboto Mono を `res/font/` に同梱し `ui-text-google-fonts` と `font_certs.xml` を削除。ライセンスは `assets/licenses/RobotoMono-OFL.txt` |

---

## 4. リファクタリング方針（推奨順）

各項目は独立してコミットできる粒度にしてある。「検証」の欄が Definition of Done。

**進捗（2026-09-26 時点）**: REF-1 / REF-2 / REF-3 / REF-6 / REF-7 は完了。
REF-4（文言リソース化）と REF-5（型安全ルート）はセッティングシート化 Phase 1 で実施する。

### REF-1: 入力値検証の一元化（BUG-1 / BUG-2 / BUG-3 を同時に解消）

**[完了: S-1' / 45cc235]**

- `domain/model/GearCalculationInput` の companion に、範囲チェック関数（`isValidTireMm` など）と
  クランプ関数（`clampTireMm` など）を追加する。
- `ChassisEditViewModel`、`ImportDataUseCase`、`CalcViewModel.onChassisSelected` / bus 受信の 3 箇所から使う。
- インポートは `withTransaction`。
- 検証: `GearCalculationInputTest`（境界値）、`ImportDataUseCaseTest`（不正行スキップ・部分失敗時ロールバック）を追加。

### REF-2: Domain 層の純化（DEBT-1 / 2 / 3）

**[完了: S-5 / b7b2c0b・78b984c・3bcdedb・880163e、S-9 / 84155d9]** 4 番（エクスポート DTO の押し出し）は `BackupCodec` という port を domain に置き、JSON 実装を data に置く形で実現した。

1. `domain/repository/` に interface を切る（`ChassisRepository`、`SetupRepository`、`PreferencesRepository`、`CalculationHistoryRepository`）。
   `data/repository/` の具象クラスは `XxxRepositoryImpl` に改名し、Hilt の `@Binds` で束ねる（`data/di/RepositoryModule.kt` を新設）。
2. `ThemeMode` を `domain/model/` に移し、`core/designsystem/theme/Theme.kt` は import するだけにする。
3. `core/domain/GearCalculator` を `domain/calculator/`（または `domain/usecase/CalculateGearUseCase`）へ移動。テストも追従。
4. エクスポート DTO（`ExportDataDto`）は Data 層に残し、UseCase は「ドメインモデルのリスト」を返す形にして、JSON 化は Data 層（`ExportFileRepository` など）に寄せる。
- 検証: `domain/` 配下に `android.*` / `data.*` / `core.designsystem.*` の import が **ゼロ** であること（grep で機械的に確認できる）。
  将来 `:domain` を Pure Kotlin モジュールに切り出せる状態が完成条件。

### REF-3: テスト基盤の整備（DEBT-5 / 10 / 11）

**[完了: S-6 / e60ac3a・07acc9f・e3274f2]** `UnconfinedTestDispatcher` ではなく `StandardTestDispatcher` を使った。Unconfined だとコルーチンが起動と同時に走り切り、「DB ロード前に流し込み要求が来る」といった順序依存の不具合を再現できないため。

- `Clock` 相当の `TimeProvider` interface を導入し、Repository / UseCase に注入する。
- Repository に interface があるので、テスト用の Fake（in-memory 実装）を `app/src/test/.../fake/` に置く。MockK より Fake を優先する。
- `CalcViewModel` を `kotlinx-coroutines-test` の `runTest` + `UnconfinedTestDispatcher` で検証する。
  最低限: 起動時の前回値復元、シャーシ選択でタイヤ径が変わる、bus 受信で値が反映される、保存の重複名エラー。
- Room の in-memory DB を使う DAO テストは Robolectric が要るため、優先度は下げる。
  代わりに `app/schemas/` を使った `MigrationTestHelper` の準備だけしておく（Room v2 が出た時点で必須）。
- 検証: `testDebugUnitTest` で UseCase 3 本 + ViewModel 1 本以上がカバーされる。

### REF-4: UI 文言のリソース化（DEBT-4、ROADMAP F-4 英語化の前提）

**[完了: S-11 / c8fa274]** 実装した形は §5.6 を参照（`UiText` は当初案の `UiMessage` より汎用にした）。

- 手順: (1) `strings.xml` に日本語を全て移す → (2) Composable は `stringResource()` → (3) ViewModel は文字列ではなく
  `sealed interface UiMessage { data class SetupSaved(val name: String) ... }` を UiState に載せ、Composable 側で文字列に解決する。
- `DbFilter.label`、`TopLevelDestination.title` のような enum に日本語を持たせている箇所は `@StringRes` に変える。
- 機械的に洗い出すコマンド:
  ```
  grep -rn '"[^"]*[ぁ-んァ-ン一-龥][^"]*"' app/src/main/java --include=*.kt
  ```
- 検証: 上記 grep のヒットが KDoc / コメント以外でゼロ。

### REF-5: ナビゲーションの型安全化（DEBT-7）

**[完了: S-12 / acbd038]** `SavedStateHandle.toRoute<T>()` だけは使えなかった。理由は §5.6。

- Navigation Compose 2.8 の `@Serializable` ルート（`data object Calc`、`data class SetupDetail(val setupId: Long)`）に置き換える。
- `RcGearApp` の「派生画面で親タブを選択状態にする」判定は `hasRoute<>()` に変える。
- 検証: 4 タブ遷移、詳細 → 戻る、SETUPS → CALC 流し込みが手動で動作。

### REF-6: 表示整形の共通化（BUG-4）

**[完了: S-7 / b624b8c]** ただし動機は BUG-4 ではない（§2 参照）。

- `core/ui/Format.kt` に `Double.formatRatio()`（小数 2 桁）、`formatSpeed()`（小数 1 桁）、`Int.formatRpm()` を置く。
  20 箇所の `String.format` を置き換える。Locale は `Locale.US` 固定（ドメインの慣習として小数点は `.`）。
- 検証: `FormatTest` を追加。

### REF-7: 静的解析と CI（負債の再発防止）

**[完了: S-4 / a5de750、S-3]**

- `ktlint`（Gradle plugin `org.jlleitschuh.gradle.ktlint`）または `detekt` を導入。既存コードの違反は一括整形して 1 コミットで済ませる。
- GitHub Actions: `ubuntu-latest` + `actions/setup-java@v4`（temurin 21）+ `gradle/actions/setup-gradle@v4` で
  `assembleDebug` / `testDebugUnitTest` / `lintDebug`。BUG-5 の修正が前提。
- 検証: PR で CI が緑になる。

### やらないこと（当面）

- **`:feature:*` の分割**: 単独開発でこの規模だと、feature 間参照を断つための navigation 抽象レイヤを
  自作するコストが並列ビルドの利益を上回る。**「クリーンビルド 2 分超え」を唯一のトリガー**にする。
  （`:core:domain` の切り出しはビルド時間ではなくレイヤ強制が目的なので、この方針とは別。S-9 で実施済み）
- `:core:designsystem` のモジュール分離: Preview の反復が遅いと感じたらやる。今は不要。
- Dynamic Color: HUD 調テーマは意図的な設計。
- ~~`CalcRequestBus` の廃止~~ → **方針変更**。用途が 4 つに増える（シート→CALC、CALC→シート、
  シート→比較、車→ベースライン複製）ので「用途が 1 つなら害がない」という前提が崩れた。
  さらに **プロセス死で値が消える**（バックスタックは復元されるがバスは空）という未記載の不具合もある。
  Phase 1 の U-3 で nav 引数 + `SavedStateHandle` に置き換える。

---

## 5. セッティングシート化の設計判断（2026-09-26 決定）

拡張計画そのものは別ファイル（リポジトリ外の作業計画）にあるが、**後から蒸し返されやすい判断**は
理由ごとここに残す。AGENTS.md §7-3 の「設計判断を変えたら HANDOFF に追記する」に従う。

### 5.1 別アプリには分けない

「ギア比計算アプリ」と「セッティングシートアプリ」を分ける案を検討し、**却下した**。
判定基準は「データ・利用の瞬間・実行環境・ユーザーの 4 つが同時に分離しているか」で、4 つとも No。
ギア比はシートの 1 セクションであり、シャーシ / ピニオン / スパー / タイヤ径を共有する。
分けるとプロセス境界越しの二重管理（ContentProvider か SAF）になり、
**1 つのピットテーブルの中に分散システムの整合性問題を自作する**ことになる。

分割が正しくなる条件（将来該当したらその時に）: 実行環境が違う（Wear OS / ウィジェット）、
リリースケイデンスが違う（Play 公開後）、ユーザーが違う、配布制約。
**正しい分割軸は APK ではなく Gradle モジュール**（S-9 で `:core:domain` を切った）。

### 5.2 Room の移行は手動（AGENTS.md DoD #4 の例外）

Phase 2 でテーブルを作り直す（`cars` / `setup_sheets` / `setup_values` / `user_chassis` を追加、
`saved_setups` と `calculation_history` を削除）が、**Room の `Migration` は書かない**。

- 未公開・利用者は本人 1 人なので、データ消失のリスクを負うのは自分だけ
- 移行手段は「v1 JSON をエクスポート → 破壊的再作成 → v2 インポータで戻す」
- こちらのほうが純 Kotlin でテストでき（Robolectric / androidTest 不要）、
  AGENTS.md §4「旧バージョンの読み込みは残す」を自然に満たす
- 破壊的変更の許可は「データ消失」ではなく「移行コードの簡略化」に使う

このため **AGENTS.md の DoD #4 を「Migration を書く、または移行手段をドキュメント化する」に緩めた**。
**公開に踏み切る場合はこの例外を撤回すること。**

#### 破壊的再作成の範囲は v1 → v2 に限定する（Phase 0 レビューで修正）

S-10 で入れた `fallbackToDestructiveMigration()`（無引数）は「**将来の全バージョン**で
移行漏れを黙ってデータ消失に変える」設定だった。手動移行を選んだのは v1 からの 1 回だけなので、
`fallbackToDestructiveMigrationFrom(1)` に変更した（`data/di/DatabaseModule.kt`）。
v2 以降で Migration を書き忘れた場合は起動時に `IllegalStateException` で落ちる ＝ 気づける。

#### 手動移行で戻らないもの（移行手順に必須）

エクスポート JSON に入るのは **保存セッティングとシャーシ上書きだけ**（`ExportDataDto`）。
v1 → v2 の作り直しで、次は戻らない:

| 失うもの | 保存先 | 扱い |
|---|---|---|
| 計算履歴（`calculation_history`） | Room | Phase 2 の M-3 でテーブルごと削除する予定なので捨てて構わない（DEBT-8） |
| テーマ / mph 併記 / 基準 FDR / 前回入力値 | DataStore | **Room を作り直しても消えない**（別ストア）。ただしアプリのデータ削除・再インストールを挟むと消える。手で設定し直せる 5 項目なので復元手段は作らない |

つまり移行手順は「① CONFIG でエクスポート → ② アプリのデータを消さずに v2 を入れる
（Room だけが作り直される）→ ③ v2 インポータで JSON を戻す」。
②で端末のアプリデータを丸ごと消すと DataStore の表示設定も失うので、消さないこと。

### 5.3 フィールド定義は Kotlin のレジストリ

40〜80 項目を固定カラムで持つと 1 項目追加のたびに 9 箇所を直すことになるので、
値は `(sheetId, fieldKey)` の EAV で持ち、**項目の定義（キー・型・範囲・単位・並び）は
`:core:domain` の Kotlin `object`（`TouringSetupSchema`）に置く**。

`assets/setup-schema.json` 案は却下した。Kotlin 定数と JSON で二重の真実になり、
ラベルが `strings.xml` の外に出て英語化の手順から外れるため。
Kotlin レジストリならキーの誤りはコンパイルエラーになる。

完全型付け（セクションごとの data class）も却下。60 項目ぶんのフィールド + Composable +
バリデーション + 比較処理の手書きになり、避けたかった爆発そのもの。

#### ラベルはレジストリに持たせない（Phase 0 レビューで修正）

当初この節には「ラベルは `@StringRes` のまま」と書いていたが、**成立しない**。
`R.string` を生成するのは `:app` であり、`:core:domain` は純 Kotlin JVM モジュールなので
`R` を参照できない（`androidx.annotation.StringRes` 自体は JVM artifact なので付けられるが、
**渡せる ID が無い**）。矛盾したまま Phase 1 に入ると、`:core:domain` に `:app` を
逆依存させるか、レジストリを `:app` に逃がすかの二択を実装中に迫られる。

決着: **定義は domain、ラベルの解決は `:app`。**

- `:core:domain` … `SetupField`（`key` / 型 / 範囲 / 単位 / 並び / 既定値）と
  `TouringSetupSchema`。検証・差分・比較はここで完結する（Android 不要）
- `:app` … `feature/sheet/SetupFieldLabels.kt` に
  `@StringRes fun SetupField.labelRes(): Int` を置き、`strings.xml` を引く
- 漏れ防止 … 「レジストリの全フィールドがラベルを持つ」ことを `:app` の単体テストで検証する
  （`TouringSetupSchema.allFields.forEach { assertNotEquals(0, it.labelRes()) }`）。
  フィールド追加時にラベルを忘れるとテストが落ちる

レビューで示された「レジストリ自体を `:app` に置く」案は採らない。検証と差分処理は domain 側の
仕事であり、レジストリを `:app` に置くと domain が型・範囲を再宣言することになって
二重の真実が戻ってくる。分ける境界は「項目の定義」と「文言」であって、「項目」ではない。

### 5.4 Phase 0 で見つかった、記載の無かった問題

| 内容 | 決着 |
|---|---|
| `.fallbackToDestructiveMigration()` が無く、`version` を上げるとワイプではなくクラッシュする | S-10 で追加 |
| `saved_setups.name` にグローバル UNIQUE インデックス。車が 2 台あると両方に「Rd1」を作れない | `setup_sheets.name` には引き継がない |
| **エクスポート JSON に `schemaVersion` が出力されていなかった**。kotlinx.serialization は既定値と一致するフィールドを書き出さないため、実際の出力は `{"exportedAt": ...}` だけだった | S-6 で `encodeDefaults = true`。旧ファイルは従来どおり 1 として読めるのでバージョンは上げない |
| `gradlew` に実行ビットが無い（100644）。Windows でしか動かしていなかったため気づいていなかった | S-4 で 100755 に。`.gitattributes` で `eol=lf` も固定 |
| instrumented テストのメソッド名にスペースを入れると `dexBuilder` が落ちる（minSdk 26 は DEX < 040）。JVM 単体テストとルールが違う | S-10 で踏んだ。AGENTS.md §5 に明記 |
| ktlint の既定ルールのうち 4 つ（`no-multi-spaces` / `argument-list-wrapping` / `function-signature` / `discouraged-comment-location`）が、意図的な桁揃え・表形式のコードを壊す | S-3 で無効化。理由は `.editorconfig` に記載 |

### 5.5 Phase 0 レビュー（2026-09-26）の指摘と決着

Phase 0 完了後に受けた外部レビュー。Phase 1 に入る前に処理した。

| 指摘 | 決着 |
|---|---|
| 高: `fallbackToDestructiveMigration()` の対象が全バージョンで、将来の移行漏れもデータ消失になる | `fallbackToDestructiveMigrationFrom(1)` に限定（§5.2） |
| 高: `ExportDataDto.schemaVersion` の既定値が `CURRENT_SCHEMA_VERSION` に連動しており、v2 を出すと `schemaVersion` キーの無い旧ファイルを v2 と誤認する | 既定値を `OMITTED_SCHEMA_VERSION = 1`（不変）に分離し、書き出し側は版を明示。S-6 以前の実形式を `JsonBackupCodecTest` のゴールデンデータとして固定（codec の形式テストはこれまで 0 件だった） |
| 高: Kotlin レジストリ案が `:core:domain` で `@StringRes` を持つ前提になっており、モジュール境界と矛盾する | 定義は domain、ラベル解決は `:app`（§5.3） |
| 中: CI が一度も実行されていない / instrumented テストが端末上で未実行 | **解決**。PR #1 で CI 初回実行が全ステップ緑（5m14s）。DAO テスト 6 件も AVD `Pixel_8`（API 34）で実行し failures 0。手順は §6 に記載 |
| 中: DAO テストの「一括挿入は 1 トランザクション」が正常系しか見ておらず、失敗時のロールバックを検証していない | UNIQUE 違反で全件ロールバックすることを確認するテストを追加（androidTest 6 件目） |
| 低: `AGENTS.md` に「`org.gradle.java.home` はコミット済み」「ktlint 未導入」という古い記述が残っている | 両方修正 |
| 手動移行で DataStore の表示設定と計算履歴が戻らない点が未記載 | §5.2 に表で明記 |

### 5.6 Phase 1 の設計判断（2026-09-26）

Phase 1（S-12 / U-3 / U-4 / U-1 / U-2 / S-11）で決めたこと。
実施順は依存に従い S-12 → U-3 → U-4 → U-1 → U-2 → S-11 にした
（文言リソース化を最後にすると、移設で動いたコードを 1 回で掃ける）。

#### `SavedStateHandle.toRoute<T>()` は使わない

型安全ルート（S-12）の受け取りは本来 `savedStateHandle.toRoute<SetupDetail>()` だが、
**これは内部で `android.os.Bundle` を組み立てる**（`RouteDecoder` → `bundleOf`）。
Robolectric を入れていない JVM 単体テストでは
`Method putString in android.os.BaseBundle not mocked` で落ちる。
ViewModel のテストを Robolectric 抜きで書き続けるほうが大事なので、
`navigation/Routes.kt` に `SavedStateHandle.calcRoute()` などの復元関数を置き、
キーを直接読む（`SavedStateHandle` には NavType が put した生の値が入っている）。

危ないのは「キー名 = ルートクラスのプロパティ名」という暗黙の対応で、
**その対応をルート定義と同じファイルに閉じ込める**のがこの関数群の役目。
Robolectric を入れる日が来たら `toRoute<T>()` に戻してよい。

#### 流し込み遷移で `restoreState = true` を使わない

`SetupDetail` →「CALC に流し込む」は `navigate(Calc(setupId = ...))` で引数を渡す。
ここでタブ切替と同じ `popUpTo(startDestination) { saveState = true } + restoreState = true` を
付けると、**保存済みの CALC エントリ（＝古い引数）が復元されて新しい `setupId` が無視される**。
`popUpTo<Calc> { inclusive = true }` で既存の CALC エントリを置き換える形にした。

#### `ScreenEvents` は「バスを消してバスを足した」のではない

U-3 で `CalcRequestBus`（アプリスコープの `@Singleton`）を消し、U-2 で `ScreenEvents`
（`Channel` ベース）を足したので一見矛盾するが、性質が違う:

| | `CalcRequestBus`（削除） | `ScreenEvents`（追加） |
|---|---|---|
| 寿命 | アプリ全体・シングルトン | ViewModel 1 つに 1 つ |
| 送り手と受け手 | 別の画面（SETUPS → CALC） | 同じ画面（VM → その画面） |
| プロセス death | 値が消える | 状態ではないので復元不要 |

「戻る」を UiState の Boolean（`isDone` / `isDeleted` / `notFound`）で表すのをやめたのは、
戻った後も true のまま残り、再コンポーズの経路によっては 2 回 pop しうるため。
バッファ付き Channel なので、`init` で即 emit する「対象が見つからない」も取りこぼさない。

#### `UiText`: ViewModel は文言ではなくリソース ID を持つ

当初案の `sealed interface UiMessage`（メッセージごとにケースを作る）ではなく、
`UiText.Res(id, args)` / `Raw` / `Joined` / `Empty` の 4 種にした。
ケースを増やさずに済み、**引数に `UiText` を入れると再帰的に解決される**ので
「取り込み完了: セッティング 3件（同名スキップ 1件）/ 上書き 2件」のような入れ子も組める。
テストは文言ではなく `UiText.Res(R.string.calc_saved, listOf("Rd1"))` の同値で書ける。

例外を 2 つ置いた:

- **常に同じ a11y ラベル**（戻る矢印、ステッパーの ±）は designsystem の部品内で
  `stringResource` する。呼び出し側に毎回書かせると、書き忘れた画面だけ読み上げが無音になる
- **`@Preview` のサンプルデータ**は日本語のままにする。出荷されない開発用の値で、
  翻訳対象ではない。HANDOFF の grep チェックもこの 2 つを例外として読むこと

`ThemeMode` のラベルは `:core:domain`（純 Kotlin、`R` を参照できない）に enum があるため、
対応表を `:app` 側の拡張プロパティに置いた。§5.3 で決めた「定義は domain、文言は `:app`」の
最初の実例になっている。

#### `setState()`: `MutableStateFlow.update {}` に再計算を入れない

`update` は CAS のリトライでラムダを何度も呼ぶ契約なので、再計算・クランプ・
`return@update` による中断のような「状態を作る以外のこと」を置く場所ではない。
`CalcViewModel.setState()` に集約し、`update` に渡すのは純粋な `copy()` だけにした。
状態変更は全てメインディスパッチャ上（UI コールバックと `viewModelScope` の collect）なので、
CAS ループ無しの read-modify-write で足りる。

#### Phase 1 の実機確認（2026-09-26、AVD `Pixel_8` / API 34）

`:app:installDebug` して次を確認した（S-12 / U-3 は runtime にしか出ない失敗モードがある）:

1. 起動 → CALC（タブタイトルがリソースから引けている）
2. 4 タブ往復、詳細画面で親タブが選択状態のまま（`hasRoute()` 判定）
3. シャーシ選択 → 保存 → SETUPS → 詳細 → 「CALC に流し込む」で値が反映される
4. CALC でピニオンを 22T → 34T に変えてから再度流し込むと **22T に戻る**（ルート引数が効いている）
5. `am kill` でプロセスを殺して再起動しても 22T のまま（旧バスでは消えていた）

### 5.7 Phase 2 の設計判断（2026-09-26）

Phase 2（M-1 〜 M-8）で決めたこと。実施順は M-1 → M-2 → M-5 → M-3 → M-4 → M-6 → M-8 → M-7 で、
純粋なドメイン（レジストリ・値・差分）を先に固めてから Room を作り替えた。

#### Phase 2 完了時点でアプリは「CALC / DB / CONFIG」の 3 タブ

M-3 で `saved_setups` をテーブルごと消したので、それに乗っていた SETUPS 画面・
`SaveSetupUseCase`・`SetupRepository`・CALC の保存ボタンと「流し込み」も同時に撤去した。
**これは意図した中間状態**で、置き換えとなる GARAGE は Phase 3 の G-1、
CALC ↔ シートの往復は G-5 で入る。

「SETUPS を Phase 3 まで残す」案は採れなかった。残すには `saved_setups` を v2 に
引き継いで v3 で消すことになり、破壊的再作成を「v1 → v2 の 1 回だけ」に限る決定（§5.2）と衝突する。

#### 移行手順（この版を端末に入れる前にやること）

1. **現行（v1）の APK で CONFIG からエクスポート**し、JSON を退避する
2. この版を入れる（初回起動で Room が作り直される。アプリのデータは消さないこと。
   消すと DataStore の表示設定 5 項目も失う。§5.2 の表を参照）
3. CONFIG からその JSON をインポートする。v1 の保存セッティングは
   **シャーシごとに 1 台の車へまとめられ、各セッティングが 1 枚のシート**になる

v1 ファイルは id を持たないので、取り込みのたびに新しい UUID が振られる（＝冪等ではない）。
2 回読むと二重になるので 1 回だけにすること。v2 以降は id upsert なので冪等。

#### 範囲の検証は `GearCalculationInput` から `FieldValidator` へ

M-2 で `GearCalculationInput.init` の範囲 `require` を外し、レジストリ駆動の
`FieldValidator` に移した。`init` に残したのは **計算式が成立しない 3 条件**
（`pinion > 0` / `spur > 0` / 内部減速比が正の有限値）だけ。

理由: BUG-1 / BUG-2 はどちらも「範囲外の値が例外になって画面ごと落ちる」形で出ていた。
セッティングシートは実測値も書ける場所なので、**スライダーの外＝存在してはいけない値ではない**。
ゼロ除算だけを `init` に残したことで、`GearCalculator.calculate()` は
非 null を返す全域関数のままでいられる（結果を nullable にすると全呼び出し側に分岐が増える）。

`GearCalculationInput` の定数は残る。これは「CALC 画面のスライダーが動ける範囲」を表し、
レジストリのギアセクションと同じ値であることは `TouringSetupSchemaTest` が保証する。

#### 単位もラベルと同じく `:app` で解決する

§5.3 で「定義は domain、文言は `:app`」と決めたが、単位（mm・°・T）も同じ扱いにした。
`unit = "mm"` の文字列をレジストリに持たせると、出す / 出さない・前後どちらに置くかという
表示の判断がドメインに漏れる。domain は `FieldUnit` の enum だけを持ち、記号は `strings.xml` にある。

#### 取り込みの棄却は「上書きは行ごと・シートの値は項目ごと」

非対称にしてある。上書きは 1 件が 1 つの値なので「行を棄却」と「値を捨てる」が同義だが、
シートは数十項目の集まりで、1 項目の範囲外で 1 セッション分を丸ごと捨てるのは損が大きい。
落とした項目数は取り込み結果で報告する。**レジストリに無いキーは検証対象外でそのまま保存する**
（将来の項目が入ったファイルを読んでも値が消えない ＝ スキーマを安全に進化させる担保）。

#### `hasCenterDiff` は「分からないから隠す」をしない

M-7 で `category` は全 45 件に付けたが、`drive` は裏の取れたものだけ、`hasCenterDiff` は
全件未設定にした。`ChassisTraits.satisfies()` は **不明（null）なら項目を出す**。
分からないことを理由に設定欄が無言で消えると、ユーザーには壊れているのと区別が付かない。
隠すのは「その車には確実に存在しない」と分かっている場合だけ。

#### Fake は「ただ動く」ではなく「本物と同じ制約で動く」

`FakeTransactionRunner` は実際にロールバックする（開始時の状態を控えて書き戻す）。
ただブロックを実行するだけの Fake にすると、M-8 で入れたトランザクション境界が
効いていなくてもテストが通ってしまう。`FakeCarRepository` / `FakeSetupSheetRepository` も
並び順・CASCADE・SET NULL・upsert の冪等性を再現する。S-6 で同名制約を再現したのと同じ理由。

#### Phase 2 で見つかった、記載の無かった問題

| 内容 | 決着 |
|---|---|
| プロパティアクセサの中の `field` は裏フィールドを指す予約語。`FieldDiff` の `val isUnknownField get() = field == null` が「初期化が必要」でコンパイルエラーになる | `this.field` と書く。`SheetDiff.kt` にコメントを残した |
| `saved_setups` を消すと KSP / Hilt の生成物が古いまま残り、`hiltJavaCompileDebug` が消えたクラスを探して落ちる | `app/build/generated/{ksp,hilt}` を消して再ビルドする |
| 既存 `strings.xml` に `unit_teeth` / `unit_millimeter` / `unit_cells` が既にあった | 新規に作らず共有する。重複定義は `mergeDebugResources` が検出する |

### 5.8 Phase 3 の設計判断（2026-09-28）

Phase 3（G-1 〜 G-7 + F-1 / F-4 / F-5）で決めたこと。実施順は
G-1 → G-2 → G-3 → G-4 → G-5 → G-6 → G-7 → F-1 → F-5 → F-4。
**画面を作ってからレジストリを横展開した**（G-7 を最後にした）のは、
「項目を足しても Composable が増えない」ことをその順序でしか確かめられないため。

#### 新規作成と編集は 1 画面。分岐はルート引数の有無

車（`CarCreate` / `CarEdit`）と自作シャーシ（`UserChassisCreate` / `UserChassisEdit`）は
同じ Composable・同じ ViewModel を 2 つのルートから開く。違いは「保存が create か update か」と
「削除を出すか」だけなので、画面を割る理由が無い。

`CarEdit(carId: String? = null)` の 1 ルートにまとめる案は採らなかった。
型安全ルートの nullable 引数は文字列 `"null"` の扱いに実装依存の癖があり、
**「新規かどうか」を引数の有無で表すほうが受け取り側も素直**になる
（`savedStateHandle.carEditRouteOrNull()` が null を返す）。
CALC の `Calc(sheetId: String? = null)` だけは nullable 引数のままだが、
あれは U-3 で実機確認済みの形（§5.6）をそのまま戻したもの。

#### シートの値編集は「1 画面 1 セクション」

全項目を 1 画面に並べると、G-7 の横展開後（60 項目超）は目的の欄まで延々スクロールになる。
シート閲覧のセクションカードをタップ → そのセクションだけの編集画面、という形にした。
ヘッダ（名前・走行条件・ベースライン）はセクションではないので別画面（`SheetHeaderEdit`）。

#### 不正な入力は「確定しない」

範囲外・数値でない入力は**下書きの文字列として保持し、値は確定しない**。
確定すると「画面には 999 と出ているのに保存されるのは 40」というズレが生まれる。
違反が 1 つでもあれば保存ボタン自体を無効にする。
検証そのものは `:core:domain` の `FieldValidator`（レジストリ駆動）に任せ、
`:app` は違反を `UiText` にする（`feature/sheet/FieldViolationText.kt`）だけ。

#### 保存は「変わったキーだけ」

EAV（`(sheetId, fieldKey)`）なので 1 項目 1 行の upsert で済む。
束ごと `replaceValues` すると、編集していないセクションの値を巻き込んで消す危険がある。
CALC からの書き戻し（G-5）も同じ理由で 5 項目だけを upsert する。

#### `RcDetailScaffold` の「戻る」は 2 つに分かれた

`onNavigateBack`（`ScreenEvent.NavigateBack` を受けたときの処理）と
`onBackClick`（矢印を押されたときの処理）を分けた。分けないと、未保存確認を
入れた画面（G-4）で **確認が出続けて画面から出られなくなる**
（破棄イベント → onNavigateBack → 確認 → …）。既定では両方同じ関数なので、
他の画面は無改造のまま。

#### 走行結果の「手応え」は値（bag）、ラップタイムはヘッダ

気温やコース名をヘッダに置いたのと同じ線引き（`SetupSheet` の KDoc）だが、
手応え（進入・クリップ・立ち上がり・グリップ感）は**差分に出したい**ので bag に入れた
（「前回はアンダー、今回はニュートラル」が軸 B で並ぶ）。
ベストラップはセッティングの評価ではなくそのセッションの記録なのでヘッダのまま。

#### 差分の左右は表示側で 1 回だけ入れ替える

`SheetDiff` は主役（このシート）を `left`、相手を `right` と呼ぶが、
画面は「元の値 → 今の値」と読めるほうが自然なので左右が逆になる。
入れ替えは `SheetCompareScreen.DiffRow` の 1 箇所だけで行い、他では読み替えない。

#### G-7 の受け入れ条件は満たせた

駆動系・タイヤ・ESC・車体・走行結果（約 30 項目）を足したコミット `47b1e5d` が触ったのは
**`TouringSetupSchema` / `FieldDef` の単位 enum / `strings.xml` / `SetupFieldLabels` の 4 ファイルだけ**で、
Composable の差分はゼロ。項目追加の手順は AGENTS.md §6 の表のとおり
（単位を増やすときだけ `FieldUnit` にも 1 行足す）。

#### F-5: `user_` 接頭辞は規約であって飾りではない

自作シャーシの id は Repository が `user_<uuid>` で採番し、画面には触らせない。
この接頭辞は「同梱 DB と衝突させない」ためだけでなく、
**インポートの判定**（`isKnownChassis`）と **一覧の振り分け**（自作は別の編集画面へ）も見ている。
取り込み時に接頭辞を持たない自作シャーシは棄却する — 通すと同梱エントリを
名前で乗っ取る id が作れてしまう。

同じメーカー名の自作シャーシは既存の見出しに合流させる（「タミヤ」が 2 つ並ばない）。
エクスポート JSON には `userChassis` キーを**追加**したが **`schemaVersion` は上げない** —
既存キーの意味を変えていないため（AGENTS.md §4）。古いアプリで読むと自作シャーシの定義だけが
落ち、それを指す車は「不明なシャーシ」として残る。

#### F-4: `Context` を配らず `Strings` を挟む

テキスト版シートの組み立てはコンポジションの外で走るので `stringResource` が使えない。
`Context` を直に渡すと**その関数が JVM 単体テストから呼べなくなる**（Robolectric は入れない方針）。
`core/ui/Strings`（`fun interface`）を 1 段挟み、本番は `Context.asStrings()`、
テストは id をそのまま返す Fake を渡す。
`UiText` とは役割が違う — あちらは「文言を運ぶ入れ物」、こちらは「文字列にする関数」。

共有ファイルは `cache/shared/` だけを `FileProvider` に公開する（`res/xml/file_paths.xml`）。
ルートを公開すると、エクスポート JSON やデータベースまで Uri を組み立てれば読める状態になる。

#### Phase 3 で見つかった、記載の無かった問題

| 内容 | 決着 |
|---|---|
| `stateIn(WhileSubscribed)` の `uiState` は **購読者がいないと一度も流れない**。テストで `advanceUntilIdle()` だけ呼んでも初期値のまま | テスト側で `backgroundScope.launch { vm.uiState.collect {} }` を張る（`GarageViewModelTest`） |
| Material3 の `DatePicker` が返すのは **UTC の 0 時**。そのまま保存すると UTC より西の地域で前日として表示される | 選ばれた日付を端末時間の正午に置き直してから保存する（`SheetHeaderEditViewModel.onDatePicked`） |
| import を足すと ktlint の並び順違反になりやすく、`assembleDebug` より先に `ktlintCheck` が落ちる | 変更のたびに `ktlintFormat` を挟む。CI の最初のステップと同じ順序で確認する |
| `RcSlider` は Int 専用。レジストリに小数のスライダー項目を足すと型が合わない | `FieldEditor` が `decimals > 0` のスライダーをステッパーに落とす（現状レジストリに該当は無い） |

### 5.9 入力フォームの検証規約（2026-09-28。BUG-7 とその横展開）

編集画面が 6 つになった時点で、**検証のやり方が 2 通りに割れていた**。
`SheetEditScreen`（シートの値）だけが入力のたびに検証し、残り 4 つは
保存ボタンを押してから違反を 1 件ずつ出していた。同じアプリの中で
「入力した瞬間に赤くなる画面」と「押すまで何も言わない画面」が混ざるのは、
どちらが正しいかという話の前に一貫していないことが問題なので、
**入力のたび**に揃えた。規約は次の 4 つ。

1. **検証は入力のたび。** `onXxxChange` が値と一緒にその項目のエラーを埋める。
   エラーは項目ごとの `xxxError: UiText?` に持ち、画面は `RcNumberField(error = ...)` /
   `RcTextField(error = ...)` で**欄の下**に出す。画面下部に 1 件だけ出す
   `errorMessage` は廃止した（どの欄の話か分からないため）。
2. **`canSave` は UiState が計算する。** エラーが 1 つでも残っていれば false。
   画面は保存ボタンの `enabled` に渡し、ViewModel の `onSave()` も先頭で
   `if (!state.canSave) return` と二重に止める（画面を経由しない呼び出しへの備え）。
3. **空欄はエラーにしない。** 消して打ち直している最中に赤くなるのは煩わしく、
   「まだ入力していない」は違反ではない。必須項目が空のまま保存されないことは
   `canSave` 側（`isNotBlank()`）が担保する。任意項目（走行条件）は空欄のままでも保存できる。
4. **読み込んだ値も init で検証に通す。** 取り込んだ JSON や古いデータに範囲外の値が
   入っていた場合、開いた時点でエラーが出ていないと「触っていないのに保存できる」状態が残る。

「入力できるが不正」な状態が無い項目（車の名前、メーカー名のような自由文字列、
一覧から選ぶシャーシ）には**エラー文を持たせない**。`canSave` が false になるだけで、
何が足りないかは欄が空であることとヒント文で分かる。
このため `car_edit_error_name` など 4 つの文言は使わなくなったので削除した。

対象は `SheetEdit` / `ChassisEdit` / `UserChassisEdit` / `CarEdit` / `SheetHeaderEdit` の 5 画面。
**新しい編集画面を足すときもこの形に従うこと。**

---

## 6. 運用メモ

- ルートの `AGENTS.md` をエージェントが自動で読む。作業指示はそこに集約し、このファイルは「状態の記録」に使う。
- Gradle デーモンが起動できるかを最初に確認する。失敗した場合は `AGENTS.md` §2 の loopback 回避策を試す。
- 作業単位は「1 タスク 1 コミット」。REF-* / S-* / F-* の ID をコミットメッセージや PR タイトルに入れると追跡しやすい。
- **ktlint の設定を変えたら `--rerun-tasks` ではなく `gradlew --stop` を挟んで確認する。**
  ワーカーが `.editorconfig` の解決結果を抱え込むことがあり、設定が効いているかの判断を誤る
  （S-3 で実際に何度も誤った計測をした）。

### 6.1 instrumented テストの実行手順

CI には入れていない（GitHub Actions でエミュレータを起動すると 1 回あたり数分＋不安定さが増すため、
Room を v2 に作り替える Phase 2 までは手動実行で足りると判断した）。ローカルでの手順:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$SDK = "$env:LOCALAPPDATA\Android\Sdk"

& "$SDK\emulator\emulator.exe" -avd Pixel_8 -no-snapshot-load -no-boot-anim   # 別ウィンドウで起動したまま
& "$SDK\platform-tools\adb.exe" wait-for-device                               # boot_completed=1 まで待つ
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

実機で走らせる場合は AVD を起動せず、USB デバッグを有効にした端末を繋いで同じコマンドを叩く。
**エミュレータと実機が同時に繋がっていると `more than one device/emulator` で落ちる**ので、
`ANDROID_SERIAL` でどちらかを名指しする（`adb devices` でシリアルを見る）:

```powershell
$env:ANDROID_SERIAL = "HQ626507E2"        # 実機。エミュレータなら "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

- 結果 XML は `app/build/outputs/androidTest-results/connected/debug/`、HTML は `app/build/reports/androidTests/connected/`。
- **コンソール出力は端末によって「Finished 16 tests」としか言わず、失敗件数を出さないことがある**
  （エミュレータでは `Tests 16/16 completed. (0 failed)` まで出たが、実機では出なかった）。
  緑かどうかは終了コードか XML の `failures` / `errors` 属性で確認する。
- **このタスクは終わったあとに app と androidTest の APK を両方アンインストールする。**
  続けて手で操作したいときは `adb install -r -t app/build/outputs/apk/debug/app-debug.apk` で入れ直す。
- AVD が無い場合は Android Studio の Device Manager で作る（API 34 / `Pixel_8` で確認済み）。
  minSdk 26 なので古い API でも動くはずだが未検証。
- **端末が 2 台繋がっているときは `$env:ANDROID_SERIAL` を必ず指定する。** 指定しないと
  繋がっている全端末で走り、双方の APK を入れ替えてしまう。
  ```powershell
  $env:ANDROID_SERIAL = "emulator-5554"   # adb devices で出る serial
  ```
- 2026-09-28 の実績: DAO テストは Pixel_8（API 34）・SO-53C（Android 14 / API 34）とも **16 件全緑**。
  BUG-6 の修正後は `SetupSheetRepositoryImplTest` 7 件と DAO の 1 件を足して
  **Pixel_8 で 24 件全緑**（実機は修正前の 16 件までしか回していない）。

## 7. 動作確認チェックリスト（手動、リリース前）

Phase 3 時点の版。GARAGE / シート系（10〜18）が Phase 3 で入った分。

1. 初回起動: スプラッシュ → CALC、シャーシ未選択でも落ちない
2. シャーシ選択 → タイヤ径が自動で変わる → スライダー操作 → HUD が即時更新
3. DB で内部減速比を上書き → CALC の結果が追従する → リセットで戻る
4. DB で内部減速比に範囲外の値（0 や 200）を入れても落ちない
5. CONFIG: テーマ 3 種、mph 併記 OFF、基準 FDR 変更で傾向バーの中心が動く
6. エクスポート → 全データ削除 → インポート → 復元される
7. **v1 の JSON をインポート** → 車とシートが作られる（§5.7 の移行手順）
8. 画面回転・バックグラウンド復帰で入力値が残る
9. 端末を機内モードにして 1〜8 が動く（オフライン要件）
10. GARAGE: 車を追加 → 一覧に出る → 編集で名前とシャーシを変えられる → アーカイブで一覧から消え、タブで出る
11. 車詳細: 「新規シート」でシートが起き、ギアに内部減速比とタイヤ径が焼き込まれている
12. 「複製」で値が引き継がれ、元シートがベースラインとして一覧に出る
13. シート閲覧: F/R のグリッドで値が読める。ギアの FDR / 最高速 / ロールアウトが出る
14. セクションをタップ → 編集 → 範囲外の値でエラーが出て保存ボタンが無効になる →
    直すと保存できる → 戻ると値が反映されている。未保存で戻ると確認が出る
15. シートの情報（ヘッダ）: 走行日を選ぶと一覧の並び順が変わる。ベースラインを選べる
16. 比較: 3 つの軸（キット標準 / ベースライン / 他のシート）が切り替わり、変更点だけが出る
17. 「CALC で調整する」→ 値が流し込まれる → ピニオンを変えて「このシートに反映」→
    シートに戻ると更新されている。**内部減速比は変わっていない**
18. DB: 自作シャーシを追加 → 一覧にメーカー見出し付きで出る → 車に選べる →
    エクスポート → 全データ削除 → インポートで自作シャーシごと戻る
19. 共有: シートの共有アイコンでテキストが飛ぶ。CALC の共有 FAB で画像が飛ぶ

### 7.1 実機確認の結果（2026-09-28 実施）

環境: エミュレータ **Pixel_8 / API 34 / 1080x2400**、実機 **Sony SO-53C（Xperia Ace III）/
Android 14 / API 34 / 720x1496 @300dpi**。debug ビルド（`0.1.0-debug`）を adb でインストールし、
§7 のチェックリスト 1〜19 を adb（`input tap` / `screencap`）で一通り操作した。

**instrumented テスト**: `:app:connectedDebugAndroidTest` を両方で実行し、
**16 件すべて成功**（`app/build/outputs/androidTest-results/connected/debug/` の XML で
`failures="0" errors="0"` を確認）。外部キーの CASCADE / SET NULL も緑。

**Phase 2 / 3 で「runtime にしか出ない」と挙げていた 3 点はいずれも正常だった**:

- 型安全ルートの nullable 引数（`Calc(sheetId)`）と流し込み遷移 → シートの値が CALC に載り、
  「このシートに反映」でシートに書き戻る。**内部減速比は書き換わらない**（チェックリスト 17 の通り）
- `FileProvider` の authority（debug の `.debug` サフィックス）と共有インテント →
  シートのテキスト共有・CALC の画像共有ともに ACTION_SEND が開く。
  画像保存（SAF）も 145 KB の PNG が実際に書けて、開くと CALC 画面がそのまま入っている
- `DatePicker` の日付が一覧の並び順に効くか → 走行日を入れたシートが日付なしのシートより前に出る

**チェックリスト 1〜19 の結果**: 1〜19 すべて通った。ただし次を発見した。

| # | 見つかったもの | 扱い |
|---|---|---|
| 17 前後 | 消えたシートに「このシートに反映」でクラッシュ | **BUG-6（高）→ 同日に修正済み** |
| 3 / 4 | シャーシ上書きの範囲外入力が保存時まで弾かれない（落ちはしない） | **BUG-7（低）→ 同日に修正済み** |
| 15 | `DatePicker` の文言が英語（"Select date" / "S M T W T F S" / M-D-Y 並び） | 下記 |
| 5 | ライトテーマの見出し（DISPLAY / DATA）と数値のミント色が白背景で低コントラスト | 下記 |
| 2 / 19 | CALC の 2 つの FAB が内容に重なる（実機 720px で「ロールアウト」の値と「セッティング傾向」が隠れる） | 下記 |
| 13 | シート閲覧のツールバーはアイコン 5 個で、720px だとタイトルが 2 行に折れて潰れる | 下記 |
| 7 | v1 インポートで作られる車の名前がシャーシ id そのまま（`tamiya_tt01_tt01e`） | 仕様。`LegacyBackupConverter` の KDoc に理由あり |

**BUG にしていない 4 件（UI の粗さ。ROADMAP に載せるか判断する）**

1. **`DatePicker` が英語になる**。`res/values/` は日本語を既定にしているが、Material3 の
   `DatePicker` は **システムロケール** で文言と曜日並びを決めるので、端末が英語だと
   日本語アプリの中に英語のカレンダーが出る。日本語で固定したいなら
   `res/values-ja/` を切って既定を英語にするか、`DatePicker` に Locale を渡す。
2. **ライトテーマのコントラスト**。セクション見出しと数値がダークと同じミント色のまま。
   白背景では WCAG AA（4.5:1）に届かない。`Color.kt` にライト用のアクセントを足す。
3. **CALC の FAB が内容に重なる**。スクロール内容の下に FAB 2 個分の padding が無い。
   実機（720x1496）では「ロールアウト」の値と傾向バーが隠れる。
4. **シート閲覧のツールバー**。共有 / 比較 / 編集 / お気に入り / 削除 の 5 個が常時出ていて、
   720px ではタイトルの幅が 120px 程度しか残らない。overflow メニューに畳むのが妥当。

**再現に使った手順はこのファイルの BUG-6 / BUG-7 に書いてある。**
**どちらも同日（2026-09-28）に修正し、同じ手順を再演して直っていることを確認済み**（§2 参照）。
残る 4 件（UI の粗さ）は `ROADMAP.md` の Phase 3.5 に載せてある。

**instrumented テストは修正後に 24 件へ増え、Pixel_8 で全件成功**
（DAO 17 件 + `SetupSheetRepositoryImpl` 7 件）。実機 SO-53C で回したのは修正前の DAO 16 件まで。
