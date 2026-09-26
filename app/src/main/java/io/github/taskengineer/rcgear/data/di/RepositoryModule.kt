package io.github.taskengineer.rcgear.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec
import io.github.taskengineer.rcgear.data.repository.ChassisRepositoryImpl
import io.github.taskengineer.rcgear.data.repository.PreferencesRepositoryImpl
import io.github.taskengineer.rcgear.data.system.SystemTimeProvider
import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import javax.inject.Singleton

/**
 * domain の Repository interface と data の実装を結線する（REF-2 / S-5）。
 *
 * `@Binds` を使うのは `@Provides` より安い（Dagger が実装クラスを直接返すコードを生成し、
 * ファクトリメソッドの本体を持たない）ため。`@Singleton` は実装クラス側の
 * アノテーションではなくこちらの束縛に付ける必要がある。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChassisRepository(impl: ChassisRepositoryImpl): ChassisRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBackupCodec(impl: JsonBackupCodec): BackupCodec

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
