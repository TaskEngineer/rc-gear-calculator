package io.github.taskengineer.rcgear.domain.repository

import io.github.taskengineer.rcgear.domain.model.ThemeMode
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/** ユーザー設定の窓口（REF-2 / S-5）。実装は DataStore Preferences。 */
interface PreferencesRepository {

    /** ユーザー設定の監視 */
    val userPreferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setShowMphAlongside(show: Boolean)

    suspend fun setAnimationEnabled(enabled: Boolean)

    suspend fun setBalanceFdr(fdr: Double)

    /** CALC 画面の入力状態を保存する（次回起動時の復元用） */
    suspend fun setLastCalcState(
        chassisId: String?,
        pinion: Int,
        spur: Int,
        kv: Int,
        cells: Int,
        tireMm: Int
    )

    /** 全設定をデフォルトに戻す（CONFIG 画面の「全データ削除」用） */
    suspend fun clear()
}
