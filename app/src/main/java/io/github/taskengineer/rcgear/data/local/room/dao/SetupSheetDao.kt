package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.taskengineer.rcgear.data.local.room.entity.SetupSheetEntity
import io.github.taskengineer.rcgear.data.local.room.relation.SheetWithValues
import kotlinx.coroutines.flow.Flow

/**
 * セッティングシートの DAO（M-3）。
 *
 * シートと値を別々に読むと「ヘッダは新しいのに値が古い」瞬間が生まれるため、
 * 1 枚を丸ごと読む経路は `@Transaction` + `@Relation`（[SheetWithValues]）にしてある。
 */
@Dao
interface SetupSheetDao {

    /** 1 台ぶんのシート一覧。走行日の新しい順、日付未入力は作成順で後ろ */
    @Query(
        """
        SELECT * FROM setup_sheets
        WHERE carId = :carId
        ORDER BY sessionDate IS NULL, sessionDate DESC, createdAt DESC
        """
    )
    fun observeByCar(carId: String): Flow<List<SetupSheetEntity>>

    /** シート 1 枚 + その値。ヘッダと値を同じトランザクションで読む */
    @Transaction
    @Query("SELECT * FROM setup_sheets WHERE id = :id")
    fun observeWithValues(id: String): Flow<SheetWithValues?>

    @Transaction
    @Query("SELECT * FROM setup_sheets WHERE id = :id")
    suspend fun getWithValues(id: String): SheetWithValues?

    @Query("SELECT * FROM setup_sheets WHERE id = :id")
    suspend fun getById(id: String): SetupSheetEntity?

    /** エクスポート用の単発取得 */
    @Transaction
    @Query("SELECT * FROM setup_sheets ORDER BY createdAt")
    suspend fun getAllWithValuesOnce(): List<SheetWithValues>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SetupSheetEntity)

    @Update
    suspend fun update(entity: SetupSheetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SetupSheetEntity>)

    /** 値は CASCADE で消える */
    @Query("DELETE FROM setup_sheets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM setup_sheets")
    suspend fun deleteAll()
}
