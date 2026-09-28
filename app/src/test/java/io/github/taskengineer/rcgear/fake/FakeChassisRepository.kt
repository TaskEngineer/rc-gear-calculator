package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.model.UserChassis
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * [ChassisRepository] のインメモリ Fake（REF-3 / S-6）。
 *
 * 本物と同じく「標準値（不変）＋上書き（可変）」の合成を行う。
 * この合成こそがバグの出る場所なので、Fake でも省略しない。
 */
class FakeChassisRepository(
    private val standardMakers: List<Maker> = listOf(DEFAULT_MAKER),
    initialOverrides: List<ChassisOverride> = emptyList()
) : ChassisRepository, Snapshotable {

    private val overrides = MutableStateFlow(initialOverrides.associateBy { it.chassisId })

    /** ユーザー定義シャーシ（F-5）。本物と同じく一覧・単発取得に混ざる */
    private val userChassis = MutableStateFlow(emptyMap<String, UserChassis>())

    /** 採番される id の連番。本物と同じく `user_` 接頭辞を付ける */
    var nextUserChassisSeed: Int = 1

    /** overrideChassis() が刻む時刻。テストから差し替えられる */
    var now: Long = 1_000L

    /**
     * true にすると [restoreAllOverrides] が例外を投げる。
     * 「取り込みの途中で失敗したときに 1 行も入らない」ことを確かめるためのスイッチ（M-8）。
     */
    var failOnRestoreOverrides: Boolean = false

    /** アサーション用。現在の上書き一覧 */
    val storedOverrides: List<ChassisOverride> get() = overrides.value.values.toList()

    override fun getAllMakers(): Flow<List<Maker>> =
        combine(overrides, userChassis) { overrideMap, userMap ->
            val userByMaker = userMap.values.map { it.toChassis() }.groupBy { it.makerName }
            val standard = standardMakers.map { maker ->
                maker.copy(
                    chassis = maker.chassis.map { it.mergeWith(overrideMap[it.id]) } +
                        userByMaker[maker.name].orEmpty()
                )
            }
            val knownMakers = standard.map { it.name }.toSet()
            standard + userByMaker
                .filterKeys { it !in knownMakers }
                .map { (name, chassis) -> Maker(name = name, chassis = chassis) }
        }

    override suspend fun getChassisById(chassisId: String): Chassis? =
        if (UserChassis.isUserDefined(chassisId)) {
            userChassis.value[chassisId]?.toChassis()
        } else {
            standardChassis(chassisId)?.mergeWith(overrides.value[chassisId])
        }

    /** 自作シャーシは「標準値」がそれ自身（上書きの対象ではない）。本物と同じ扱い */
    override suspend fun getStandardChassisById(chassisId: String): Chassis? =
        if (UserChassis.isUserDefined(chassisId)) {
            userChassis.value[chassisId]?.toChassis()
        } else {
            standardChassis(chassisId)
        }

    override suspend fun overrideChassis(
        chassisId: String,
        internalRatio: Double?,
        defaultTireMm: Int?,
        note: String?
    ) {
        if (internalRatio == null && defaultTireMm == null && note == null) {
            overrides.value -= chassisId
            return
        }
        overrides.value += chassisId to ChassisOverride(
            chassisId = chassisId,
            internalRatio = internalRatio,
            defaultTireMm = defaultTireMm,
            note = note,
            updatedAt = now
        )
    }

    override suspend fun getAllOverridesOnce(): List<ChassisOverride> = storedOverrides

    /**
     * [restoreAllOverrides] の呼ばれた回数。
     * 1 件ずつ insert すると途中失敗で半端に取り込まれる（BUG-3）ので、
     * 「一括で 1 回」を検証するために数える。
     */
    var restoreAllOverridesCallCount: Int = 0
        private set

    override suspend fun restoreAllOverrides(overrides: List<ChassisOverride>) {
        restoreAllOverridesCallCount++
        if (failOnRestoreOverrides) error("上書きの書き込みに失敗（テスト用）")
        if (overrides.isEmpty()) return
        this.overrides.value += overrides.associateBy { it.chassisId }
    }

    override suspend fun resetOverride(chassisId: String) {
        overrides.value -= chassisId
    }

    override suspend fun resetAllOverrides() {
        overrides.value = emptyMap()
    }

    // ----- ユーザー定義シャーシ（F-5） -----

    override fun observeUserChassis(): Flow<List<UserChassis>> =
        userChassis.map { it.values.sortedBy { chassis -> chassis.createdAt } }

    override suspend fun getUserChassis(id: String): UserChassis? = userChassis.value[id]

    override suspend fun addUserChassis(chassis: UserChassis): String {
        // id の採番は実装の責務（接頭辞の規約を呼び出し側に漏らさない）
        val id = UserChassis.ID_PREFIX + "chassis-${nextUserChassisSeed++}"
        userChassis.value += id to chassis.copy(id = id, createdAt = now, updatedAt = now)
        return id
    }

    override suspend fun updateUserChassis(chassis: UserChassis) {
        val stored = userChassis.value[chassis.id] ?: return
        userChassis.value += chassis.id to chassis.copy(
            createdAt = stored.createdAt,
            updatedAt = now
        )
    }

    override suspend fun deleteUserChassis(id: String) {
        userChassis.value -= id
    }

    override suspend fun getAllUserChassisOnce(): List<UserChassis> =
        userChassis.value.values.sortedBy { it.createdAt }

    /** 一括で 1 回だけ呼ばれていることの確認用（BUG-3 と同じ理由） */
    var restoreAllUserChassisCallCount: Int = 0
        private set

    override suspend fun restoreAllUserChassis(chassis: List<UserChassis>) {
        restoreAllUserChassisCallCount++
        if (chassis.isEmpty()) return
        userChassis.value += chassis.associateBy { it.id }
    }

    override suspend fun deleteAllUserChassis() {
        userChassis.value = emptyMap()
    }

    private fun standardChassis(chassisId: String): Chassis? =
        standardMakers.asSequence().flatMap { it.chassis }.firstOrNull { it.id == chassisId }

    private fun Chassis.mergeWith(override: ChassisOverride?): Chassis =
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

    companion object {
        val TT02 = Chassis(
            id = "tamiya_tt02",
            name = "TT-02",
            internalRatio = 2.6,
            defaultTireMm = 63
        )
        val TA08 = Chassis(
            id = "tamiya_ta08",
            name = "TA08",
            internalRatio = 1.9,
            defaultTireMm = 62
        )
        val DEFAULT_MAKER = Maker(name = "タミヤ", chassis = listOf(TT02, TA08))
    }

    /**
     * トランザクションのロールバック対象（M-8）。**上書きと自作シャーシの両方**を控える。
     * 片方だけにすると、取り込みの途中失敗で自作シャーシだけ残る状態をテストが見逃す。
     */
    @Suppress("UNCHECKED_CAST")
    override fun restoreState(state: Any) {
        val (storedOverrides, storedUserChassis) =
            state as Pair<Map<String, ChassisOverride>, Map<String, UserChassis>>
        overrides.value = storedOverrides
        userChassis.value = storedUserChassis
    }

    override fun captureState(): Any = overrides.value to userChassis.value
}
