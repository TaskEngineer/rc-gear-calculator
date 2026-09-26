# ロードマップ（ROADMAP）— RcGear Android

> **Last Updated**: 2026-09-26
> 負債・不具合の詳細は `HANDOFF.md`、作業ルールは `AGENTS.md`、当初計画は `PLAN.md`（凍結）。

## スコープの変更（2026-09-26）

**このアプリは「ギア比計算機」から「ギア比も計算できるセッティングシート管理アプリ」になる。**

旧 ROADMAP はセッティングシートを Phase 4 の 1 行（「走行メモ（路面、気温、結果）を
セッティングに紐付け」）としか想定していなかった。実際にやりたいのは
ダンパーオイル・ピストン・スプリング・キャンバー・トー・キャスター・車高・ドループ・
スタビ・デフ・タイヤ・バラスト・ESC まで含む**セッティングシート一式の管理**で、
これは「車（Car）」と「シート（SetupSheet）」という新しい中心概念を要求する。
ギア比はその 1 セクションに格下げされる。

対象は**ツーリング（オンロード）専用**。バギー / ドリフト / F1 は対象外。
別アプリに分けない判断と、その理由は `HANDOFF.md` §5.1。

優先順位の基準:
1. **クラッシュを消す** ＞ 2. **将来の変更を安くする** ＞ 3. **自分が毎週使う機能** ＞ 4. **公開に必要なもの** ＞ 5. あれば嬉しいもの

旧 ID（S-* / REF-* / F-* / BUG-* / DEBT-*）は追跡できるよう可能な限り流用している。

---

## Phase 0 — 地固め【完了: 2026-09-26】

40〜80 項目のフォームを載せる前に、土台側の問題を潰す工程。

| ID | 内容 | 状態 |
|---|---|---|
| S-2 | BUG-5: `gradle.properties` の開発機固有 JDK パス除去 | 完了 `7a3516b` |
| S-1' | BUG-1/2/3: 入力値検証を `GearCalculationInput` に一元化 | 完了 `45cc235` |
| S-10 | `fallbackToDestructiveMigration()` 追加、`room-testing` と instrumented テストの足場 | 完了 `d7b01bb` |
| S-8 | DEBT-13: Roboto Mono を同梱しダウンローダブルフォント依存を削除 | 完了 `862b155` |
| S-7 | REF-6: 表示整形を `core/ui/Format.kt` に集約 | 完了 `b624b8c` |
| S-5 | REF-2: Domain 純化（Repository interface / ThemeMode 移動 / GearCalculator 移動 / BackupCodec） | 完了 |
| S-6 | REF-3: テスト基盤（TimeProvider、Fake 4 種、UseCase 3 本 + CalcViewModel） | 完了 |
| S-9 | `:core:domain` を純 Kotlin JVM モジュールとして切り出し | 完了 `84155d9` |
| S-4 | REF-7 後半: GitHub Actions（ktlint / test / assemble / lint） | 完了 `a5de750` |
| S-3 | REF-7 前半: ktlint 導入と一括整形 | 完了 |

到達点: 単体テスト 19 → **79 件**、CI あり、`:core:domain` は Android を import できない。

---

## Phase 1 — 共有 UI / 文言 / ナビの土台

40 項目のフォームを書く前に必要な前提条件。ここを飛ばすと同じ部品を 10 回書くことになる。

| ID | 内容 | 規模 | 依存 |
|---|---|---|---|
| U-1 | `core/designsystem/component/` 新設。`RcCard` / `SectionHeader` / `LabeledRow` / `RcSlider`（`GearSlider` を移設）/ `RcStepper` / `RcNumberField` / `RcSelectField` / `MetricsGrid` / `ValueDiffRow`。**全部に `@Preview`** | 大 | なし |
| U-2 | `RcDetailScaffold` + `ScreenEvent` で、3 画面にコピペされている `Scaffold+TopAppBar+戻る` と `isDone/notFound+LaunchedEffect` を 1 本化 | 中 | U-1 |
| S-11 | REF-4: 文言リソース化。Composable は `stringResource()`、ViewModel は `sealed interface UiMessage` | 中 | なし |
| S-12 | REF-5: 型安全ルート（`@Serializable` data class） | 中 | なし |
| U-3 | DEBT-6: `CalcRequestBus` 廃止 → nav 引数 + `SavedStateHandle` | 小 | S-12 |
| U-4 | DEBT-10: `_uiState.update{}` 内の例外・副作用を除去、`first { !it.isLoading }` の永久サスペンド解消 | 小 | なし |

