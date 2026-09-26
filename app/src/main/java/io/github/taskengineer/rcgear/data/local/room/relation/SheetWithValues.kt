package io.github.taskengineer.rcgear.data.local.room.relation

import androidx.room.Embedded
import androidx.room.Relation
import io.github.taskengineer.rcgear.data.local.room.entity.SetupSheetEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SetupValueEntity

/**
 * シート 1 枚とその値の束（M-3）。
 *
 * `@Transaction` 付きのクエリから返すことで、ヘッダと値が同じ時点のスナップショットになる。
 * 別々に読むと「ヘッダは新しいのに値は古い」中間状態が UI に出る余地が生まれる。
 */
data class SheetWithValues(
    @Embedded val sheet: SetupSheetEntity,
    @Relation(parentColumn = "id", entityColumn = "sheetId")
    val values: List<SetupValueEntity>
)
