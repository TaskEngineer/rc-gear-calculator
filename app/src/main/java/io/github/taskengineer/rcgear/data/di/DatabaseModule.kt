package io.github.taskengineer.rcgear.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.taskengineer.rcgear.data.local.room.RcGearDatabase
import io.github.taskengineer.rcgear.data.local.room.dao.CalculationHistoryDao
import io.github.taskengineer.rcgear.data.local.room.dao.ChassisOverrideDao
import io.github.taskengineer.rcgear.data.local.room.dao.SavedSetupDao
import javax.inject.Singleton

/**
 * Room 関連の Hilt モジュール。
 *
 * Database はアプリで1インスタンス（@Singleton）。
 * DAO は Database から生成するだけの軽量オブジェクトなのでスコープ指定なし
 * （Database 側が Singleton なので実質同一インスタンスが返る）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RcGearDatabase =
        Room.databaseBuilder(
            context,
            RcGearDatabase::class.java,
            RcGearDatabase.DATABASE_NAME
        ).build()

    @Provides
    fun provideSavedSetupDao(db: RcGearDatabase): SavedSetupDao = db.savedSetupDao()

    @Provides
    fun provideChassisOverrideDao(db: RcGearDatabase): ChassisOverrideDao = db.chassisOverrideDao()

    @Provides
    fun provideCalculationHistoryDao(db: RcGearDatabase): CalculationHistoryDao =
        db.calculationHistoryDao()
}
