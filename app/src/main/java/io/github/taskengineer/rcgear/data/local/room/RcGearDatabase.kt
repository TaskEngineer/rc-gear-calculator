package io.github.taskengineer.rcgear.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.taskengineer.rcgear.data.local.room.dao.CarDao
import io.github.taskengineer.rcgear.data.local.room.dao.ChassisOverrideDao
import io.github.taskengineer.rcgear.data.local.room.dao.SetupSheetDao
import io.github.taskengineer.rcgear.data.local.room.dao.SetupValueDao
import io.github.taskengineer.rcgear.data.local.room.dao.UserChassisDao
import io.github.taskengineer.rcgear.data.local.room.entity.CarEntity
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SetupSheetEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SetupValueEntity
import io.github.taskengineer.rcgear.data.local.room.entity.UserChassisEntity

/**
 * アプリ本体の Room データベース。
 *
 * ### version 2（M-3。セッティングシート化）
 * - 追加: `cars` / `setup_sheets` / `setup_values` / `user_chassis`
 * - 削除: `saved_setups`（cars + setup_sheets に置き換え）
 * - 削除: `calculation_history`（DEBT-8。Insert のみ・読み出し経路ゼロで中身が薄く、
 *   セッティングシート自体が遥かに良い履歴になるため、UI を作らず削除で決着させた）
 * - 据え置き: `chassis_overrides`
 *
 * **v1 → v2 の Migration は書かない。** 未公開・利用者は開発者本人だけなので、
 * 「v1 の JSON をエクスポート → 破壊的再作成 → v2 インポータで戻す」という手動移行を選んだ
 * （HANDOFF §5.2）。破壊的再作成は `fallbackToDestructiveMigrationFrom(1)` で
 * **v1 からの 1 回だけ**に限定してある。v3 以降は Migration を書くこと。
 *
 * 外部キーの ON DELETE CASCADE / SET NULL を効かせるには、接続ごとに
 * `PRAGMA foreign_keys = ON` が要る。Room は `@Database` に宣言された
 * `@ForeignKey` に対してこれを自動で有効にする。
 */
@Database(
    entities = [
        CarEntity::class,
        SetupSheetEntity::class,
        SetupValueEntity::class,
        UserChassisEntity::class,
        ChassisOverrideEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RcGearDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun setupSheetDao(): SetupSheetDao
    abstract fun setupValueDao(): SetupValueDao
    abstract fun userChassisDao(): UserChassisDao
    abstract fun chassisOverrideDao(): ChassisOverrideDao

    companion object {
        const val DATABASE_NAME = "rcgear.db"
    }
}
