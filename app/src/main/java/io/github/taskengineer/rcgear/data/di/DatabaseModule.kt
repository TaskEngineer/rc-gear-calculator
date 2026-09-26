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
     * 破壊的再作成について（S-10、範囲を限定: Phase 0 レビュー）:
     * Migration を 1 つも登録していない状態で `version` を上げると、Room は
     * ワイプではなく IllegalStateException で落ちる。本アプリは未公開・
     * ユーザーは開発者本人のみで、データの持ち出しは CONFIG の JSON
     * エクスポートで担保されているため、Phase 2 の v1 → v2 だけは作り直しを選ぶ
     * （HANDOFF §5.2）。
     *
     * ただし `fallbackToDestructiveMigration()`（無引数）だと **将来の全ての
     * バージョンで** 移行漏れが黙ってデータ消失になる。手動移行を選んだのは
     * v1 からの 1 回だけなので、`fallbackToDestructiveMigrationFrom(1)` で
     * 起点バージョンを限定する。v2 以降で Migration を書き忘れた場合は
     * 起動時にクラッシュして気づける。
     *
     * 公開する場合はこの行ごと外し、Migration と MigrationTestHelper による
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
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides
    fun provideSavedSetupDao(db: RcGearDatabase): SavedSetupDao = db.savedSetupDao()

    @Provides
    fun provideChassisOverrideDao(db: RcGearDatabase): ChassisOverrideDao = db.chassisOverrideDao()

    @Provides
    fun provideCalculationHistoryDao(db: RcGearDatabase): CalculationHistoryDao =
        db.calculationHistoryDao()
}
