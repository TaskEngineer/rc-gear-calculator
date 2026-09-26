package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.ThemeMode
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [PreferencesRepository] のインメモリ Fake（REF-3 / S-6）。
 *
 * 本物と同じく Flow で流すので、「CONFIG で mph 併記を切り替えると
 * CALC の表示が追従する」といった購読の振る舞いもテストできる。
 */
class FakePreferencesRepository(
    initial: UserPreferences = UserPreferences()
) : PreferencesRepository {

    private val state = MutableStateFlow(initial)

    override val userPreferences: Flow<UserPreferences> = state

    /** アサーション用。現在の設定値 */
    val current: UserPreferences get() = state.value

    /** テストから設定変更を流し込む（CONFIG 画面での操作を模す） */
    fun emit(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setShowMphAlongside(show: Boolean) {
        state.value = state.value.copy(showMphAlongside = show)
    }

    override suspend fun setAnimationEnabled(enabled: Boolean) {
        state.value = state.value.copy(animationEnabled = enabled)
    }

    override suspend fun setBalanceFdr(fdr: Double) {
        state.value = state.value.copy(balanceFdr = fdr)
    }

    override suspend fun setLastCalcState(
        chassisId: String?,
        pinion: Int,
        spur: Int,
        kv: Int,
        cells: Int,
        tireMm: Int
    ) {
        state.value = state.value.copy(
            lastSelectedChassisId = chassisId,
            lastPinion = pinion,
            lastSpur = spur,
            lastKv = kv,
            lastCells = cells,
            lastTireMm = tireMm
        )
    }

    override suspend fun clear() {
        state.value = UserPreferences()
    }
}
