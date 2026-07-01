package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * シャーシ上書きの DAO。
 *
 * observeAll() が ChassisRepository の合成ロジックの起点になる。
 * 上書きの追加・変更・削除が起きるたびに Flow が発火し、
 * 合成後のシャーシ一覧が自動で再計算される。
 */
@Dao
interface ChassisOverrideDao {

    /** 全上書きレコードを監視する（合成ロジックの入力） */
    @Query("SELECT * FROM chassis_overrides")
    fun observeAll(): Flow<List<ChassisOverrideEntity>>

    /** シャーシID 指定で1件取得。上書きがなければ null */
    @Query("SELECT * FROM chassis_overrides WHERE chassisId = :chassisId")
    suspend fun getByChassisId(chassisId: String): ChassisOverrideEntity?

    /**
     * 上書きの登録・更新。
     * chassisId が主キーなので REPLACE で「なければ挿入、あれば置換」になる。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChassisOverrideEntity)

    /**
     * 上書きのリセット。レコードごと削除することで標準値（JSON値）に戻る（PLAN 6.2）。
     */
    @Query("DELETE FROM chassis_overrides WHERE chassisId = :chassisId")
    suspend fun deleteByChassisId(chassisId: String)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    @Query("DELETE FROM chassis_overrides")
    suspend fun deleteAll()
}
