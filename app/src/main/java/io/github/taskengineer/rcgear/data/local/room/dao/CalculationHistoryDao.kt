package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.taskengineer.rcgear.data.local.room.entity.CalculationHistoryEntity

/**
 * 計算履歴の DAO。
 *
 * MVP では Insert のみ使用する（PLAN 2.1.3）。
 * observeRecent() は Phase 2 の履歴UI実装に備えて先に定義しておく。
 */
@Dao
interface CalculationHistoryDao {

    /** 履歴を1件記録する */
    @Insert
    suspend fun insert(entity: CalculationHistoryEntity)

    /** 直近の履歴を新しい順に取得する（Phase 2 の履歴UIで使用予定） */
    @Query("SELECT * FROM calculation_history ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CalculationHistoryEntity>

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    @Query("DELETE FROM calculation_history")
    suspend fun deleteAll()
}
