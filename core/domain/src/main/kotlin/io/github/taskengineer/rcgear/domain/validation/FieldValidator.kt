package io.github.taskengineer.rcgear.domain.validation

import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.schema.BoolFieldDef
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TextFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * 値の妥当性検証（M-2）。**範囲を知っているのはレジストリだけ**という状態にする。
 *
 * これまで範囲チェックは `GearCalculationInput.init` の `require` にあり、
 * スライダー以外の経路（シャーシ上書き / JSON インポート / DataStore の古い値）から
 * 範囲外の値が来るとアプリが落ちていた（BUG-1 / BUG-2）。
 * 検証を `FieldDef` 駆動のここに移し、**呼び出し側は例外ではなく違反の一覧を受け取る**。
 *
 * 未知キーはここでは違反にしない。捨てずに保全するのが仕様（計画 §7.2）なので、
 * 必要なら [SetupValues.unknownKeys] で別に取る。
 */
object FieldValidator {

    /**
     * 1 項目を検証する。問題なければ `null`。
     *
     * @param field 項目の定義
     * @param value 入っている値
     */
    fun validate(field: FieldDef, value: SetupValue): FieldViolation? = when (field) {
        is NumberFieldDef -> validateNumber(field, value)
        is ChoiceFieldDef -> validateChoice(field, value)
        is TextFieldDef -> validateText(field, value)
        is BoolFieldDef -> if (value is SetupValue.BoolV) {
            null
        } else {
            FieldViolation.TypeMismatch(field.key, "bool", value.typeName())
        }
    }

    /**
     * 束をまとめて検証する。返るのは**既知キーの違反だけ**で、順序はレジストリの宣言順。
     * 空欄（キーが無い）は違反にしない — 必須項目という概念を入れていないため。
     */
    fun validateAll(values: SetupValues): List<FieldViolation> =
        TouringSetupSchema.allFields.mapNotNull { field ->
            val value = values[field.key] ?: return@mapNotNull null
            validate(field, value)
        }

    /** 違反が 1 つも無いか。インポート時の 1 行判定に使う */
    fun isValid(values: SetupValues): Boolean = validateAll(values).isEmpty()

    /**
     * 範囲外の値を有効範囲に丸める。型違い・未知の選択肢のように
     * 丸めようがないものは `null`（＝その項目は捨てる）を返す。
     *
     * 「棄却すると操作不能になる」場面（編集画面に出す値）で使う。
     * インポートのように「不正な行を数えて報告したい」場面では [validate] を使うこと。
     */
    fun coerce(field: FieldDef, value: SetupValue): SetupValue? = when (field) {
        is NumberFieldDef -> coerceNumber(field, value)
        is ChoiceFieldDef -> value.takeIf { it is SetupValue.ChoiceV && field.choiceSet.contains(it.key) }
        is TextFieldDef -> (value as? SetupValue.TextV)?.let { SetupValue.TextV(it.value.take(field.maxLength)) }
        is BoolFieldDef -> value.takeIf { it is SetupValue.BoolV }
    }

    /**
     * 束をまるごと丸める。未知キーは**そのまま通す**（保全が仕様）。
     * 丸められなかった既知キーは落ちる。
     */
    fun coerceAll(values: SetupValues): SetupValues {
        val coerced = buildMap {
            for ((key, value) in values.map) {
                val field = TouringSetupSchema.byKey[key]
                if (field == null) {
                    put(key, value)
                } else {
                    coerce(field, value)?.let { put(key, it) }
                }
            }
        }
        return SetupValues(coerced)
    }

    private fun validateNumber(field: NumberFieldDef, value: SetupValue): FieldViolation? {
        val number = when (value) {
            is SetupValue.IntV -> value.value.toDouble()
            is SetupValue.DecimalV -> value.value
            else -> return FieldViolation.TypeMismatch(field.key, "number", value.typeName())
        }
        if (field.isInteger && value is SetupValue.DecimalV && number != kotlin.math.floor(number)) {
            return FieldViolation.TypeMismatch(field.key, "int", "decimal")
        }
        if (!number.isFinite() || number < field.min || number > field.max) {
            return FieldViolation.OutOfRange(field.key, field.min, field.max, number)
        }
        return null
    }

    private fun validateChoice(field: ChoiceFieldDef, value: SetupValue): FieldViolation? {
        if (value !is SetupValue.ChoiceV) {
            return FieldViolation.TypeMismatch(field.key, "choice", value.typeName())
        }
        return if (field.choiceSet.contains(value.key)) {
            null
        } else {
            FieldViolation.UnknownChoice(field.key, value.key)
        }
    }

    private fun validateText(field: TextFieldDef, value: SetupValue): FieldViolation? {
        if (value !is SetupValue.TextV) {
            return FieldViolation.TypeMismatch(field.key, "text", value.typeName())
        }
        return if (value.value.length <= field.maxLength) {
            null
        } else {
            FieldViolation.TooLong(field.key, field.maxLength, value.value.length)
        }
    }

    private fun coerceNumber(field: NumberFieldDef, value: SetupValue): SetupValue? {
        val number = when (value) {
            is SetupValue.IntV -> value.value.toDouble()
            is SetupValue.DecimalV -> value.value
            else -> return null
        }
        if (!number.isFinite()) return null
        return SetupValue.number(number.coerceIn(field.min, field.max), field.decimals)
    }

    private fun SetupValue.typeName(): String = when (this) {
        is SetupValue.IntV -> "int"
        is SetupValue.DecimalV -> "decimal"
        is SetupValue.BoolV -> "bool"
        is SetupValue.ChoiceV -> "choice"
        is SetupValue.TextV -> "text"
    }
}

/**
 * 検証で見つかった問題。文言は持たない（`:core:domain` は `R` を参照できない）ので、
 * `:app` 側でこの型から `UiText` に変換する。
 */
sealed interface FieldViolation {

    /** どの項目の話か */
    val fieldKey: String

    /** 入っている値の型が定義と違う */
    data class TypeMismatch(
        override val fieldKey: String,
        val expected: String,
        val actual: String
    ) : FieldViolation

    /** 値が定義された範囲の外 */
    data class OutOfRange(
        override val fieldKey: String,
        val min: Double,
        val max: Double,
        val actual: Double
    ) : FieldViolation

    /** 選択肢に無いキーが入っている（表示名の変更ではなくキーの改名が起きた場合など） */
    data class UnknownChoice(
        override val fieldKey: String,
        val optionKey: String
    ) : FieldViolation

    /** 自由入力が長すぎる */
    data class TooLong(
        override val fieldKey: String,
        val maxLength: Int,
        val actual: Int
    ) : FieldViolation
}
