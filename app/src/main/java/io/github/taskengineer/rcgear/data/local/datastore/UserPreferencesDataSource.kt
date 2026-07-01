package io.github.taskengineer.rcgear.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore Preferences とドメインモデル UserPreferences の相互変換を担うデータソース。
 *
 * - キー定義はこのクラスに閉じ込め、外部にはドメインモデルだけを見せる。
 * - 読み取り時に IOException（ファイル破損など）が起きた場合は
 *   emptyPreferences() = 全デフォルト値として扱い、アプリを落とさない。
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // ----- キー定義 -----
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")           // ThemeMode.name を保存
        val SHOW_MPH_ALONGSIDE = booleanPreferencesKey("show_mph_alongside")
        val ANIMATION_ENABLED = booleanPreferencesKey("animation_enabled")
        val BALANCE_FDR = doublePreferencesKey("balance_fdr")
        val LAST_SELECTED_CHASSIS_ID = stringPreferencesKey("last_selected_chassis_id")
        val LAST_PINION = intPreferencesKey("last_pinion")
        val LAST_SPUR = intPreferencesKey("last_spur")
        val LAST_KV = intPreferencesKey("last_kv")
        val LAST_CELLS = intPreferencesKey("last_cells")
        val LAST_TIRE_MM = intPreferencesKey("last_tire_mm")
    }

    /** ユーザー設定の監視。未保存のキーは UserPreferences のデフォルト値で埋める */
    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs.toUserPreferences() }

    // ----- 個別の設定変更（CONFIG 画面用） -----

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setShowMphAlongside(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_MPH_ALONGSIDE] = show }
    }

    suspend fun setAnimationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ANIMATION_ENABLED] = enabled }
    }

    suspend fun setBalanceFdr(fdr: Double) {
        dataStore.edit { it[Keys.BALANCE_FDR] = fdr }
    }

    // ----- CALC 画面の状態保存（次回起動時の復元用） -----

    /**
     * CALC 画面の入力状態をまとめて保存する。
     * スライダー操作のたびではなく、画面離脱時などの区切りで呼ぶ想定。
     */
    suspend fun setLastCalcState(
        chassisId: String?,
        pinion: Int,
        spur: Int,
        kv: Int,
        cells: Int,
        tireMm: Int
    ) {
        dataStore.edit { prefs ->
            if (chassisId != null) {
                prefs[Keys.LAST_SELECTED_CHASSIS_ID] = chassisId
            } else {
                prefs.remove(Keys.LAST_SELECTED_CHASSIS_ID)
            }
            prefs[Keys.LAST_PINION] = pinion
            prefs[Keys.LAST_SPUR] = spur
            prefs[Keys.LAST_KV] = kv
            prefs[Keys.LAST_CELLS] = cells
            prefs[Keys.LAST_TIRE_MM] = tireMm
        }
    }

    /** 全設定をデフォルトに戻す（CONFIG 画面の「全データ削除」用） */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    // ----- Preferences → ドメインモデルの変換 -----

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themeMode = this[Keys.THEME_MODE]?.toThemeModeOrNull() ?: defaults.themeMode,
            showMphAlongside = this[Keys.SHOW_MPH_ALONGSIDE] ?: defaults.showMphAlongside,
            animationEnabled = this[Keys.ANIMATION_ENABLED] ?: defaults.animationEnabled,
            balanceFdr = this[Keys.BALANCE_FDR] ?: defaults.balanceFdr,
            lastSelectedChassisId = this[Keys.LAST_SELECTED_CHASSIS_ID],
            lastPinion = this[Keys.LAST_PINION] ?: defaults.lastPinion,
            lastSpur = this[Keys.LAST_SPUR] ?: defaults.lastSpur,
            lastKv = this[Keys.LAST_KV] ?: defaults.lastKv,
            lastCells = this[Keys.LAST_CELLS] ?: defaults.lastCells,
            lastTireMm = this[Keys.LAST_TIRE_MM] ?: defaults.lastTireMm
        )
    }

    /**
     * 保存された文字列を ThemeMode に変換する。
     * 将来 enum 名が変わった場合など、不正値はデフォルトに落とすため null を返す。
     */
    private fun String.toThemeModeOrNull(): ThemeMode? =
        ThemeMode.entries.firstOrNull { it.name == this }
}
