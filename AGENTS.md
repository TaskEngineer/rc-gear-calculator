# AGENTS.md — RcGear（RC ギア比計算機 / Android）

このファイルは AI コーディングエージェント（Codex など）向けの作業指針です。
人間向けの概要は `README.md`、設計の経緯は `docs/PLAN.md`、
現状の課題と方針は `docs/HANDOFF.md`、機能ロードマップは `docs/ROADMAP.md` を参照。

## 1. プロジェクト概要（30 秒版）

- ラジコンのギア比 / 理論最高速を計算する **完全オフライン** の Android アプリ。
- Kotlin 2.0 / Jetpack Compose / Material 3 / Hilt / Room / DataStore / Navigation Compose。
- 単一モジュール `:app`。パッケージで疑似マルチモジュール（`core` / `data` / `domain` / `feature` / `navigation`）。
- MVP（Step 1〜12）は実装済み。以降は `docs/ROADMAP.md` に沿って拡張する。
- コード内コメント・UI 文言・ドキュメントは **日本語** で統一している。新規コードも日本語コメントで書く。

## 2. ビルド・テスト（必ずこの手順で）

```powershell
# Windows / PowerShell。JAVA は PATH に無いので JAVA_HOME を明示する
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain      # デバッグビルド
.\gradlew.bat :app:testDebugUnitTest --console=plain  # 単体テスト（現在 19 件、GearCalculator のみ）
.\gradlew.bat :app:lintDebug --console=plain          # Android Lint
```

- 使用 JDK は Android Studio 同梱の JBR（Java 21）。`gradle.properties` の `org.gradle.java.home` も同じパスを指す。
- **このマシン固有の既知問題**: Gradle 起動時に
  `java.io.IOException: Unable to establish loopback connection` が出ることがある。
  原因は `%LOCALAPPDATA%\Temp` 配下での AF_UNIX ソケット作成失敗。次を設定してから再実行する。
  ```powershell
  $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$env:USERPROFILE\.jvmtmp"   # ディレクトリは作成済み
  ```
- Gradle 9.0 / AGP 8.7.3 / Kotlin 2.0.20 / KSP 2.0.20-1.0.25 の組み合わせで動作確認済み。
  **バージョンを個別に上げない**（AGP と Gradle、Kotlin と KSP は組で更新する）。
- ビルドは初回 3〜5 分、以降はデーモンが温まっていれば 30 秒〜1 分程度。

### 完了の定義（Definition of Done）

1. `:app:assembleDebug` が通る
2. `:app:testDebugUnitTest` が全件成功
3. ドメイン層（計算・UseCase）を触ったら **必ず単体テストを追加または更新** する
4. Room の Entity を変えたら `version` を上げ、`Migration` を書き、`app/schemas/` に新しいスキーマ JSON が生成されていることを確認する
5. UI 文言を追加したら、可能な限り `res/values/strings.xml` に置く（現状ハードコードが多いが、増やさない）

## 3. アーキテクチャの要点（as-built）

```
feature/*   Composable Screen + ViewModel + UiState（画面ごと）
    ↓
domain/     model（Chassis, SavedSetup, GearCalculationInput/Result, UserPreferences）
            usecase（SaveSetup / ExportData / ImportData）
core/domain GearCalculator（純粋関数。Web 版の計算式を移植）
    ↑
data/       repository（Chassis / Setup / Preferences / CalculationHistory）
            local/room（Entity・DAO・RcGearDatabase v1）
            local/datastore（UserPreferencesDataSource）
            local/asset（ChassisJsonProvider: assets/chassis-db.json をキャッシュ）
            local/file（JsonFileDataSource: SAF の Uri へ読み書き）
navigation/ RcGearApp（Scaffold + NavigationBar）、RcGearNavHost、Routes
```

