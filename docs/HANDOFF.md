# 引き継ぎ書（HANDOFF）— RcGear Android

> **Status**: MVP 完了 + **セッティングシート化 Phase 0（地固め）/ Phase 1（共有 UI・文言・ナビの土台）完了**。Phase 2 未着手。
> **Last Updated**: 2026-09-26
> **対象読者**: 次にこのリポジトリを扱う AI エージェントと、その指示を出す本人。
>
> 運用ルールは `AGENTS.md`、機能ロードマップは `ROADMAP.md`、当初計画は `PLAN.md`（凍結）。
>
> **このアプリは「ギア比計算機」から「ギア比も計算できるセッティングシート管理アプリ」へ
> 拡張する途中にある。** ダンパーオイル・スプリング・キャンバー等、実物のセッティングシートに
> 書く内容を扱えるようにするのが目標。その設計判断（別アプリに分けない／手動移行／
> フィールド定義は Kotlin レジストリ）は §7 に記録してある。

---

## 1. 現状サマリ

| 項目 | 状態 |
|---|---|
| 実装範囲 | PLAN Step 1〜12 完了（CALC / SETUPS / DB / CONFIG、画像エクスポート、アイコン、R8）+ Phase 1 の土台 |
| 共有 UI | `core/designsystem/component/` に 13 部品（全てに `@Preview`）。文言は `strings.xml`（約 110 件） |
| モジュール | `:app`（Android）＋ `:core:domain`（純 Kotlin JVM） |
| ビルド | `:app:assembleDebug` 成功（2026-09-26 確認） |
| 単体テスト | **95 件成功**（`:core:domain` 30 / `:app` 65）。GearCalculator・入力値検証・表示整形・UseCase 3 本・CalcViewModel・SetupDetailViewModel・JsonBackupCodec |
| Instrumented / UI テスト | DAO テスト **6 件成功**（`app/src/androidTest/`）。2026-09-26 に AVD `Pixel_8`（API 34）で `connectedDebugAndroidTest` 実行、failures 0 / errors 0。UI テストは未着手 |
| Lint / 静的解析 | **ktlint 導入済み**（`ktlintCheck` 緑）。Android Lint も CI で実行 |
| CI | 設定済み（`.github/workflows/ci.yml`: ktlint → domain test → test → assemble → lint）。**2026-09-26 の PR #1 で初回実行、全ステップ緑**（5m14s）。instrumented テストは CI に入っていない（エミュレータが要るため。§6 参照） |
| リリース署名 | 未設定。`versionCode = 1`、`versionName = 0.1.0` |
| スクリーンショット | README に TODO のまま（`docs/screenshots/` 未作成） |
| ライセンス | TBD |
| リモート | `https://github.com/TaskEngineer/rc-gear-calculator.git` |
| データ | シャーシ 45 エントリ / 9 メーカー、`id` 重複なし |

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

**[部分解消: S-1' / 45cc235]** セッティング・上書きそれぞれを一括 insert（Room の @Insert(List) は 1 トランザクション）にした。**ファイル全体の原子性はまだ無い**（両者が別トランザクション）。計画 M-8 で解消する。

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
| DEBT-8 | `calculation_history` テーブルは Insert のみで読み出し経路が無い。UI（ROADMAP F-1）を作るか、テーブルごと削除するか決める | **削除で決着予定**（Phase 2 の M-3）。セッティングシート自体がこれより良い履歴になるため |
| DEBT-9 | `PreferencesRepository` は `UserPreferencesDataSource` の透過的ラッパー。層を揃える以外の価値が無い | **そのまま**。interface 化で層は揃った。実害が無いので放置 |
| DEBT-10 | `CalcViewModel.init` に 3 本の `collect` が並び、`recalculate` は例外を投げうる（BUG-1/2 の受け口）。状態遷移がテストしづらい | **部分解消**。`recalculate` が例外を投げなくなり（S-1'）、CalcViewModel のテストも入った（S-6）。`update {}` 内の再計算は **解消（U-4 / 28c437b）** `setState()` に集約。`first { !it.isLoading }` は U-3 でバスごと消えた |
| DEBT-11 | `ExportDataUseCase` / `ImportDataUseCase` が `System.currentTimeMillis()` を直接呼ぶ。Repository も同様。時刻をテストで固定できない | **解消（S-6）** `TimeProvider` を注入。`IdGenerator` は採番する行がまだ無いので Phase 2 で追加する |
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

- 結果 XML は `app/build/outputs/androidTest-results/connected/debug/`、HTML は `app/build/reports/androidTests/connected/`。
- **Gradle のコンソール出力は「Finished 6 tests」しか言わず、失敗件数を出さない。**
  緑かどうかは終了コードか XML の `failures` / `errors` 属性で確認する。
- AVD が無い場合は Android Studio の Device Manager で作る（API 34 / `Pixel_8` で確認済み）。
  minSdk 26 なので古い API でも動くはずだが未検証。

## 7. 動作確認チェックリスト（手動、リリース前）

1. 初回起動: スプラッシュ → CALC、シャーシ未選択でも落ちない
2. シャーシ選択 → タイヤ径が自動で変わる → スライダー操作 → HUD が即時更新
3. 保存 → SETUPS に出る → 詳細で値が一致 → 「CALC に流し込む」で反映
4. DB で内部減速比を上書き → SETUPS 詳細で「保存時 / 現在」の差分が出る → リセットで消える
5. CONFIG: テーマ 3 種、mph 併記 OFF、基準 FDR 変更で傾向バーの中心が動く
6. エクスポート → 全データ削除 → インポート → 復元される（同名スキップ件数が 0）
7. 画面回転・バックグラウンド復帰で入力値が残る
8. 端末を機内モードにして 1〜7 が動く（オフライン要件）
