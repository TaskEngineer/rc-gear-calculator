# 引き継ぎ書（HANDOFF）— RcGear Android

> **Status**: MVP 完了、Phase 2 未着手。管理主体を Claude Code から Codex へ移行する時点の as-built 記録。
> **Last Updated**: 2026-09-02
> **対象読者**: 次にこのリポジトリを扱う AI エージェント（Codex）と、その指示を出す本人。
>
> 運用ルールは `AGENTS.md`、機能ロードマップは `ROADMAP.md`、当初計画は `PLAN.md`（凍結）。

---

## 1. 現状サマリ

| 項目 | 状態 |
|---|---|
| 実装範囲 | PLAN Step 1〜12 完了（CALC / SETUPS / DB / CONFIG、画像エクスポート、アイコン、R8） |
| 最終コミット | `a93b912 feat: Step 12 - Polish` |
| ビルド | `:app:assembleDebug` 成功（2026-09-02 確認） |
| 単体テスト | `GearCalculatorTest` 19 件成功（2026-09-02 確認）。それ以外のテストは存在しない |
| Instrumented / UI テスト | なし（`androidTest` ディレクトリ自体が無い） |
| Lint / 静的解析 | ktlint / detekt 未導入。Android Lint も CI で回していない |
| CI | なし（`.github/` 無し） |
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
| JDK | 21（Android Studio 同梱 JBR、`gradle.properties` で絶対パス指定） |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Compose BOM | 2025.05.00 |
| Hilt / Room / DataStore | 2.52 / 2.6.1 / 1.1.1 |

Gradle 9 上で「Gradle 10 で非互換になる非推奨機能」の警告が出ている（原因は AGP 8.7 側の可能性が高い）。
AGP を上げると解消する見込みだが、Gradle と AGP の互換表に従って組で更新すること。

---

## 2. 既知の不具合（優先して直す）

コードを読んで確認したもの。再現手順付き。**いずれも未修正**。

### BUG-1: タイヤ径の上書きが範囲外だと CALC がクラッシュする（重要度: 高）

- 再現: DB 画面 → 任意シャーシ → タイヤ径に `200` を入力して保存 → CALC でそのシャーシを選択。
- 原因: `ChassisEditViewModel.onSave` は「正の整数」しか検証しない。
  `CalcViewModel.onChassisSelected` が `tireMm = chassis.defaultTireMm` をそのままセットし、
  `GearCalculationInput` の `init` にある `require(tireMm <= 120)` が `IllegalArgumentException` を投げる。
- 修正方針: 編集画面で `GearCalculationInput.MIN_TIRE_MM..MAX_TIRE_MM` に制限する。
  さらに防御として `CalcViewModel` 側でも `coerceIn` する。

### BUG-2: インポート JSON の値が範囲外だと後でクラッシュする（重要度: 高）

- 再現: エクスポート JSON の `pinion` を `5` に書き換えてインポート → SETUPS でその項目を開く。
- 原因: `ImportDataUseCase` は JSON の構文と `schemaVersion` しか検証せず、
  `SavedSetup` を無検証で Room に入れる。`SetupDetailViewModel` が `GearCalculationInput` を組み立てた時点で例外。
  同様に `CalcRequestBus` 経由で CALC に流し込んでも落ちる。
- 修正方針: `ImportDataUseCase` で 1 件ずつ範囲検証し、不正な行は `skippedInvalid` としてカウントして返す。
  既存の `Result.Success` にフィールドを足す。

### BUG-3: インポートがトランザクションではない（重要度: 中）

- `ImportDataUseCase` はセッティングと上書きを 1 件ずつ `insert` する。途中で失敗すると半端に取り込まれる。
- 修正方針: `RcGearDatabase.withTransaction { }` で囲む。Repository に `restoreAll(List)` を追加して DAO の `@Transaction` を使う。

### BUG-4: `String.format` の Locale 未指定が 3 箇所（重要度: 低）

- `feature/calc/component/ChassisSelectorCard.kt:75`、`feature/db/ChassisEditScreen.kt:106`、`feature/setups/SetupDetailScreen.kt:214`
- 小数点が `,` になる地域で表示が崩れる。`Locale.US` を付けるか、共通の整形関数に寄せる（REF-6 参照）。

### BUG-5: `gradle.properties` に開発機の絶対パスがコミットされている（重要度: 中）

- `org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr`。他の環境・CI では即失敗する。
- 修正方針: この行を削除し、`JAVA_HOME` または Gradle Toolchain（`kotlin { jvmToolchain(21) }` は既にある）に任せる。
  ローカルで必要なら `~/.gradle/gradle.properties` に移す。

---

## 3. 技術的負債（設計上のズレ）

PLAN.md が掲げた「Domain は Pure Kotlin、依存方向は UI → Domain ← Data」が as-built では守られていない。
今すぐ壊れるわけではないが、テスト追加とマルチモジュール化の前提になるので早めに直す。

