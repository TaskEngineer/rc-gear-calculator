package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.room.dao.SavedSetupDao
import io.github.taskengineer.rcgear.data.local.room.entity.SavedSetupEntity
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import io.github.taskengineer.rcgear.domain.repository.SetupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SetupRepository] の Room 実装。
 *
 * Entity とドメインモデル SavedSetup の相互変換を担い、
 * UI / Domain 層からは Room の存在を隠蔽する。
 */
@Singleton
class SetupRepositoryImpl @Inject constructor(
    private val setupDao: SavedSetupDao,
    private val timeProvider: TimeProvider
) : SetupRepository {

    /** 全セッティングを更新日時の新しい順で監視する */
    override fun observeAll(): Flow<List<SavedSetup>> =
        setupDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** ID 指定で1件取得。存在しなければ null */
    override suspend fun getById(id: Long): SavedSetup? =
        setupDao.getById(id)?.toDomain()

    /** 同名セッティングが既に存在するか（保存ダイアログのバリデーション用） */
    override suspend fun existsByName(name: String): Boolean =
        setupDao.countByName(name) > 0

    /**
     * 新規保存。createdAt / updatedAt は現在時刻を自動設定する。
     * @return 採番された id
     */
    override suspend fun save(
        name: String,
        chassisId: String,
        pinion: Int,
        spur: Int,
        internalRatioSnapshot: Double,
        kv: Int,
        cells: Int,
        tireMm: Int
    ): Long {
        val now = timeProvider.now()
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
    override suspend fun getAllOnce(): List<SavedSetup> = observeAll().first()

    /**
     * インポートしたセッティングの一括復元（BUG-3）。
     * 1 トランザクションで実行されるため、途中で失敗しても半端に取り込まれない。
     */
    override suspend fun restoreAll(setups: List<SavedSetup>) {
        if (setups.isEmpty()) return
        setupDao.insertAll(setups.map { it.toEntity().copy(id = 0) })
    }

    /** 既存セッティングの上書き保存。createdAt は維持し updatedAt のみ更新する */
    override suspend fun update(setup: SavedSetup) {
        setupDao.update(
            setup.toEntity().copy(updatedAt = timeProvider.now())
        )
    }

    /** ID 指定で削除 */
    override suspend fun delete(id: Long) {
        setupDao.deleteById(id)
    }

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    override suspend fun deleteAll() {
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
