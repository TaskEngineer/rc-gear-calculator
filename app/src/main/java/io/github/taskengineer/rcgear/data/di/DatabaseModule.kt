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

    /**
     * Database の生成。
     *
     * fallbackToDestructiveMigration について（S-10）:
     * Migration を 1 つも登録していない状態で `version` を上げると、Room は
     * ワイプではなく IllegalStateException で落ちる。本アプリは未公開・
     * ユーザーは開発者本人のみで、データの持ち出しは CONFIG の JSON
     * エクスポートで担保されているため、スキーマ変更時は作り直しを選ぶ。
     *
     * 公開する場合はここを外し、Migration と MigrationTestHelper による
     * テスト（ROADMAP P-7）を用意すること。
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RcGearDatabase =
        Room.databaseBuilder(
            context,
            RcGearDatabase::class.java,
            RcGearDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSavedSetupDao(db: RcGearDatabase): SavedSetupDao = db.savedSetupDao()

    @Provides
    fun provideChassisOverrideDao(db: RcGearDatabase): ChassisOverrideDao = db.chassisOverrideDao()

    @Provides
    fun provideCalculationHistoryDao(db: RcGearDatabase): CalculationHistoryDao =
        db.calculationHistoryDao()
}