| ID | 内容 | 場所 |
|---|---|---|
| DEBT-1 | `domain/usecase` が `data.repository.*`（具象クラス）と `data.local.file.dto.ExportDataDto` を直接 import。Domain → Data の逆依存 | `ExportDataUseCase` / `ImportDataUseCase` / `SaveSetupUseCase` |
| DEBT-2 | `domain/model/UserPreferences` が `core.designsystem.theme.ThemeMode`（UI 層の enum）を参照 | `UserPreferences.kt`、`Theme.kt` |
| DEBT-3 | 計算ロジックが `core/domain/GearCalculator` にあり、`domain/` と二重パッケージ。テストも `core/domain` 配下 | `core/domain/` |
| DEBT-4 | UI 文言・エラー文言が Composable と ViewModel にハードコード（約 130 箇所）。`strings.xml` は `app_name` のみ。ViewModel が日本語文字列を組み立てている（`CalcViewModel.savedMessage`、`ConfigViewModel.message` など） | `feature/**` |
| DEBT-5 | Repository が具象クラスで interface が無い。ViewModel / UseCase のテストで MockK に頼ることになる（MockK は依存に入っている） | `data/repository/*` |
| DEBT-6 | `CalcRequestBus` はアプリスコープの可変グローバル状態。「流し込み」以外の用途が増えると追跡困難 | `core/common/CalcRequestBus.kt` |
| DEBT-7 | ルートが文字列（`"setups/$setupId"`）。Navigation 2.8 の型安全ルート（`@Serializable` data class）に移行可能 | `navigation/Routes.kt`、`RcGearNavHost.kt` |
| DEBT-8 | `calculation_history` テーブルは Insert のみで読み出し経路が無い。UI（ROADMAP F-1）を作るか、テーブルごと削除するか決める | `CalculationHistory*` |
| DEBT-9 | `PreferencesRepository` は `UserPreferencesDataSource` の透過的ラッパー。層を揃える以外の価値が無い | `data/repository/PreferencesRepository.kt` |
| DEBT-10 | `CalcViewModel.init` に 3 本の `collect` が並び、`recalculate` は例外を投げうる（BUG-1/2 の受け口）。状態遷移がテストしづらい | `feature/calc/CalcViewModel.kt` |
| DEBT-11 | `ExportDataUseCase` / `ImportDataUseCase` が `System.currentTimeMillis()` を直接呼ぶ。Repository も同様。時刻をテストで固定できない | `data/repository/*`、`domain/usecase/*` |
| DEBT-12 | `libs.versions.toml` にコメント「既存の行はそのまま、以下を追加」が残っている（作業メモの残骸） | `gradle/libs.versions.toml` |
| DEBT-13 | `res/font/` が空。Google Fonts（`ui-text-google-fonts`）経由で Roboto Mono を取得している可能性があり、**完全オフライン** の方針と矛盾しうる。ネットワーク無し・初回起動の端末で等幅フォントが出るか確認する | `core/designsystem/theme/Type.kt` |

---

## 4. リファクタリング方針（推奨順）

各項目は独立してコミットできる粒度にしてある。「検証」の欄が Definition of Done。

### REF-1: 入力値検証の一元化（BUG-1 / BUG-2 / BUG-3 を同時に解消）

- `domain/model/GearCalculationInput` の companion に、範囲チェック関数（`isValidTireMm` など）と
  クランプ関数（`clampTireMm` など）を追加する。
- `ChassisEditViewModel`、`ImportDataUseCase`、`CalcViewModel.onChassisSelected` / bus 受信の 3 箇所から使う。
- インポートは `withTransaction`。
- 検証: `GearCalculationInputTest`（境界値）、`ImportDataUseCaseTest`（不正行スキップ・部分失敗時ロールバック）を追加。

### REF-2: Domain 層の純化（DEBT-1 / 2 / 3）

1. `domain/repository/` に interface を切る（`ChassisRepository`、`SetupRepository`、`PreferencesRepository`、`CalculationHistoryRepository`）。
   `data/repository/` の具象クラスは `XxxRepositoryImpl` に改名し、Hilt の `@Binds` で束ねる（`data/di/RepositoryModule.kt` を新設）。
2. `ThemeMode` を `domain/model/` に移し、`core/designsystem/theme/Theme.kt` は import するだけにする。
3. `core/domain/GearCalculator` を `domain/calculator/`（または `domain/usecase/CalculateGearUseCase`）へ移動。テストも追従。
4. エクスポート DTO（`ExportDataDto`）は Data 層に残し、UseCase は「ドメインモデルのリスト」を返す形にして、JSON 化は Data 層（`ExportFileRepository` など）に寄せる。
- 検証: `domain/` 配下に `android.*` / `data.*` / `core.designsystem.*` の import が **ゼロ** であること（grep で機械的に確認できる）。
  将来 `:domain` を Pure Kotlin モジュールに切り出せる状態が完成条件。

### REF-3: テスト基盤の整備（DEBT-5 / 10 / 11）

