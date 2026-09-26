package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.taskengineer.rcgear.data.local.room.entity.UserChassisEntity
import kotlinx.coroutines.flow.Flow

/** ユーザー定義シャーシの DAO（M-3。UI は F-5） */
@Dao
interface UserChassisDao {

    @Query("SELECT * FROM user_chassis ORDER BY makerName, name")
    fun observeAll(): Flow<List<UserChassisEntity>>

    @Query("SELECT * FROM user_chassis WHERE id = :id")
    suspend fun getById(id: String): UserChassisEntity?

    @Query("SELECT * FROM user_chassis ORDER BY createdAt")
    suspend fun getAllOnce(): List<UserChassisEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UserChassisEntity)

    @Update
    suspend fun update(entity: UserChassisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserChassisEntity>)

    @Query("DELETE FROM user_chassis WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_chassis")
    suspend fun deleteAll()
}
