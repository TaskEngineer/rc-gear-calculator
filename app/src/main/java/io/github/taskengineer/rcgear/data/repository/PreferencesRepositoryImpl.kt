package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.datastore.UserPreferencesDataSource
import io.github.taskengineer.rcgear.domain.model.ThemeMode
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * [PreferencesRepository] の DataStore 実装。
 *
 * 現状は UserPreferencesDataSource の薄いラッパーだが、
 * 他のリポジトリと同じ層でアクセスを揃えることで ViewModel からの見え方を統一する
 * （ViewModel は DataSource の存在を知らない）。
 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource
) : PreferencesRepository {

    /** ユーザー設定の監視 */
    override val userPreferences: Flow<UserPreferences> = dataSource.userPreferences

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setShowMphAlongside(show: Boolean) = dataSource.setShowMphAlongside(show)

    override suspend fun setAnimationEnabled(enabled: Boolean) = dataSource.setAnimationEnabled(enabled)

    override suspend fun setBalanceFdr(fdr: Double) = dataSource.setBalanceFdr(fdr)

    /** CALC 画面の入力状態を保存する（次回起動時の復元用） */
    override suspend fun setLastCalcState(
        chassisId: String?,
        pinion: Int,
        spur: Int,
        kv: Int,
        cells: Int,
        tireMm: Int
    ) = dataSource.setLastCalcState(chassisId, pinion, spur, kv, cells, tireMm)

    /** 全設定をデフォルトに戻す（CONFIG 画面の「全データ削除」用） */
    override suspend fun clear() = dataSource.clear()
}