- **ChassisRepository** が中核。「同梱 JSON（読み取り専用）＋ Room の `chassis_overrides`（差分）」を Flow で合成する。
- **CalcRequestBus**（`core/common`）は SETUPS → CALC の「流し込み」専用のシングルトン StateFlow。
- UiState は immutable data class。ViewModel が `MutableStateFlow.update { copy(...) }` で更新する。
- 計算はスライダーの `onValueChange` ごとに同期実行（debounce なし）。DataStore 保存は操作確定時のみ。
- 現状の層違反（`domain/usecase` が `data.repository` と `data.local.file.dto` を直接参照、
  `domain/model/UserPreferences` が `core.designsystem.theme.ThemeMode` を参照）は
  `docs/HANDOFF.md` の負債リストに載っている。**新規コードで層違反を増やさない**。

## 4. 変えてはいけないもの

- `assets/chassis-db.json` の各エントリの `id`（例 `tamiya_tt02`）。保存セッティング・上書き・エクスポート JSON の外部キーになっている。
  追加は可、改名・削除は不可。削除が必要なら「非表示フラグ」で対応する。
- エクスポート JSON の互換性。`ExportDataDto.CURRENT_SCHEMA_VERSION` を上げずにフィールドの意味を変えない。
  旧バージョンの読み込みは残す（`ImportDataUseCase` が `schemaVersion` で分岐）。
- `GearCalculationInput` の上下限定数（Web 版と同一）。変えるときはスライダー・インポート検証・テストを同時に更新する。
- `applicationId = io.github.taskengineer.rcgear`、debug ビルドの `.debug` サフィックス。
- HUD 調のカラーパレット（`core/designsystem/theme/Color.kt`）。Dynamic Color は採用しない方針。

## 5. コーディング規約

- Kotlin 公式スタイル（`kotlin.code.style=official`）。ktlint / detekt は未導入（導入は ROADMAP に記載）。
- Compose: 画面は `XxxScreen(viewModel = hiltViewModel())` の薄いラッパー ＋ 状態を受け取る `XxxContent`。
  プレビュー可能な stateless Composable を優先する。
- ViewModel は `@HiltViewModel`。画面遷移引数は `SavedStateHandle` から取る（`checkNotNull`）。
- 数値の表示整形は `String.format(Locale.US, ...)` を使う（Locale 未指定は不可。小数点が `,` になる地域がある）。
- 例外を握りつぶさない。ユーザーに見せるエラーは UiState の `message` / `errorMessage` に載せて Snackbar / ダイアログで出す。
- 1 タスク 1 コミット。メッセージは `feat: ...` / `fix: ...` / `refactor: ...` / `docs: ...` / `test: ...`（Conventional Commits）。
  これまでの履歴は `feat: Step N - ...` 形式。以降は Step 番号ではなく内容で書く。
- 署名鍵（`*.jks` / `keystore.properties`）と `local.properties` は絶対にコミットしない（`.gitignore` 済み）。

## 6. よくある作業の入り口

| やりたいこと | 触る場所 |
|---|---|
| 計算式・新メトリック追加 | `core/domain/GearCalculator.kt` → `domain/model/GearCalculationResult.kt` → `GearCalculatorTest` → `feature/calc/component/MetricsGrid.kt` |
| シャーシを追加 | `app/src/main/assets/chassis-db.json`（`id` は `メーカー_型番` のスネークケース、重複不可） |
| 設定項目を追加 | `domain/model/UserPreferences.kt` → `data/local/datastore/UserPreferencesDataSource.kt`（Keys）→ `PreferencesRepository` → `feature/config` |
| 新しい画面 | `navigation/Routes.kt` → `RcGearNavHost.kt` → `feature/<name>/` |
| 保存データの項目追加 | `SavedSetupEntity`（Room version++ と Migration）→ `SavedSetup` → `SetupRepository` の変換 → `ExportedSetupDto`（schemaVersion 検討） |

## 7. 作業前に読むもの・やること

1. `docs/HANDOFF.md` の「既知の不具合」と「負債」を確認し、触る領域に該当があれば先に潰す
2. 大きめの変更は `docs/ROADMAP.md` の優先順位と整合させる。順位を変えるならドキュメント側も更新する
3. 設計判断を変えたら `docs/PLAN.md` ではなく `docs/HANDOFF.md`（as-built）に追記する。PLAN.md は当初計画の記録として凍結
