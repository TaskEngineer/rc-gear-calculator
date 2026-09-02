# ロードマップ（ROADMAP）— RcGear Android

> **Last Updated**: 2026-09-02
> PLAN.md の Phase 2 / 3 を、MVP 完成後の視点で再評価して並べ直したもの。
> 負債・不具合の詳細は `HANDOFF.md`、作業ルールは `AGENTS.md`。

優先順位の基準:
1. **クラッシュを消す** ＞ 2. **将来の変更を安くする** ＞ 3. **自分が毎週使う機能** ＞ 4. **公開に必要なもの** ＞ 5. あれば嬉しいもの

---

## Phase 1.5 — 安定化（機能追加の前に済ませる）

| ID | 内容 | 規模 | 依存 |
|---|---|---|---|
| S-1 | BUG-1 / BUG-2 / BUG-3 修正（入力値検証の一元化 = REF-1） | 小 | なし |
| S-2 | BUG-5 修正（`gradle.properties` の絶対パス除去） | 極小 | なし |
| S-3 | REF-7 前半: ktlint 導入と一括整形 | 小 | なし |
| S-4 | REF-7 後半: GitHub Actions（build + unit test + lint） | 小 | S-2 |
| S-5 | REF-2: Domain 層の純化（Repository interface 化、ThemeMode 移動、GearCalculator 移動） | 中 | なし |
| S-6 | REF-3: テスト基盤（TimeProvider、Fake Repository、UseCase / CalcViewModel テスト） | 中 | S-5 |
| S-7 | REF-6: 数値整形の共通化（BUG-4 も解消） | 小 | なし |
| S-8 | DEBT-13 確認: オフライン端末で等幅フォントが表示されるか。出ないなら Roboto Mono を `res/font/` に同梱 | 小 | なし |

Phase 1.5 の完了条件: CI 緑、`domain/` に Android 依存ゼロ、既知のクラッシュ経路ゼロ。

---

## Phase 2 — 使う人（自分）が毎週得をする機能

PLAN.md の Phase 2 候補を再評価した。「自分が走行前に開く」場面を想定して並べ替えている。

| ID | 機能 | 価値 | 規模 | メモ |
|---|---|---|---|---|
| F-1 | **ロールアウト表示**（タイヤ周長 ÷ FDR、mm/回転） | 高 | 小 | `GearCalculator` に 1 行足すだけ。ツーリング勢は FDR よりロールアウトで会話する。`MetricsGrid` にタイルを 1 枚追加 |
| F-2 | **目標速度からの逆算**（速度を入れると近い FDR / ピニオン候補を出す） | 高 | 中 | 持っているピニオン歯数を CONFIG に登録し、その中から候補を出すと実用的。F-1 と同じ純粋関数で実装可能 |
| F-3 | **セッティング比較**（SETUPS で 2 件選んで差分表示） | 高 | 中 | `SetupDetailViewModel` の「保存時 / 現在」の並置 UI を流用できる |
| F-4 | **共有**（Share Sheet で PNG / テキスト） | 中 | 小 | 画像生成は Step 12 で済んでいる。`FileProvider` と `ACTION_SEND` を足すだけ |
| F-5 | **独自シャーシの追加**（DB 画面で新規エントリ作成） | 中 | 中 | `chassis_overrides` とは別に `user_chassis` テーブルを追加（Room v2 + Migration）。エクスポート JSON の `schemaVersion` も 2 へ。`id` は `user_<uuid>` 形式にして同梱 DB と衝突させない |
| F-6 | **計算履歴 UI**（最近の計算、よく使うシャーシ） | 低 | 中 | テーブルはあるが「保存時のみ記録」なので中身が薄い。F-2 の入力ログとして意味が出るまで後回し。作らないなら DEBT-8 としてテーブルごと削除する判断も可 |
| F-7 | **英語化**（`values-en/`） | 中 | 中 | REF-4（文言リソース化）が前提。Play 公開するなら必須 |
| F-8 | **アプリショートカット**（長押しで直近セッティングを開く） | 低 | 小 | `shortcuts.xml` 静的 + 動的ショートカット。SETUPS 上位 3 件 |
| F-9 | **セッティングの編集・上書き保存** | 中 | 小 | `SetupRepository.update` は実装済みだが UI から呼ばれていない。詳細画面に「現在の CALC の値で上書き」を追加 |
| F-10 | **モーター入力の拡張**（ターン数 → KV 換算、ブラシモーター向けの RPM 直入力） | 中 | 小 | 定数テーブル（例: 17.5T ≒ 2,500〜3,000KV）を `assets/` に置く。`GearCalculationInput.kv` はそのまま |

推奨着手順: F-1 → F-4 → F-9 → F-2 → F-3 → F-5 → F-7 → F-10 → F-8 → F-6

---

## Phase 3 — 公開準備

| ID | 内容 | メモ |
|---|---|---|
| P-1 | ライセンス決定（MIT 推奨）と `LICENSE` 追加。`chassis-db.json` の出典表記を「データ出典」画面（CONFIG > ABOUT）に出す | `ChassisDatabaseDto.sources` は既にパースしている |
| P-2 | OSS ライセンス表示画面（`com.google.android.gms.oss-licenses` か `aboutlibraries`） | Play 公開時の慣例 |
| P-3 | 署名設定（`keystore.properties` を `.gitignore` 済み、`signingConfigs` を `build.gradle.kts` に追加） | 鍵はコミットしない |
| P-4 | スクリーンショットを `docs/screenshots/` に配置し README を更新 | エミュレータで 4 画面 |
| P-5 | Play Console: プライバシーポリシー（データ収集なし）、コンテンツレーティング、`targetSdk` の年次更新 | 完全オフラインなので記述は短い |
| P-6 | バージョニング方針: `versionCode` を CI で自動採番、`versionName` は SemVer | タグ `v0.2.0` から運用 |
| P-7 | Room マイグレーションテスト（`MigrationTestHelper`、`androidTest`）を F-5 と同時に整備 | スキーマ JSON は既にエクスポートしている |

---

## Phase 4 — 先送り（需要が出たら）

- ホーム画面ウィジェット（Glance）: 直近セッティングの FDR と最高速を表示
- KV / モーターデータベース（メーカー・ターン数別）
- グラフ（ピニオン歯数 × 最高速の曲線、複数 KV の重ね描き）: Compose Canvas で自前描画、ライブラリ不要
- 走行メモ（路面、気温、結果）をセッティングに紐付け
- マルチモジュール化（`:core:domain` / `:core:data` / `:feature:*`）: ビルド時間が 2 分を超えたら
- Wear OS / タブレット向けレイアウト（`WindowSizeClass`）
- クラウド同期: **やらない**（完全オフラインが設計方針）

---

## 依存関係の更新方針

- Compose BOM、Navigation、Lifecycle は四半期に 1 回まとめて上げる。個別に上げない。
- AGP と Gradle は互換表に従い組で上げる。Kotlin と KSP も同様（`libs.versions.toml` の `kotlin` / `ksp` は必ず同じ Kotlin バージョン）。
- Room 2.7 以降は KMP 対応でパッケージが変わる部分がある。Phase 3 の P-7 と同時に検討する。
- 更新ごとに `assembleDebug` / `testDebugUnitTest` / `lintDebug` と `HANDOFF.md` §6 の手動チェックを通す。
