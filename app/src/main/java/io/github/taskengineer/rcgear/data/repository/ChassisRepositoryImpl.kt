package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.asset.ChassisJsonProvider
import io.github.taskengineer.rcgear.data.local.room.dao.ChassisOverrideDao
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChassisRepository] の実装（本アプリの中核ロジック、PLAN 4.3）。
 *
 * 標準DB（assets/chassis-db.json）と Room の上書きテーブルを合成し、
 * 最終的なシャーシ一覧を提供する。
 *
 * - 標準DBは読み取り専用。ユーザーの変更は chassis_overrides に差分として保存される。
 * - overrideDao.observeAll() を起点にした Flow なので、上書きの登録・リセットが
 *   起きるたびに合成結果が自動で流れ直す。UI 側は collect するだけでよい。
 */
@Singleton
class ChassisRepositoryImpl @Inject constructor(
    private val jsonProvider: ChassisJsonProvider,
    private val overrideDao: ChassisOverrideDao,
    private val timeProvider: TimeProvider
) : ChassisRepository {

    /**
     * 全メーカーのシャーシ一覧（上書き合成済み）を監視する。
     * メーカーの並び順は JSON の定義順を保持する。
     */
    override fun getAllMakers(): Flow<List<Maker>> =
        overrideDao.observeAll().map { overrides ->
            val overrideMap = overrides.associateBy { it.chassisId }
            jsonProvider.getMakers().map { maker ->
                maker.copy(
                    chassis = maker.chassis.map { it.mergeWith(overrideMap[it.id]) }
                )
            }
        }

    /**
     * ID 指定で1台分（上書き合成済み）を取得する。存在しなければ null。
     * 保存セッティングの詳細表示など、単発取得の用途向け。
     */
    override suspend fun getChassisById(chassisId: String): Chassis? {
        // M-7 で Map 引き（O(1)）になった。v1 はメーカーを総なめしていた
        val base = jsonProvider.getById(chassisId) ?: return null
        return base.mergeWith(overrideDao.getByChassisId(chassisId))
    }

    /**
     * ID 指定で標準値（JSON 由来、上書き適用前）を取得する。
     * シャーシ編集画面で「標準値との差分」を表示するために使う。
     */
    override suspend fun getStandardChassisById(chassisId: String): Chassis? =
        jsonProvider.getById(chassisId)

    /**
     * シャーシの上書きを登録・更新する。
     * すべてのフィールドが null（= 標準値と同じにしたい）の場合は、
     * 無意味なレコードを残さないようリセットとして扱う。
     */
    override suspend fun overrideChassis(
        chassisId: String,
        internalRatio: Double?,
        defaultTireMm: Int?,
        note: String?
    ) {
        if (internalRatio == null && defaultTireMm == null && note == null) {
            overrideDao.deleteByChassisId(chassisId)
            return
        }
        overrideDao.upsert(
            ChassisOverrideEntity(
                chassisId = chassisId,
                internalRatio = internalRatio,
                defaultTireMm = defaultTireMm,
                note = note,
                updatedAt = timeProvider.now()
            )
        )
    }

    /** 全上書きの単発取得（エクスポート用） */
    override suspend fun getAllOverridesOnce(): List<ChassisOverride> =
        overrideDao.observeAll().first().map { entity ->
            ChassisOverride(
                chassisId = entity.chassisId,
                internalRatio = entity.internalRatio,
                defaultTireMm = entity.defaultTireMm,
                note = entity.note,
                updatedAt = entity.updatedAt
            )
        }

    /**
     * インポートした上書きの一括復元（BUG-3）。
     * 1 トランザクションで実行されるため、途中で失敗しても半端に取り込まれない。
     */
    override suspend fun restoreAllOverrides(overrides: List<ChassisOverride>) {
        if (overrides.isEmpty()) return
        overrideDao.upsertAll(
            overrides.map { override ->
                ChassisOverrideEntity(
                    chassisId = override.chassisId,
                    internalRatio = override.internalRatio,
                    defaultTireMm = override.defaultTireMm,
                    note = override.note,
                    updatedAt = override.updatedAt
                )
            }
        )
    }

    /** 上書きをリセットし、標準値（JSON値）に戻す */
    override suspend fun resetOverride(chassisId: String) {
        overrideDao.deleteByChassisId(chassisId)
    }

    /** 全上書きをリセットする（CONFIG 画面の「全データ削除」用） */
    override suspend fun resetAllOverrides() {
        overrideDao.deleteAll()
    }

    /**
     * JSON 由来の標準値に上書きを適用する。
     * フィールド単位で「上書きがあれば優先、なければ標準値」（PLAN 4.3）。
     */
    private fun Chassis.mergeWith(override: ChassisOverrideEntity?): Chassis =
        if (override == null) {
            this
        } else {
            copy(
                internalRatio = override.internalRatio ?: internalRatio,
                defaultTireMm = override.defaultTireMm ?: defaultTireMm,
                note = override.note ?: note,
                isUserEdited = true
            )
        }
}