- `Clock` 相当の `TimeProvider` interface を導入し、Repository / UseCase に注入する。
- Repository に interface があるので、テスト用の Fake（in-memory 実装）を `app/src/test/.../fake/` に置く。MockK より Fake を優先する。
- `CalcViewModel` を `kotlinx-coroutines-test` の `runTest` + `UnconfinedTestDispatcher` で検証する。
  最低限: 起動時の前回値復元、シャーシ選択でタイヤ径が変わる、bus 受信で値が反映される、保存の重複名エラー。
- Room の in-memory DB を使う DAO テストは Robolectric が要るため、優先度は下げる。
  代わりに `app/schemas/` を使った `MigrationTestHelper` の準備だけしておく（Room v2 が出た時点で必須）。
- 検証: `testDebugUnitTest` で UseCase 3 本 + ViewModel 1 本以上がカバーされる。

### REF-4: UI 文言のリソース化（DEBT-4、ROADMAP F-4 英語化の前提）

- 手順: (1) `strings.xml` に日本語を全て移す → (2) Composable は `stringResource()` → (3) ViewModel は文字列ではなく
  `sealed interface UiMessage { data class SetupSaved(val name: String) ... }` を UiState に載せ、Composable 側で文字列に解決する。
- `DbFilter.label`、`TopLevelDestination.title` のような enum に日本語を持たせている箇所は `@StringRes` に変える。
- 機械的に洗い出すコマンド:
  ```
  grep -rn '"[^"]*[ぁ-んァ-ン一-龥][^"]*"' app/src/main/java --include=*.kt
  ```
- 検証: 上記 grep のヒットが KDoc / コメント以外でゼロ。

### REF-5: ナビゲーションの型安全化（DEBT-7）

- Navigation Compose 2.8 の `@Serializable` ルート（`data object Calc`、`data class SetupDetail(val setupId: Long)`）に置き換える。
- `RcGearApp` の「派生画面で親タブを選択状態にする」判定は `hasRoute<>()` に変える。
- 検証: 4 タブ遷移、詳細 → 戻る、SETUPS → CALC 流し込みが手動で動作。

### REF-6: 表示整形の共通化（BUG-4）

- `core/ui/Format.kt` に `Double.formatRatio()`（小数 2 桁）、`formatSpeed()`（小数 1 桁）、`Int.formatRpm()` を置く。
  20 箇所の `String.format` を置き換える。Locale は `Locale.US` 固定（ドメインの慣習として小数点は `.`）。
- 検証: `FormatTest` を追加。

### REF-7: 静的解析と CI（負債の再発防止）

- `ktlint`（Gradle plugin `org.jlleitschuh.gradle.ktlint`）または `detekt` を導入。既存コードの違反は一括整形して 1 コミットで済ませる。
- GitHub Actions: `ubuntu-latest` + `actions/setup-java@v4`（temurin 21）+ `gradle/actions/setup-gradle@v4` で
  `assembleDebug` / `testDebugUnitTest` / `lintDebug`。BUG-5 の修正が前提。
- 検証: PR で CI が緑になる。

### やらないこと（当面）

- マルチモジュール化: 単一モジュールで 6,000 行程度。REF-2 が済めばいつでも切り出せるので、ビルド時間が問題になるまで見送る。
- Dynamic Color: HUD 調テーマは意図的な設計。
- `CalcRequestBus` の廃止: 用途が 1 つのうちは害がない。SavedStateHandle 経由に変える案もあるが、
  タブ間の `restoreState` と相性が悪いので、用途が増えるまで現状維持。

---

## 5. Codex での運用メモ

- ルートの `AGENTS.md` を Codex が自動で読む。作業指示はそこに集約し、このファイルは「状態の記録」に使う。
- Codex の設定（`~/.codex/config.toml`）に本プロジェクトはまだ `trusted` 登録されていない。
  初回起動時にトラストを求められたら承認する。Windows の sandbox は `elevated` 設定になっている。
- Gradle デーモンが sandbox 内で起動できるかを最初に確認する。失敗した場合は `AGENTS.md` §2 の loopback 回避策を試す。
- Claude Code 側の設定（`.claude/settings.local.json`）は残しておいて害はない。不要なら削除してよい。
- 作業単位は「1 タスク 1 コミット」。REF-* / F-* の ID をコミットメッセージや PR タイトルに入れると追跡しやすい。

## 6. 動作確認チェックリスト（手動、リリース前）

1. 初回起動: スプラッシュ → CALC、シャーシ未選択でも落ちない
2. シャーシ選択 → タイヤ径が自動で変わる → スライダー操作 → HUD が即時更新
3. 保存 → SETUPS に出る → 詳細で値が一致 → 「CALC に流し込む」で反映
4. DB で内部減速比を上書き → SETUPS 詳細で「保存時 / 現在」の差分が出る → リセットで消える
5. CONFIG: テーマ 3 種、mph 併記 OFF、基準 FDR 変更で傾向バーの中心が動く
6. エクスポート → 全データ削除 → インポート → 復元される（同名スキップ件数が 0）
7. 画面回転・バックグラウンド復帰で入力値が残る
8. 端末を機内モードにして 1〜7 が動く（オフライン要件）
