package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.schema.BoolFieldDef
import io.github.taskengineer.rcgear.domain.schema.ChoiceFieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TextFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * `SetupValue` と「数値 1 本 + 文字列 1 本」の相互変換（M-4）。
 *
 * EAV の `setup_values` は `num` / `text` の 2 列しか持たない（3 本目は作らない）。
 * 読み戻すときにどちらの型として解釈するかは、値そのものではなく
 * **レジストリの型定義**が決める。その判断をここ 1 箇所に集める。
 *
 * 同じ変換はエクスポート JSON（値をスカラで書く。M-6）でも要るので、
 * Room ではなく `:core:domain` に置いてある。
 *
 * ### 未知キー
 * レジストリに無いキーは型が分からないので、`text` があれば [SetupValue.TextV]、
 * `num` があれば [SetupValue.DecimalV] として読む。整数に丸めないのは、
 * 記録された値を勝手に変えないため（差分では `IntV(22)` と `DecimalV(22.0)` を同値に扱う）。
 */
object SetupValueCodec {

    /** 保存用の 2 列表現。どちらか一方だけが非 null になる */
    data class Stored(val num: Double?, val text: String?)

    fun encode(value: SetupValue): Stored = when (value) {
        is SetupValue.IntV -> Stored(num = value.value.toDouble(), text = null)
        is SetupValue.DecimalV -> Stored(num = value.value, text = null)
        is SetupValue.BoolV -> Stored(num = if (value.value) 1.0 else 0.0, text = null)
        is SetupValue.ChoiceV -> Stored(num = null, text = value.key)
        is SetupValue.TextV -> Stored(num = null, text = value.value)
    }

    /**
     * 2 列表現から値を復元する。両方 null なら `null`
     * （そういう行は書かない。空欄は行ごと消すのが仕様）。
     */
    fun decode(fieldKey: String, num: Double?, text: String?): SetupValue? {
        val field = TouringSetupSchema.byKey[fieldKey]
        return when (field) {
            is NumberFieldDef -> num?.let { SetupValue.number(it, field.decimals) }
            is BoolFieldDef -> num?.let { SetupValue.BoolV(it != 0.0) }
            is ChoiceFieldDef -> text?.let { SetupValue.ChoiceV(it) }
            is TextFieldDef -> text?.let { SetupValue.TextV(it) }
            // 未知キー: 入っている列から素直に読む
            null -> when {
                text != null -> SetupValue.TextV(text)
                num != null -> SetupValue.DecimalV(num)
                else -> null
            }
        }
    }
}
