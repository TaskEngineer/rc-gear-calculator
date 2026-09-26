package io.github.taskengineer.rcgear.domain.backup

import io.github.taskengineer.rcgear.domain.common.IdGenerator
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * v1 のバックアップを v2 の形に変換する（M-6）。
 *
 * AGENTS.md §4「旧バージョンの読み込みは残す」に従い、この経路は**永久に残す**。
 *
 * ### 対応づけ
 * v1 の「保存セッティング」は 1 件が「シャーシ + ギア設定 + 名前」だった。
 * v2 には車（Car）という層が挟まるので、**同じシャーシの保存セッティングを
 * 1 台の車にまとめ、各セッティングをその車の 1 枚のシートにする**。
 *
 * ```
 * v1: [Rd1(TT-02), Rd2(TT-02), 練習(TA08)]
 * v2: 車「TT-02」    -> シート Rd1 / Rd2
 *     車「TA08」     -> シート 練習
 * ```
 *
 * 1 セッティング = 1 台にする案は採らない。同じ車のセッティング履歴が
 * バラバラの車として並ぶことになり、差分（軸 B）が使えなくなるため。
 *
 * 車の名前はシャーシ id をそのまま使う。v1 はシャーシの表示名を持っておらず
 * （id しか無い）、シャーシ DB は `:core:domain` からは見えない。
 * 取り込んだあとユーザーが改名すればよい種類の情報なので、推測はしない。
 *
 * ### 失われるもの
 * v1 は id を出力していなかったので、**取り込むたびに新しい UUID が振られる**
 * （＝ v1 ファイルの取り込みは冪等ではない）。v2 以降は id を出すので冪等になる。
 */
object LegacyBackupConverter {

    /**
     * @param setups      v1 の保存セッティング
     * @param idGenerator 車とシートの UUID 採番
     */
    fun toV2(setups: List<LegacySavedSetup>, idGenerator: IdGenerator): Converted {
        if (setups.isEmpty()) return Converted(emptyList(), emptyList())

        val cars = mutableListOf<Car>()
        val sheets = mutableListOf<SetupSheetWithValues>()
        val carIdByChassis = mutableMapOf<String, String>()

        // 並び順は v1 の入力順のまま。chassisId 単位でまとめる
        for (setup in setups) {
            val carId = carIdByChassis.getOrPut(setup.chassisId) {
                val id = idGenerator.newId()
                cars += Car(
                    id = id,
                    name = setup.chassisId,
                    chassisId = setup.chassisId,
                    note = null,
                    isArchived = false,
                    createdAt = setup.createdAt,
                    updatedAt = setup.updatedAt
                )
                id
            }
            sheets += SetupSheetWithValues(
                sheet = SetupSheet(
                    id = idGenerator.newId(),
                    carId = carId,
                    name = setup.name,
                    baselineId = null,
                    conditions = SessionConditions(),
                    note = null,
                    isFavorite = false,
                    schemaId = TouringSetupSchema.SCHEMA_ID,
                    createdAt = setup.createdAt,
                    updatedAt = setup.updatedAt
                ),
                values = setup.toValues()
            )
        }

        // 車の updatedAt は、その車の一番新しいセッティングに合わせる
        val latestByCar = sheets.groupBy { it.sheet.carId }
            .mapValues { (_, list) -> list.maxOf { it.sheet.updatedAt } }
        return Converted(
            cars = cars.map { it.copy(updatedAt = latestByCar[it.id] ?: it.updatedAt) },
            sheets = sheets
        )
    }

    /**
     * v1 の 6 項目を bag に移す。
     *
     * `internalRatioSnapshot` は `internalRatio` に入れる。v2 では bag が常に絶対値を持ち、
     * 全フィールドが構造的にスナップショットなので、「保存時の値」という特別扱いが消える（計画 §4.5）。
     * 範囲外の値もそのまま入れる — 棄却するかどうかは取り込みポリシー（UseCase）の判断。
     */
    private fun LegacySavedSetup.toValues(): SetupValues = SetupValues.of(
        "pinion" to SetupValue.IntV(pinion),
        "spur" to SetupValue.IntV(spur),
        "internalRatio" to SetupValue.DecimalV(internalRatioSnapshot),
        "motorKv" to SetupValue.IntV(kv),
        "cells" to SetupValue.IntV(cells),
        "tireMm" to SetupValue.IntV(tireMm)
    )

    data class Converted(
        val cars: List<Car>,
        val sheets: List<SetupSheetWithValues>
    )
}