現状の UI 層の問題: **共有コンポーネント層が存在しない**。`DetailRow` / `ConfigRow` /
`SwitchRow` / `SectionHeader` / `MetricCell` / `SetupCard` が全て `private`、
再利用可能な入力部品は `GearSlider` ただ 1 つ、`@Preview` はゼロ。

---

## Phase 2 — ドメイン再構築（本丸）

| ID | 内容 | 規模 |
|---|---|---|
| M-1 | `:core:domain` に `FieldDef` / `SectionDef` / `TouringSetupSchema`（ギア + 前後ダンパー + 主要サス の第 1 スライスのみ） | 中 |
| M-2 | `SetupValue` / `SetupValues` / `toGearInput()`。`GearCalculationInput.init` の `require` を `FieldValidator` に移管 | 中 |
| M-3 | Room v2: `cars` / `setup_sheets` / `setup_values` / `user_chassis` 追加、`saved_setups` と `calculation_history` 削除 | 大 |
| M-4 | `CarRepository` / `SetupSheetRepository`（domain に interface、data に Impl）。`IdGenerator` 注入 | 中 |
| M-5 | `SheetDiff.compare()` — 3 つの比較軸を 1 つの純粋関数に落とす | 小 |
| M-6 | エクスポート JSON v2 + **v1 → v2 インポータ**。v1 の読み込みは永久に残す | 中 |
| M-8 | インポートをファイル全体で 1 トランザクションにする（BUG-3 の積み残し） | 小 |
| M-7 | `chassis-db.json` v2: `maker` / `category` / `drive` / `hasCenterDiff` を追加。**既存 45 個の `id` は不変**（テストで固定） | 中 |

移行は**手動**（v1 JSON をエクスポート → 破壊的再作成 → v2 インポータで復元）。
Room の `Migration` は書かない。理由は `HANDOFF.md` §5.2。

`calculation_history`（DEBT-8）は M-3 で**テーブルごと削除**して決着させる。
Insert のみ・読み出し経路ゼロで中身が薄く、セッティングシート自体が遥かに良い履歴になるため。
旧 F-6（計算履歴 UI）はこれに伴い**取り下げ**。

### スナップショット凍結の一般化

現行の `internalRatioSnapshot` + `SnapshotDiffCard`（「保存時 2.60 / 現在 2.70」）は、
**「値が絶対値で保存されている」性質の特殊ケース**だった。新モデルでは全フィールドが
構造的にスナップショットになり、専用カラムは消える。代わりに 3 つの比較軸が
同じ `ValueDiffRow` で描かれる:

| 軸 | 意味 |
|---|---|
| A | シート値 vs シャーシ標準 —「キット標準から変えた所」。現行 `SnapshotDiffCard` の後継 |
| B | シート値 vs ベースラインシート —「**前回のセットから何を変えたか**」。セッティングシートアプリの核心 |
| C | シート値 vs 任意シート — 旧 F-3「セッティング比較」 |

---

## Phase 3 — UI 構築

タブ構成は `[GARAGE] [CALC] [DB] [CONFIG]`。GARAGE が SETUPS を置換する。
**CALC はトップレベルに残す**（「車の文脈なしで 10 秒で終わる問い」に答える画面なので、
シートのセクションに格下げすると一番速い操作が一番遅くなる）。

| ID | 内容 | 旧 ID |
|---|---|---|
| G-1 | GARAGE タブ: 車一覧 + 車の CRUD | — |
| G-2 | 車詳細 = シート一覧 +「新規シート」/「ベースラインから複製」 | — |
| G-3 | シート閲覧画面: 実物のシート用紙に近い F/C/R グリッド表示 | — |
| G-4 | シート編集画面: セクション単位編集、`FieldEditor` ディスパッチ、インラインバリデーション | — |
| G-5 | ギアセクション ↔ CALC 双方向連携 | — |
| G-6 | 差分 UI 3 軸（A / B / C） | F-3 |
| G-7 | **フィールドの横展開**（駆動系・タイヤ・ESC・車体・走行結果）。UI コードは増えない | — |
| F-1 | ロールアウト表示（タイヤ周長 ÷ FDR） | F-1 |
| F-4 | 共有（`FileProvider` + `ACTION_SEND`）。テキスト版シート + 既存 PNG | F-4 |
| F-5 | ユーザー定義シャーシ（`user_chassis` は M-3 で作成済み） | F-5 |

