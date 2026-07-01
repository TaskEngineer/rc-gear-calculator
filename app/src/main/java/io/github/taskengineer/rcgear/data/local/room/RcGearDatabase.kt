package io.github.taskengineer.rcgear.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.taskengineer.rcgear.data.local.room.dao.CalculationHistoryDao
import io.github.taskengineer.rcgear.data.local.room.dao.ChassisOverrideDao
import io.github.taskengineer.rcgear.data.local.room.dao.SavedSetupDao
import io.github.taskengineer.rcgear.data.local.room.entity.CalculationHistoryEntity
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SavedSetupEntity

/**
 * アプリ本体の Room データベース。
 *
 * - version 1 で開始（PLAN 9.4）。Phase 2 で calculation_history のUI追加時に
 *   スキーマ変更が入る可能性があるため、schemas/ へのエクスポートを有効にしている
 *   （build.gradle.kts の room.schemaLocation 設定）。
 * - 全カラムがプリミティブ型のため TypeConverter は現時点では不要。
 */
@Database(
    entities = [
        SavedSetupEntity::class,
        ChassisOverrideEntity::class,
        CalculationHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class RcGearDatabase : RoomDatabase() {

    abstract fun savedSetupDao(): SavedSetupDao
    abstract fun chassisOverrideDao(): ChassisOverrideDao
    abstract fun calculationHistoryDao(): CalculationHistoryDao

    companion object {
        const val DATABASE_NAME = "rcgear.db"
    }
}
