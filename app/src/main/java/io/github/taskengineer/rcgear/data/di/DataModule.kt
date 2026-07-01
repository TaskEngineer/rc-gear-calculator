package io.github.taskengineer.rcgear.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * データレイヤー全体の Hilt モジュール。
 *
 * Room 関連は DatabaseModule に分離済み（Step 5）。
 * ここでは DataStore Preferences のインスタンスを提供する（Step 6）。
 *
 * ChassisJsonProvider / 各 Repository / UserPreferencesDataSource は
 * @Inject constructor を持つので、@Provides を書かなくても Hilt が自動で解決する。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private const val USER_PREFERENCES_NAME = "user_preferences"

    /**
     * DataStore Preferences のシングルトン。
     *
     * Context 拡張の preferencesDataStore デリゲートではなく Factory を使うのは、
     * Hilt 管理下に置いてテストで差し替え可能にするため。
     * ファイルは files/datastore/user_preferences.preferences_pb に作られる。
     */
    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_NAME) }
        )
}