G-7 が「レジストリ方式が機能している」ことの証明になる。
**項目追加が Composable を 1 行も書かずに反映されること**が受け入れ条件。

---

## Phase 4 — その後

セッティングシートが動いてから再評価する。

| ID | 機能 | メモ |
|---|---|---|
| F-9 | セッティングの編集・上書き保存 | シート編集（G-4）に吸収される見込み |
| F-2 | 目標速度からの逆算 | 持っているピニオン歯数を CONFIG に登録すると実用的 |
| F-10 | モーター入力の拡張（ターン数 → KV 換算） | `motorTurn` は `TouringSetupSchema` に入る予定なので、換算テーブルだけの話になる |
| F-7 | 英語化（`values-en/`） | S-11 が前提。フィールドラベルも `@StringRes` なので通常手順で済む |
| F-8 | アプリショートカット | 長押しで直近シートを開く |
| — | `androidTest`: DAO テスト（CASCADE 削除）、Compose UI スモーク 1 本 | |
| — | `:core:designsystem` のモジュール分離 | Preview の反復が遅いと感じたら |

**取り下げ**: F-6（計算履歴 UI）— テーブルごと削除する（§Phase 2）。

---

## Phase 5 — 公開準備（公開する気になったら）

| ID | 内容 | メモ |
|---|---|---|
| P-1 | ライセンス決定（MIT 推奨）と `LICENSE` 追加。`chassis-db.json` の出典表記を CONFIG > ABOUT に出す | `ChassisDatabaseDto.sources` は既にパースしている |
| P-2 | OSS ライセンス表示画面 | Roboto Mono の OFL は `assets/licenses/` に同梱済み |
| P-3 | 署名設定（`keystore.properties` は `.gitignore` 済み） | 鍵はコミットしない |
| P-4 | スクリーンショットを `docs/screenshots/` に配置し README を更新 | |
| P-5 | Play Console: プライバシーポリシー（データ収集なし）、コンテンツレーティング | 完全オフラインなので記述は短い |
| P-6 | バージョニング: `versionCode` を CI で自動採番、`versionName` は SemVer | |
| P-7 | **Room の `Migration` を書く体制に戻す** | 公開すると手動移行の例外（`HANDOFF.md` §5.2）は撤回が必要 |

---

## やらないこと

- **`:feature:*` の分割**: 「クリーンビルド 2 分超え」を唯一のトリガーにする。
  単独開発でこの規模だと、feature 間参照を断つ抽象レイヤのコストが並列化の利益を上回る。
- **Dynamic Color**: HUD 調テーマは意図的な設計。
- **クラウド同期**: 完全オフラインが設計方針。ただし UUID 主キーと `updatedAt` は
  後から入れ替えるのが高くつくので Phase 2 から入れる。tombstone は同期を実際に
  設計する段階まで入れない（全クエリに `deletedAt IS NULL` が付く割に、
  競合解決のルールが無い状態では意味がないため）。
- **バギー / ドリフト / F1 対応**: ツーリング専用の固定タクソノミで設計している。

---

## 依存関係の更新方針

- Compose BOM、Navigation、Lifecycle は四半期に 1 回まとめて上げる。個別に上げない。
- AGP と Gradle は互換表に従い組で上げる。Kotlin と KSP も同様
  （`libs.versions.toml` の `kotlin` / `ksp` は必ず同じ Kotlin バージョン）。
- **セッティングシート化の実行中はバージョンを上げない。**
- Room 2.7 以降は KMP 対応でパッケージが変わる部分がある。Phase 5 の P-7 と同時に検討する。
- 更新ごとに `gradlew test` / `:app:assembleDebug` / `:app:lintDebug` と
  `HANDOFF.md` §7 の手動チェックを通す。
