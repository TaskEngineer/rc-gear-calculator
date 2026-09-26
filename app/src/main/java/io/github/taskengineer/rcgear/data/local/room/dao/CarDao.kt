package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.taskengineer.rcgear.data.local.room.entity.CarEntity
import kotlinx.coroutines.flow.Flow

/** 車（GARAGE タブの主役）の DAO（M-3） */
@Dao
interface CarDao {

    /** 一覧。アーカイブ済みは除き、更新の新しい順 */
    @Query("SELECT * FROM cars WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<CarEntity>>

    /** アーカイブ済みも含めた全件 */
    @Query("SELECT * FROM cars ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars WHERE id = :id")
    fun observeById(id: String): Flow<CarEntity?>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun getById(id: String): CarEntity?

    /** エクスポート用の単発取得 */
    @Query("SELECT * FROM cars ORDER BY createdAt")
    suspend fun getAllOnce(): List<CarEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CarEntity)

    @Update
    suspend fun update(entity: CarEntity)

    /**
     * id 一致で置換する取り込み用。インポートは id による upsert なので
     * 同じファイルを 2 回読んでも増えない（冪等）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CarEntity>)

    /** 車を消すとシート（と値）も CASCADE で消える */
    @Delete
    suspend fun delete(entity: CarEntity)

    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cars")
    suspend fun deleteAll()
}
