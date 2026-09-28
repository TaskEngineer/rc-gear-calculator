# ロードマップ（ROADMAP）— RcGear Android

> **Last Updated**: 2026-09-28（Phase 3 完了 + 実機確認で Phase 3.5 を追加、BUG-6 / BUG-7 は同日に修正済み）
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

## Phase 1 — 共有 UI / 文言 / ナビの土台【完了: 2026-09-26】

40 項目のフォームを書く前に必要な前提条件。ここを飛ばすと同じ部品を 10 回書くことになる。

着手前の UI 層の問題は「**共有コンポーネント層が存在しない**」こと。`DetailRow` / `ConfigRow` /
`SwitchRow` / `SectionHeader` / `MetricCell` / `SetupCard` が全て `private`、
再利用可能な入力部品は `GearSlider` ただ 1 つ、`@Preview` はゼロだった。

実施順は依存関係に従って S-12 → U-3 → U-4 → U-1 → U-2 → S-11 にした
（文言リソース化を最後にすることで、移設後のコードを 1 回で掃ける）。

| ID | 内容 | 状態 |
|---|---|---|
| S-12 | REF-5: 型安全ルート（`@Serializable`）。親タブ判定を `hasRoute()` に | 完了 `acbd038` |
| U-3 | DEBT-6: `CalcRequestBus` 廃止 → ルート引数 `Calc(setupId)` + `SavedStateHandle` | 完了 `8bd116d` |
| U-4 | DEBT-10: `update {}` 内の再計算・副作用を `setState()` に追い出す | 完了 `28c437b` |
| U-1 | `core/designsystem/component/` 新設（13 部品、全てに `@Preview`） | 完了 `d8687a9` |
| U-2 | `RcDetailScaffold` + `ScreenEvent` / `ObserveEvents` | 完了 `95a619e` |
| S-11 | REF-4: 文言リソース化（`stringResource()` / `UiText` / `@StringRes`） | 完了 `c8fa274` |

到達点: 単体テスト 87 → **95 件**、`@Preview` 0 → 13、
`strings.xml` 1 件 → 約 110 件、ハードコードされた日本語は `@Preview` のサンプルのみ。
設計判断は `HANDOFF.md` §5.6 に記録した。

---

## Phase 2 — ドメイン再構築（本丸）【完了: 2026-09-26】

実施順は M-1 → M-2 → M-5 → M-3 → M-4 → M-6 → M-8 → M-7。
純粋なドメイン（M-1 / M-2 / M-5）を先に固め、その後で Room を作り替えた。

| ID | 内容 | 状態 |
|---|---|---|
| M-1 | `:core:domain` に `FieldDef` / `SectionDef` / `TouringSetupSchema`（ギア + 前後ダンパー + 主要サス の第 1 スライス） | 完了 `7492800` |
| M-2 | `SetupValue` / `SetupValues` / `toGearInput()`。`GearCalculationInput.init` の範囲 `require` を `FieldValidator` に移管 | 完了 `696f9ae` |
| M-5 | `SheetDiff.compare()` — 3 つの比較軸を 1 つの純粋関数に落とす | 完了 `ca1db68` |
| M-3 | Room v2: `cars` / `setup_sheets` / `setup_values` / `user_chassis` 追加、`saved_setups` と `calculation_history` 削除 | 完了 `0c94124` |
| M-4 | `CarRepository` / `SetupSheetRepository`（domain に interface、data に Impl）。`IdGenerator` 注入 | 完了 `32af42a` |
| M-6 | エクスポート JSON v2 + **v1 → v2 インポータ**。v1 の読み込みは永久に残す | 完了 `b431e24` |
| M-8 | インポートをファイル全体で 1 トランザクションにする（BUG-3 の積み残し） | 完了 `994fafb` |
| M-7 | `chassis-db.json` v2: `maker` / `category` / `drive` を追加。**既存 45 個の `id` は不変**（テストで固定） | 完了 `c7d2b9c` |

到達点: 単体テスト 95 → **251 件**、Room v2（`app/schemas/2.json`）、
エクスポート JSON v2（v1 読み込みは永続）、`getChassisById` が O(1)。

**Phase 2 終了時点のアプリの状態**: タブは `[CALC] [DB] [CONFIG]` の 3 つ。
SETUPS は `saved_setups` ごと無くなり、GARAGE は Phase 3 の G-1 で入る。
CALC の保存と流し込みも G-5 まで一時的に外れている。
設計判断は `HANDOFF.md` §5.7 に記録した。

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

## Phase 3 — UI 構築【完了: 2026-09-28】

タブ構成は `[GARAGE] [CALC] [DB] [CONFIG]`。GARAGE が SETUPS を置換した。
**CALC はトップレベルに残した**（「車の文脈なしで 10 秒で終わる問い」に答える画面なので、
シートのセクションに格下げすると一番速い操作が一番遅くなる）。

実施順は G-1 → G-7 → F-1 → F-5 → F-4。**画面を作ってからレジストリを横展開した**のは、
「項目を足しても Composable が増えない」ことをその順序でしか確かめられないため。

