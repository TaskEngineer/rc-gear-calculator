package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.taskengineer.rcgear.data.local.room.entity.SavedSetupEntity
import kotlinx.coroutines.flow.Flow

/**
 * 保存セッティングの DAO。
 *
 * 一覧表示は Flow で公開し、変更が UI に自動反映されるようにする。
 * 単発取得（詳細画面の初期読込など）は suspend 関数で提供する。
 */
@Dao
interface SavedSetupDao {

    /** 全セッティングを更新日時の新しい順で監視する（SETUPS 画面の一覧用） */
    @Query("SELECT * FROM saved_setups ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SavedSetupEntity>>

    /** ID 指定で1件取得。存在しなければ null */
    @Query("SELECT * FROM saved_setups WHERE id = :id")
    suspend fun getById(id: Long): SavedSetupEntity?

    /**
     * 同名セッティングの件数を返す（保存時の重複チェック用）。
     * name にはユニークインデックスがあるため 0 or 1。
     */
    @Query("SELECT COUNT(*) FROM saved_setups WHERE name = :name")
    suspend fun countByName(name: String): Int

    /**
     * 新規保存。name が重複した場合は SQLiteConstraintException を投げる
     * （事前チェックをすり抜けた場合の最終防衛線として ABORT を選択）。
     * @return 採番された id
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SavedSetupEntity): Long

    /** 既存セッティングの更新（上書き保存） */
    @Update
    suspend fun update(entity: SavedSetupEntity)

    /** ID 指定で削除 */
    @Query("DELETE FROM saved_setups WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    @Query("DELETE FROM saved_setups")
    suspend fun deleteAll()
}
