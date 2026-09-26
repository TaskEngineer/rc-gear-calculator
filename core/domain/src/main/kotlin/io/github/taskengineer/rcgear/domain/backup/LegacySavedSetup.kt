package io.github.taskengineer.rcgear.domain.backup

/**
 * v1 のエクスポート JSON に入っていた「保存セッティング」（旧 `SavedSetup`）。
 *
 * **これは過去のワイヤ形式であって、現在のドメインモデルではない。**
 * M-3 で `saved_setups` テーブルごと無くなり、車（`Car`）＋ セッティングシート
 * （`SetupSheet`）に置き換わった。それでも AGENTS.md §4 の
 * 「旧バージョンの読み込みは残す」に従い、v1 ファイルを読む経路は永久に残す。
 * 読み込んだ結果を v2 の形へ変換するのは M-6 の v1 → v2 インポータの仕事。
 *
 * 置き場所を `domain/model` ではなく `domain/backup` にしてあるのは、
 * 「今のアプリの概念」と誤解させないため。
 *
 * @property internalRatioSnapshot 保存時点で凍結した内部減速比。
 *   v2 では bag が常に絶対値を持つので、全フィールドが構造的にこの性質を備える
 *   （専用の「スナップショット」という概念自体が消えた。計画 §4.5）
 * @property createdAt / updatedAt エポックミリ秒
 */
data class LegacySavedSetup(
    val id: Long,
    val name: String,
    val chassisId: String,
    val pinion: Int,
    val spur: Int,
    val internalRatioSnapshot: Double,
    val kv: Int,
    val cells: Int,
    val tireMm: Int,
    val createdAt: Long,
    val updatedAt: Long
)
