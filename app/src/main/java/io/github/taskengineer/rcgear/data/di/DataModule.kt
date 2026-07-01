package io.github.taskengineer.rcgear.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * データレイヤー全体の Hilt モジュール。
 *
 * Room 関連は DatabaseModule に分離した（Step 5）。
 * Step 6 で DataStore の Provides メソッドがここに追加される予定。
 *
 * ChassisJsonProvider / 各 Repository は @Inject constructor を持つので、
 * このモジュールに @Provides を書かなくても Hilt が自動で解決してくれる。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    // Step 6 で以下が追加される予定:
    //   @Provides @Singleton fun provideDataStore(...): DataStore<Preferences>
}