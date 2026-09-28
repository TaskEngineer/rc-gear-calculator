# AGENTS.md — RcGear（RC ギア比計算機 / Android）

このファイルは AI コーディングエージェント（Codex など）向けの作業指針です。
人間向けの概要は `README.md`、設計の経緯は `docs/PLAN.md`、
現状の課題と方針は `docs/HANDOFF.md`、機能ロードマップは `docs/ROADMAP.md` を参照。

## 1. プロジェクト概要（30 秒版）

- ラジコンのギア比 / 理論最高速を計算する **完全オフライン** の Android アプリ。
- Kotlin 2.0 / Jetpack Compose / Material 3 / Hilt / Room / DataStore / Navigation Compose。
- `:app`（Android）＋ `:core:domain`（純 Kotlin JVM）の 2 モジュール。
  `:app` 内は引き続きパッケージで疑似分割（`core` / `data` / `feature` / `navigation`）。
- MVP（Step 1〜12）と Phase 0 / 1 / 2（ドメイン再構築）/ 3（UI 構築）は完了。次は Phase 4。
  **タブは GARAGE / CALC / DB / CONFIG の 4 つ。** GARAGE は 車一覧 → 車詳細（シート一覧）
  → シート閲覧 → セクション編集 / ヘッダ編集 / 比較 と続く。
  **実機確認と instrumented テストは 2026-09-28 に実施済み**（`docs/HANDOFF.md` §7.1）。
  そこで出た BUG-6（クラッシュ）と BUG-7 は修正済み。残りは `docs/ROADMAP.md` の Phase 3.5。
- コード内コメント・UI 文言・ドキュメントは **日本語** で統一している。新規コードも日本語コメントで書く。

## 2. ビルド・テスト（必ずこの手順で）

```powershell
# Windows / PowerShell。JAVA は PATH に無いので JAVA_HOME を明示する
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain      # デバッグビルド
.\gradlew.bat test --console=plain                    # 全モジュールの単体テスト（現在 348 件）
.\gradlew.bat :core:domain:test --console=plain       # ドメインのみ（Android を経由しないので速い）
.\gradlew.bat :app:lintDebug --console=plain          # Android Lint
```

- 使用 JDK は Android Studio 同梱の JBR（Java 21）。
  **`gradle.properties` に `org.gradle.java.home` は書かない**（BUG-5 / S-2 で削除済み。開発機固有の絶対パスは
  他環境と CI で即失敗する）。上のように `JAVA_HOME` を渡すか、`~/.gradle/gradle.properties` に各自で書く。
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
2. `gradlew test` が全モジュール全件成功（`:app` と `:core:domain` の両方）
3. ドメイン層（計算・UseCase）を触ったら **必ず単体テストを追加または更新** する
4. Room の Entity を変えたら `version` を上げ、**`Migration` を書くか、移行手段をドキュメント化する**。
   `app/schemas/` に新しいスキーマ JSON が生成されていることを確認する。
   ※ セッティングシート化（Phase 2）は破壊的再作成 + v1 JSON の再インポートという
   手動移行を選んだため、この項に例外を設けている（`docs/HANDOFF.md` §5.2 参照）。
   ただし例外は **v1 → v2 の 1 回だけ**（`fallbackToDestructiveMigrationFrom(1)`）。
   v2 以降は Migration を書く。`fallbackToDestructiveMigration()`（無引数）に戻さないこと。
   公開に踏み切る場合はこの例外を撤回すること
5. **UI 文言は必ず `res/values/strings.xml` に置く**（S-11 で移行済み）。
   Composable は `stringResource()`、ViewModel は `core/ui/UiText`（リソース ID + 引数）を UiState に載せる。
   例外は `@Preview` のサンプルデータと、designsystem 部品内で解決する固定の a11y ラベルだけ

## 3. アーキテクチャの要点（as-built）

