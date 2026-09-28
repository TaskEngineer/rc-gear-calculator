package io.github.taskengineer.rcgear.feature.db

import androidx.annotation.StringRes
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive

/**
 * シャーシの分類・駆動方式の文言（F-5）。
 *
 * enum は `:core:domain`（純 Kotlin JVM）にあり `R` を参照できないので、
 * 対応表を `:app` 側に置く。「定義は domain、文言は `:app`」（HANDOFF §5.3）の
 * 3 例目で、1 例目が `ThemeMode`、2 例目が `SetupFieldLabels`。
 *
 * 抜けは `ChassisLabelsTest` が検出する。
 */
@get:StringRes
val ChassisCategory.labelRes: Int
    get() = when (this) {
        ChassisCategory.TOURING -> R.string.chassis_category_touring
        ChassisCategory.BUGGY -> R.string.chassis_category_buggy
        ChassisCategory.DRIFT -> R.string.chassis_category_drift
        ChassisCategory.OTHER -> R.string.chassis_category_other
    }

@get:StringRes
val ChassisDrive.labelRes: Int
    get() = when (this) {
        ChassisDrive.SHAFT_4WD -> R.string.chassis_drive_shaft_4wd
        ChassisDrive.BELT_4WD -> R.string.chassis_drive_belt_4wd
        ChassisDrive.HYBRID_4WD -> R.string.chassis_drive_hybrid_4wd
        ChassisDrive.FWD -> R.string.chassis_drive_fwd
        ChassisDrive.RWD -> R.string.chassis_drive_rwd
        ChassisDrive.DIRECT -> R.string.chassis_drive_direct
    }
