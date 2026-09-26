package io.github.taskengineer.rcgear.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * セッティングシート 1 枚のヘッダ（M-3）。
 *
 * **ヘッダ列と bag（`setup_values`）の線引き:**
 * > bag = 「車に対して設定した値」。ヘッダ列 = 「その設定を行った条件」。
 *
 * トラック名・気温・路面はここ（ヘッダ）に置く。bag に入れると汎用 diff の対象になり、
 * 「気温が 3℃ 違います」が「セッティング変更点」として出てしまうため。
 *
 * 旧 `saved_setups` の **`name` に対する UNIQUE 制約はここに持ち込まない**。
 * 車が 2 台あれば「Rd1」というシートは 2 つ存在して当然なので。
 *
 * @property baselineId 「どのシートから変えたか」。差分の軸 B（前回のセットからの変更点）の相手。
 *   自己参照 FK で、参照先が消えたら `SET NULL`（シートは残す）
 * @property schemaId   `TouringSetupSchema.SCHEMA_ID`。バギー用スキーマが増えたらここが分岐点になる
 * @property surface    路面。ツーリング専用なので選択肢は少ないが、
 *   サーキットごとに呼び方が違うので自由入力で持つ
 */
@Entity(
    tableName = "setup_sheets",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SetupSheetEntity::class,
            parentColumns = ["id"],
            childColumns = ["baselineId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("carId", "sessionDate"),
        Index("baselineId"),
        Index("updatedAt")
    ]
)
data class SetupSheetEntity(
    @PrimaryKey val id: String,
    val carId: String,
    val name: String,
    val baselineId: String?,
    val sessionDate: Long?,
    val trackName: String?,
    val surface: String?,
    val airTempC: Double?,
    val trackTempC: Double?,
    val humidityPct: Int?,
    val bestLapMs: Int?,
    val note: String?,
    val isFavorite: Boolean,
    val schemaId: String,
    val createdAt: Long,
    val updatedAt: Long
)