```
:app
feature/*   Composable Screen + ViewModel + UiState（画面ごと）
    ↓
:core:domain（純 Kotlin JVM。android を import した瞬間にビルドが落ちる）
domain/schema      FieldDef / SectionDef / TouringSetupSchema（項目定義の単一の真実。文言は持たない）
domain/model       Chassis, Car, SetupSheet, SetupValue(s), GearCalculationInput/Result, UserPreferences
domain/calculator  GearCalculator（純粋関数。Web 版の計算式を移植）
domain/validation  FieldValidator（範囲・型の検証。例外ではなく違反の一覧を返す）
domain/diff        SheetDiff（3 つの比較軸を 1 つの純粋関数に）
domain/repository  Repository の interface（実装は :app の data/repository/*Impl）
domain/backup      BackupCodec / LegacyBackupConverter（v1 → v2。形式は data 側の実装が知る）
domain/common      TimeProvider / IdGenerator / TransactionRunner
domain/usecase     ExportData / ImportData
    ↑
:app
data/       repository（Chassis / Car / SetupSheet / Preferences の *Impl）
            local/room（Entity・DAO・RcGearDatabase v2、RoomTransactionRunner）
            local/datastore（UserPreferencesDataSource）
            local/asset（ChassisJsonProvider: assets/chassis-db.json をキャッシュ）
            local/file（JsonFileDataSource: SAF の Uri へ読み書き）
navigation/ RcGearApp（Scaffold + NavigationBar）、RcGearNavHost、Routes
```

- **ChassisRepository** が中核。「同梱 JSON（読み取り専用）＋ Room の `chassis_overrides`（差分）」を Flow で合成する。
- **シートの値は EAV**（`setup_values` の `(sheetId, fieldKey)`）。項目の定義は
  `:core:domain` の `TouringSetupSchema`、文言は `:app` の `feature/sheet/SetupFieldLabels.kt`。
  **項目を足すのはこの 2 ファイルだけ**で、Composable は 1 行も書かない（それが崩れたら設計が壊れた合図）。
- UiState は immutable data class。ViewModel が `MutableStateFlow.update { copy(...) }` で更新する。
- 計算はスライダーの `onValueChange` ごとに同期実行（debounce なし）。DataStore 保存は操作確定時のみ。
- 層違反は S-5 / S-9 で解消済み。`:core:domain` は Android プラグインを適用していないため、
  **`android.*` や Compose を import すると即コンパイルエラーになる**。
  ドメインに何かを足すときは「Android 無しで意味が通るか」を毎回問うこと。

## 4. 変えてはいけないもの

- `assets/chassis-db.json` の各エントリの `id`（例 `tamiya_tt02`）。保存セッティング・上書き・エクスポート JSON の外部キーになっている。
  追加は可、改名・削除は不可。削除が必要なら「非表示フラグ」で対応する。
- エクスポート JSON の互換性。`ExportDataDto.CURRENT_SCHEMA_VERSION` を上げずにフィールドの意味を変えない。
  旧バージョンの読み込みは残す（`ImportDataUseCase` が `schemaVersion` で分岐）。
- `GearCalculationInput` の上下限定数（Web 版と同一）。変えるときはスライダー・インポート検証・テストを同時に更新する。
- `applicationId = io.github.taskengineer.rcgear`、debug ビルドの `.debug` サフィックス。
- HUD 調のカラーパレット（`core/designsystem/theme/Color.kt`）。Dynamic Color は採用しない方針。

## 5. コーディング規約

- Kotlin 公式スタイル（`kotlin.code.style=official`）。**ktlint 導入済み**（S-3。Gradle plugin 12.1.1、設定は `.editorconfig`）。
  `.\gradlew.bat ktlintFormat` で整形、`ktlintCheck` で検証。CI の最初のステップでもある。detekt は未導入。
  ktlint の設定を変えたときは `gradlew --stop` を挟んで確認する（ワーカーが `.editorconfig` の解決結果を抱える）。
- テストは Fake を優先する（`app/src/test/**/fake/`）。MockK は Fake を書くのが割に合わないときだけ。
  Fake は本物の制約（ユニーク制約・並び順）を再現すること。それが Fake を使う理由なので。
- **instrumented テストのメソッド名にスペースを入れない。** minSdk 26（DEX < 040）では
  SimpleName に空白を置けず `dexBuilder` が落ちる。JVM 単体テストはバッククォート内に置けるので、
  両者でルールが違う。
- Compose: 画面は `XxxScreen(viewModel = hiltViewModel())` の薄いラッパー ＋ 状態を受け取る `XxxContent`。
  プレビュー可能な stateless Composable を優先する。
  共有部品は `core/designsystem/component/` にあるものを使い、画面に `private` で作り直さない。
