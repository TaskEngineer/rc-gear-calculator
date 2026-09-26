package io.github.taskengineer.rcgear.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.taskengineer.rcgear.data.local.room.entity.SetupValueEntity

/**
 * シートの値（EAV）の DAO（M-3）。
 *
 * EAV を採った理由は 1 つだけ: **値が `(sheetId, fieldKey)` という安定キーで
 * アドレス可能になること**。「この項目だけ書き換える」が 1 行の upsert で済み、
 * 「この項目だけ前回シートからコピー」も素直に書ける。
 * JSON 列だと常に「全体を読んで書き戻す」になる（計画 §4.1）。
 */
@Dao
interface SetupValueDao {

    @Query("SELECT * FROM setup_values WHERE sheetId = :sheetId")
    suspend fun getBySheet(sheetId: String): List<SetupValueEntity>

    /** 主キーが (sheetId, fieldKey) なので REPLACE がそのまま upsert になる */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SetupValueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SetupValueEntity>)

    /** 空欄にする＝行ごと消す。`num`/`text` を両方 null にした行は作らない */
    @Query("DELETE FROM setup_values WHERE sheetId = :sheetId AND fieldKey = :fieldKey")
    suspend fun delete(sheetId: String, fieldKey: String)

    @Query("DELETE FROM setup_values WHERE sheetId = :sheetId")
    suspend fun deleteBySheet(sheetId: String)

    /** 束ごと入れ替えるとき、消えた項目を落とすために使う */
    @Query("DELETE FROM setup_values WHERE sheetId = :sheetId AND fieldKey NOT IN (:keepKeys)")
    suspend fun deleteBySheetExcept(sheetId: String, keepKeys: List<String>)

    @Query("DELETE FROM setup_values")
    suspend fun deleteAll()
}
