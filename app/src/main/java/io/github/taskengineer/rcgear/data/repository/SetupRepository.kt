package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.room.dao.SavedSetupDao
import io.github.taskengineer.rcgear.data.local.room.entity.SavedSetupEntity
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保存セッティングのリポジトリ。
 *
 * Room の Entity とドメインモデル SavedSetup の相互変換を担い、
 * UI / Domain 層からは Room の存在を隠蔽する。
 */
@Singleton
class SetupRepository @Inject constructor(
    private val setupDao: SavedSetupDao
) {

    /** 全セッティングを更新日時の新しい順で監視する */
    fun observeAll(): Flow<List<SavedSetup>> =
        setupDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** ID 指定で1件取得。存在しなければ null */
    suspend fun getById(id: Long): SavedSetup? =
        setupDao.getById(id)?.toDomain()

    /** 同名セッティングが既に存在するか（保存ダイアログのバリデーション用） */
    suspend fun existsByName(name: String): Boolean =
        setupDao.countByName(name) > 0

    /**
     * 新規保存。createdAt / updatedAt は現在時刻を自動設定する。
     * @return 採番された id
     */
    suspend fun save(
        name: String,
        chassisId: String,
        pinion: Int,
        spur: Int,
        internalRatioSnapshot: Double,
        kv: Int,
        cells: Int,
        tireMm: Int
    ): Long {
        val now = System.currentTimeMillis()
        return setupDao.insert(
            SavedSetupEntity(
                name = name,
                chassisId = chassisId,
                pinion = pinion,
                spur = spur,
                internalRatioSnapshot = internalRatioSnapshot,
                kv = kv,
                cells = cells,
                tireMm = tireMm,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 全セッティングの単発取得（エクスポート用） */
    suspend fun getAllOnce(): List<SavedSetup> = observeAll().first()

    /**
     * インポートしたセッティングの復元。
     * save() と違い createdAt / updatedAt を元データのまま保持する。
     * id は端末固有のため 0（自動採番）で登録する。
     * @return 採番された id
     */
    suspend fun restore(setup: SavedSetup): Long =
        setupDao.insert(setup.toEntity().copy(id = 0))

    /** 既存セッティングの上書き保存。createdAt は維持し updatedAt のみ更新する */
    suspend fun update(setup: SavedSetup) {
        setupDao.update(
            setup.toEntity().copy(updatedAt = System.currentTimeMillis())
        )
    }

    /** ID 指定で削除 */
    suspend fun delete(id: Long) {
        setupDao.deleteById(id)
    }

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    suspend fun deleteAll() {
        setupDao.deleteAll()
    }

    // ----- Entity ⇔ ドメインモデルの変換 -----

    private fun SavedSetupEntity.toDomain(): SavedSetup = SavedSetup(
        id = id,
        name = name,
        chassisId = chassisId,
        pinion = pinion,
        spur = spur,
        internalRatioSnapshot = internalRatioSnapshot,
        kv = kv,
        cells = cells,
        tireMm = tireMm,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun SavedSetup.toEntity(): SavedSetupEntity = SavedSetupEntity(
        id = id,
        name = name,
        chassisId = chassisId,
        pinion = pinion,
        spur = spur,
        internalRatioSnapshot = internalRatioSnapshot,
        kv = kv,
        cells = cells,
        tireMm = tireMm,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
