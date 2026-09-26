package io.github.taskengineer.rcgear.domain.repository

/**
 * 計算履歴の窓口（REF-2 / S-5）。
 *
 * 注意: このリポジトリは **セッティングシート化（Phase 2 / M-3）で削除予定**。
 * 書き込み専用で読み出し経路が存在せず、セッティングシート自体が
 * これより遥かに良い履歴になるため（計画 §7.5）。それまでの繋ぎとして
 * interface 化だけしておく。
 */
interface CalculationHistoryRepository {

    /** 計算結果を1件記録する */
    suspend fun record(chassisId: String, pinion: Int, spur: Int, topSpeedKmh: Double)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    suspend fun deleteAll()
}
