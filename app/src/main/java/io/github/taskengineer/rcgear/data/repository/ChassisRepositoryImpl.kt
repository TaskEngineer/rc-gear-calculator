package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.asset.ChassisJsonProvider
import io.github.taskengineer.rcgear.data.local.room.dao.ChassisOverrideDao
import io.github.taskengineer.rcgear.data.local.room.dao.UserChassisDao
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import io.github.taskengineer.rcgear.data.local.room.entity.UserChassisEntity
import io.github.taskengineer.rcgear.domain.common.IdGenerator
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.model.UserChassis
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChassisRepository] の実装（本アプリの中核ロジック、PLAN 4.3）。
 *
 * 標準DB（assets/chassis-db.json）と Room の上書きテーブル、
 * そしてユーザー定義シャーシ（F-5）を合成し、最終的なシャーシ一覧を提供する。
 *
 * - 標準DBは読み取り専用。ユーザーの変更は chassis_overrides に差分として保存される。
 * - 2 つの DAO の Flow を combine しているので、上書きの登録・リセットや
 *   自作シャーシの追加が起きるたびに合成結果が自動で流れ直す。UI 側は collect するだけでよい。
 * - **ユーザー定義シャーシは既存メーカーに合流させる。** 「タミヤ」に自作エントリを
 *   足したとき、一覧に「タミヤ」が 2 つ並ばないようにするため。
 */
@Singleton
class ChassisRepositoryImpl @Inject constructor(
    private val jsonProvider: ChassisJsonProvider,
    private val overrideDao: ChassisOverrideDao,
    private val userChassisDao: UserChassisDao,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator
) : ChassisRepository {

    /**
     * 全メーカーのシャーシ一覧（上書き合成済み）を監視する。
     * メーカーの並び順は JSON の定義順を保持する。
     */
    override fun getAllMakers(): Flow<List<Maker>> =
        combine(
            overrideDao.observeAll(),
            userChassisDao.observeAll()
        ) { overrides, userChassis ->
            val overrideMap = overrides.associateBy { it.chassisId }
            val userByMaker = userChassis
                .map { it.toDomain().toChassis() }
                .groupBy { it.makerName }
            val standard = jsonProvider.getMakers().map { maker ->
                maker.copy(
                    chassis = maker.chassis.map { it.mergeWith(overrideMap[it.id]) } +
                        userByMaker[maker.name].orEmpty()
                )
            }
            // 同梱DBに無いメーカー名で作られた自作シャーシは、新しいメーカーとして末尾に足す
            val knownMakers = standard.map { it.name }.toSet()
            val extraMakers = userByMaker
                .filterKeys { it !in knownMakers }
                .map { (name, chassis) -> Maker(name = name, chassis = chassis) }
            standard + extraMakers
        }

    /**
     * ID 指定で1台分（上書き合成済み）を取得する。存在しなければ null。
     * 保存セッティングの詳細表示など、単発取得の用途向け。
     */
    override suspend fun getChassisById(chassisId: String): Chassis? {
        if (UserChassis.isUserDefined(chassisId)) {
            // 自作シャーシに上書き差分は無い（自分で直せるので差分を持つ意味が無い）
            return userChassisDao.getById(chassisId)?.toDomain()?.toChassis()
        }
        // M-7 で Map 引き（O(1)）になった。v1 はメーカーを総なめしていた
        val base = jsonProvider.getById(chassisId) ?: return null
        return base.mergeWith(overrideDao.getByChassisId(chassisId))
    }

    /**
     * ID 指定で標準値（JSON 由来、上書き適用前）を取得する。
     * シャーシ編集画面で「標準値との差分」を表示するために使う。
     */
    /**
     * 自作シャーシは「標準値」がそれ自身になる（上書きの対象ではないため）。
     * インポートの「同梱DBに居るか」判定はこのメソッドを使うので、
     * ここで自作分も返さないと、自作シャーシを指す上書きが常にスキップされる。
     */
    override suspend fun getStandardChassisById(chassisId: String): Chassis? =
        if (UserChassis.isUserDefined(chassisId)) {
            userChassisDao.getById(chassisId)?.toDomain()?.toChassis()
        } else {
            jsonProvider.getById(chassisId)
        }

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

    // ----- ユーザー定義シャーシ（F-5） -----

    override fun observeUserChassis(): Flow<List<UserChassis>> =
        userChassisDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getUserChassis(id: String): UserChassis? =
        userChassisDao.getById(id)?.toDomain()

    override suspend fun addUserChassis(chassis: UserChassis): String {
        // id は規約（user_ 接頭辞）を守るためここで採番する。呼び出し側には触らせない
        val id = UserChassis.ID_PREFIX + idGenerator.newId()
        val now = timeProvider.now()
        userChassisDao.insert(
            chassis.copy(id = id, createdAt = now, updatedAt = now).toEntity()
        )
        return id
    }

    override suspend fun updateUserChassis(chassis: UserChassis) {
        val stored = userChassisDao.getById(chassis.id) ?: return
        userChassisDao.update(
            chassis.copy(
                createdAt = stored.createdAt,
                updatedAt = timeProvider.now()
            ).toEntity()
        )
    }

    override suspend fun deleteUserChassis(id: String) {
        userChassisDao.deleteById(id)
    }

    override suspend fun getAllUserChassisOnce(): List<UserChassis> =
        userChassisDao.getAllOnce().map { it.toDomain() }

    override suspend fun restoreAllUserChassis(chassis: List<UserChassis>) {
        if (chassis.isEmpty()) return
        userChassisDao.upsertAll(chassis.map { it.toEntity() })
    }

    override suspend fun deleteAllUserChassis() {
        userChassisDao.deleteAll()
    }

    private fun UserChassisEntity.toDomain() = UserChassis(
        id = id,
        makerName = makerName,
        name = name,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        category = ChassisCategory.fromKey(category),
        traits = ChassisTraits(
            drive = ChassisDrive.fromKey(drive),
            hasCenterDiff = hasCenterDiff
        ),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun UserChassis.toEntity() = UserChassisEntity(
        id = id,
        makerName = makerName,
        name = name,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        category = category.name,
        drive = traits.drive?.name,
        hasCenterDiff = traits.hasCenterDiff,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

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
