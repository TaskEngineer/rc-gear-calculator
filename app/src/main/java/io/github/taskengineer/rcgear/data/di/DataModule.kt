package io.github.taskengineer.rcgear.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * データレイヤー全体の Hilt モジュール。
 *
 * 現状は ChassisJsonProvider のみだが、Step 5 で Room、Step 6 で DataStore の
 * Provides メソッドがここに追加される。
 *
 * ChassisJsonProvider は @Inject constructor を持つので、
 * このモジュールに @Provides を書かなくても Hilt が自動で解決してくれる。
 * → このファイルは現時点では「空のマーカー」だが、後で使う予定地として用意しておく。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    // Step 5 以降で以下が追加される予定:
    //   @Provides @Singleton fun provideDatabase(...): RcGearDatabase
    //   @Provides fun provideOverrideDao(db: RcGearDatabase): ChassisOverrideDao
    //   ... など
}