- ViewModel は `@HiltViewModel`。画面遷移引数は `navigation/Routes.kt` の復元関数（`savedStateHandle.calcRoute()` 等）から取る。
  `toRoute<T>()` は Bundle を要求し JVM 単体テストで落ちるので使わない（`docs/HANDOFF.md` §5.6）。
  画面を閉じる・戻るは UiState の Boolean ではなく `core/ui/ScreenEvent` で流す。
- 数値の表示整形は `core/ui/Format.kt` の拡張関数（`formatRatio()` 等）を使う。
  新しく `String.format` を書くときは必ず `Locale.US` を指定する（小数点が `,` になる地域がある）。
- 例外を握りつぶさない。ユーザーに見せるエラーは UiState の `message` / `errorMessage` に載せて Snackbar / ダイアログで出す。
- 1 タスク 1 コミット。メッセージは `feat: ...` / `fix: ...` / `refactor: ...` / `docs: ...` / `test: ...`（Conventional Commits）。
  これまでの履歴は `feat: Step N - ...` 形式。以降は Step 番号ではなく内容で書く。
- 署名鍵（`*.jks` / `keystore.properties`）と `local.properties` は絶対にコミットしない（`.gitignore` 済み）。

## 6. よくある作業の入り口

| やりたいこと | 触る場所 |
|---|---|
| 計算式・新メトリック追加 | `:core:domain` の `domain/calculator/GearCalculator.kt` → `domain/model/GearCalculationResult.kt` → `GearCalculatorTest` → `feature/calc/component/GearMetrics.kt`（表示するメトリックの一覧） |
| **シートの項目を追加** | `domain/schema/TouringSetupSchema.kt` → `res/values/strings.xml` → `feature/sheet/SetupFieldLabels.kt` の 3 箇所だけ（新しい単位が要るときだけ `FieldDef.kt` の `FieldUnit` にも 1 行）。**Composable は書かない** — 書く必要が出たら設計が壊れた合図。忘れると `SetupFieldLabelsTest` が落ちる |
| シートの画面を直す | 閲覧 `feature/sheet/SheetDetailScreen.kt`、値の編集 `SheetEditScreen.kt` + `component/FieldEditor.kt`（入力手段の分岐）、ヘッダ編集 `SheetHeaderEditScreen.kt`、比較 `SheetCompareScreen.kt` |
| 車・ガレージ周り | `feature/garage/`（一覧 `GarageScreen` / 車詳細 `CarDetailScreen` / 車の作成・編集 `CarEditScreen`） |
| UI 部品を足す / 直す | `core/designsystem/component/`（ドメイン非依存。**必ず `@Preview` を付ける**）。ドメインを知る部品は `core/ui/` |
| シャーシを追加 | `app/src/main/assets/chassis-db.json`（v2 のフラット配列。`id` は `メーカー_型番` のスネークケース、重複不可。`category` 必須、`drive` は裏が取れたものだけ）。`ChassisDbValidityTest` が既存 id を固定している |
| 設定項目を追加 | `domain/model/UserPreferences.kt` → `data/local/datastore/UserPreferencesDataSource.kt`（Keys）→ `PreferencesRepository` → `feature/config` |
| 新しい画面 | `navigation/Routes.kt`（`@Serializable` ルート + 復元関数）→ `RcGearNavHost.kt` → `feature/<name>/`。詳細画面は `core/ui/RcDetailScaffold` に載せる |
| シートのヘッダ項目を追加 | `SetupSheetEntity`（Room version++ と Migration）→ `SetupSheet` / `SessionConditions` → `SetupSheetRepositoryImpl` の変換 → `ExportedSheetDto`（schemaVersion 検討）。**値（設定した内容）はヘッダではなくレジストリに足す** |

## 7. 作業前に読むもの・やること

1. `docs/HANDOFF.md` の「既知の不具合」と「負債」を確認し、触る領域に該当があれば先に潰す
2. 大きめの変更は `docs/ROADMAP.md` の優先順位と整合させる。順位を変えるならドキュメント側も更新する
3. 設計判断を変えたら `docs/PLAN.md` ではなく `docs/HANDOFF.md`（as-built）に追記する。PLAN.md は当初計画の記録として凍結
