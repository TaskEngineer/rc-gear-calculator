package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import io.github.taskengineer.rcgear.data.local.datastore.UserPreferencesDataSource
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ユーザー設定のリポジトリ。
 *
 * 現状は UserPreferencesDataSource の薄いラッパーだが、
 * 他のリポジトリと同じ層でアクセスを揃えることで ViewModel からの見え方を統一する
 * （ViewModel は DataSource の存在を知らない）。
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataSource: UserPreferencesDataSource
) {

    /** ユーザー設定の監視 */
    val userPreferences: Flow<UserPreferences> = dataSource.userPreferences

    suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    suspend fun setShowMphAlongside(show: Boolean) = dataSource.setShowMphAlongside(show)

    suspend fun setAnimationEnabled(enabled: Boolean) = dataSource.setAnimationEnabled(enabled)

    suspend fun setBalanceFdr(fdr: Double) = dataSource.setBalanceFdr(fdr)

    /** CALC 画面の入力状態を保存する（次回起動時の復元用） */
    suspend fun setLastCalcState(
        chassisId: String?,
        pinion: Int,
        spur: Int,
        kv: Int,
        cells: Int,
        tireMm: Int
    ) = dataSource.setLastCalcState(chassisId, pinion, spur, kv, cells, tireMm)

    /** 全設定をデフォルトに戻す（CONFIG 画面の「全データ削除」用） */
    suspend fun clear() = dataSource.clear()
}
