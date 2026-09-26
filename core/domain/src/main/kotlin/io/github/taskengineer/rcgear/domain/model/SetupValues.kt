package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.schema.ChassisDefault
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * セッティングシート 1 枚ぶんの値の束（M-2）。
 *
 * レジストリ（`TouringSetupSchema`）が「どんな項目があるか」を、この型が
 * 「その項目に何が入っているか」を持つ。UI もドメインも **保存形式（EAV か JSON 列か）を
 * 知らない**ので、重いと感じたら Repository の中だけで差し替えられる（計画 §4.1）。
 *
 * **レジストリに無いキーも捨てずに保持する。** 将来の版で追加された項目が入った
 * エクスポート JSON を読んでも値が消えないことが、スキーマを安全に進化させる担保になる。
 */
@JvmInline
value class SetupValues(val map: Map<String, SetupValue>) {

    val keys: Set<String> get() = map.keys
    val size: Int get() = map.size
    fun isEmpty(): Boolean = map.isEmpty()

    operator fun get(fieldKey: String): SetupValue? = map[fieldKey]

    operator fun contains(fieldKey: String): Boolean = map.containsKey(fieldKey)

    /** 整数として読む。小数が入っていたら切り捨てずに null（型違いは呼び出し側に見せる） */
    fun intOf(fieldKey: String): Int? = (map[fieldKey] as? SetupValue.IntV)?.value

    /**
     * 小数として読む。[SetupValue.IntV] も受け付ける
     * （`step = 1.0` の項目を後から小数化したときに過去データが読めなくなるのを避けるため）。
     */
    fun decimalOf(fieldKey: String): Double? = when (val value = map[fieldKey]) {
        is SetupValue.DecimalV -> value.value
        is SetupValue.IntV -> value.value.toDouble()
        else -> null
    }

    fun boolOf(fieldKey: String): Boolean? = (map[fieldKey] as? SetupValue.BoolV)?.value

    fun choiceOf(fieldKey: String): String? = (map[fieldKey] as? SetupValue.ChoiceV)?.key

    fun textOf(fieldKey: String): String? = (map[fieldKey] as? SetupValue.TextV)?.value

    /** 1 項目だけ差し替えた新しい束を返す */
    fun with(fieldKey: String, value: SetupValue): SetupValues =
        SetupValues(map + (fieldKey to value))

    /** 1 項目を空欄に戻した新しい束を返す（`null` を入れるのではなくキーごと消す） */
    fun without(fieldKey: String): SetupValues = SetupValues(map - fieldKey)

    /** 右側の値で上書きした束を返す。ベースラインからの複製（G-2）で使う */
    operator fun plus(other: SetupValues): SetupValues = SetupValues(map + other.map)

    /** レジストリに無いキー。UI では「未分類」として読み取り専用で出す */
    fun unknownKeys(): Set<String> = map.keys.filterNot { TouringSetupSchema.isKnown(it) }.toSet()

    companion object {
        val EMPTY = SetupValues(emptyMap())

        fun of(vararg pairs: Pair<String, SetupValue>): SetupValues = SetupValues(pairs.toMap())
    }
}

/**
 * シャーシ DB 由来の既定値。新規シートを起こすときだけ使う。
 *
 * 一度シートに書き込まれた値はシャーシ DB を参照しない（§4.5）。
 * 後からシャーシ DB を編集しても既存シートが変わらないのはそのため。
 */
data class ChassisDefaults(val internalRatio: Double?, val defaultTireMm: Int?) {

    fun valueFor(source: ChassisDefault): Double? = when (source) {
        ChassisDefault.INTERNAL_RATIO -> internalRatio
        ChassisDefault.DEFAULT_TIRE_MM -> defaultTireMm?.toDouble()
    }

    companion object {
        val NONE = ChassisDefaults(internalRatio = null, defaultTireMm = null)

        fun from(chassis: Chassis): ChassisDefaults =
            ChassisDefaults(chassis.internalRatio, chassis.defaultTireMm)
    }
}

/**
 * 新規シートの初期値を作る。
 *
 * `defaultFrom` があればシャーシ DB の値を、無ければ `defaultValue` を入れる。
 * どちらも無い項目は**キーごと入れない**（＝空欄で始まる）。
 * 空欄と「0 が入っている」は意味が違うので、0 で埋めないこと。
 */
fun TouringSetupSchema.initialValues(defaults: ChassisDefaults): SetupValues {
    val initial = buildMap {
        for (field in allFields) {
            when (field) {
                is NumberFieldDef -> {
                    val value = field.defaultFrom?.let { defaults.valueFor(it) } ?: field.defaultValue
                    if (value != null) put(field.key, SetupValue.number(value, field.decimals))
                }

                is ChoiceFieldDef -> field.defaultValue?.let { put(field.key, SetupValue.ChoiceV(it)) }

                else -> Unit
            }
        }
    }
    return SetupValues(initial)
}

/**
 * ギアセクションの値を `GearCalculator` の入力に変換する（M-2）。
 *
 * **bag と計算機の接続点はこの 1 関数だけ。** `GearCalculator` は bag を知らず、
 * 従来どおり `GearCalculationInput` を受け取る純粋関数のまま。
 *
 * シャーシ DB は参照しない。シート作成時に `initialValues` で焼き込んだ絶対値を読むだけ。
 * 1 項目でも欠けている / 計算が成立しない（ピニオン・スパー・内部減速比が 0 以下）なら `null` を返す。
 * 範囲外の値そのものは弾かない — 範囲の判断は `FieldValidator` の仕事で、
 * ここで二重に弾くと「シートには入っているのに計算だけ黙って出ない」状態になる。
 */
fun SetupValues.toGearInput(): GearCalculationInput? {
    val pinion = intOf("pinion") ?: return null
    val spur = intOf("spur") ?: return null
    val internalRatio = decimalOf("internalRatio") ?: return null
    val kv = intOf("motorKv") ?: return null
    val cells = intOf("cells") ?: return null
    val tireMm = intOf("tireMm") ?: return null

    // 計算式が成立しない値（ゼロ除算になる）は null にする。範囲外そのものは弾かない
    if (pinion <= 0 || spur <= 0) return null
    if (!GearCalculationInput.isValidInternalRatio(internalRatio)) return null

    return GearCalculationInput(
        pinion = pinion,
        spur = spur,
        internalRatio = internalRatio,
        kv = kv,
        cells = cells,
        tireMm = tireMm
    )
}
