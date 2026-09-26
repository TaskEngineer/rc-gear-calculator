package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
) : ChassisRepository {

    private val overrides = MutableStateFlow(initialOverrides.associateBy { it.chassisId })

    /** overrideChassis() が刻む時刻。テストから差し替えられる */
    var now: Long = 1_000L

    /** アサーション用。現在の上書き一覧 */
    val storedOverrides: List<ChassisOverride> get() = overrides.value.values.toList()

    override fun getAllMakers(): Flow<List<Maker>> =
        overrides.map { map ->
            standardMakers.map { maker ->
                maker.copy(chassis = maker.chassis.map { it.mergeWith(map[it.id]) })
            }
        }

    override suspend fun getChassisById(chassisId: String): Chassis? =
        standardChassis(chassisId)?.mergeWith(overrides.value[chassisId])

    override suspend fun getStandardChassisById(chassisId: String): Chassis? =
        standardChassis(chassisId)

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

    override suspend fun restoreAllOverrides(overrides: List<ChassisOverride>) {
        if (overrides.isEmpty()) return
        this.overrides.value += overrides.associateBy { it.chassisId }
    }

    override suspend fun resetOverride(chassisId: String) {
        overrides.value -= chassisId
    }

    override suspend fun resetAllOverrides() {
        overrides.value = emptyMap()
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
}