| ID | 内容 | 状態 |
|---|---|---|
| G-1 | GARAGE タブ: 車一覧 + 車の CRUD | 完了 `78da13b` |
| G-2 | 車詳細 = シート一覧 +「新規シート」/「ベースラインから複製」 | 完了 `93528a8` |
| G-3 | シート閲覧画面: 実物のシート用紙に近い F/C/R グリッド表示 | 完了 `07b3bb1` |
| G-4 | シート編集画面: セクション単位編集、`FieldEditor` ディスパッチ、インラインバリデーション | 完了 `d352862` |
| G-4' | シートのヘッダ編集（走行条件・ベースライン・備考） | 完了 `eeb4eb5` |
| G-5 | ギアセクション ↔ CALC 双方向連携 | 完了 `8b62a68` |
| G-6 | 差分 UI 3 軸（A / B / C） | 完了 `6a1a124` |
| G-7 | **フィールドの横展開**（駆動系・タイヤ・ESC・車体・走行結果）。UI コードは増えない | 完了 `47b1e5d` |
| F-1 | ロールアウト表示（タイヤ周長 ÷ FDR） | 完了 `dd9ce79` |
| F-5 | ユーザー定義シャーシ（`user_chassis` は M-3 で作成済み） | 完了 `c649019` |
| F-4 | 共有（`FileProvider` + `ACTION_SEND`）。テキスト版シート + 既存 PNG | 完了 `f88fff5` |

到達点: 単体テスト 251 → **332 件**（`:core:domain` 127 / `:app` 205）、
画面 3 → 9、`strings.xml` 約 135 → 約 330 件、Room のスキーマは変更なし（v2 のまま）。

**G-7 の受け入れ条件は満たせた**: 約 30 項目を足したコミット `47b1e5d` が触ったのは
レジストリ・単位 enum・`strings.xml`・`SetupFieldLabels` の 4 ファイルだけで、
Composable の差分はゼロ。設計判断は `HANDOFF.md` §5.8 に記録した。

**Phase 3 で未実施**: 実機 / エミュレータでの動作確認と instrumented テスト。
JVM 単体テストとビルドまでしか通していない（`HANDOFF.md` §7.1）。

---

## Phase 3.5 — 実機確認で出た不具合【2026-09-28 追加】

実機確認と instrumented テストは **2026-09-28 に実施済み**（結果は `HANDOFF.md` §7.1。
DAO テスト 16 件は Pixel_8 と実機 SO-53C の両方で全緑、チェックリスト 1〜19 も通った）。
そこで出た不具合を Phase 4 より先に潰す。優先順位の基準 1「クラッシュを消す」に従う。
**BUG-6 / BUG-7 は 2026-09-28 に修正済み。** 残りは UI の粗さだけなので、
Phase 4 の前に必ず片づけるべき理由はもう無い。着手順は好みで決めてよい。

| ID | 内容 | 優先 |
|---|---|---|
| ~~BUG-6~~ | ~~消えたシートに CALC から「反映」すると FK 違反でクラッシュ~~ → **2026-09-28 修正済み**（`HANDOFF.md` §2） | 完了 |
| ~~BUG-7~~ | ~~シャーシ上書きの範囲外入力が保存時まで弾かれない~~ → **2026-09-28 修正済み** | 完了 |
| ~~—~~ | ~~`UserChassisEdit` / `CarEdit` / `SheetHeaderEdit` も保存時にまとめて検証する古い形のまま~~ → **2026-09-28 に 5 画面すべて揃えた**（`HANDOFF.md` §5.9） | 完了 |
| — | `DatePicker` がシステムロケール依存で英語になる | 低 |
| — | ライトテーマのアクセント色が白背景で低コントラスト（WCAG AA 未達） | 低 |
| — | CALC の FAB 2 個がスクロール内容に重なる（実機 720px で値が隠れる） | 低 |
| — | シート閲覧のツールバー 5 アイコンが 720px でタイトルを潰す | 低 |

---

## Phase 4 — その後

セッティングシートが動いてから再評価する。Phase 3.5 に残っているのは落ちない項目だけなので、
Phase 4 と並べて優先順位を決めてよい。

| ID | 機能 | メモ |
|---|---|---|
| ~~F-9~~ | ~~セッティングの編集・上書き保存~~ | **取り下げ**。シート編集（G-4）に吸収された |
| F-2 | 目標速度からの逆算 | 持っているピニオン歯数を CONFIG に登録すると実用的 |
| F-10 | モーター入力の拡張（ターン数 → KV 換算） | `motorTurn` は `TouringSetupSchema` に入る予定なので、換算テーブルだけの話になる |
| F-7 | 英語化（`values-en/`） | S-11 が前提。フィールドラベルも `@StringRes` なので通常手順で済む |
| F-8 | アプリショートカット | 長押しで直近シートを開く |
| — | `androidTest`: Compose UI スモーク 1 本 | DAO テストと `SetupSheetRepositoryImplTest`（BUG-6）は 2026-09-28 に実行済み・全緑（24 件）。残るは画面側 |
| — | シートの並べ替え・お気に入りでの絞り込み | 枚数が増えてから判断する |
| — | ヘッダ項目（路面・天候）の選択肢化 | 今は自由入力。使ってみてから語彙を決める |
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
