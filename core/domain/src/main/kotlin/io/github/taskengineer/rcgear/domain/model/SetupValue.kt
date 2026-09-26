package io.github.taskengineer.rcgear.domain.model

/**
 * セッティングシートの 1 項目の値（M-2）。
 *
 * 項目そのものの定義は `:core:domain` の `schema/TouringSetupSchema` にあり、
 * ここはその「入れ物」だけを受け持つ。保存は `(sheetId, fieldKey)` の bag（EAV）で、
 * DB では [IntV] / [DecimalV] / [BoolV] が `num` 列、[ChoiceV] / [TextV] が `text` 列に入る。
 *
 * **bag は常に絶対値だけを持ち、シャーシ DB を参照しない。**
 * よって全フィールドが構造的に「保存時点のスナップショット」になり、
 * 旧 `internalRatioSnapshot` のような専用カラムは不要になる（計画 §4.5）。
 */
sealed interface SetupValue {

    /** 整数。ピニオン歯数・ピストン穴数など */
    data class IntV(val value: Int) : SetupValue

    /** 小数。キャンバー角・車高など */
    data class DecimalV(val value: Double) : SetupValue

    /** ON / OFF */
    data class BoolV(val value: Boolean) : SetupValue

    /**
     * 選択肢。保持するのは表示名ではなく永続キー
     * （`ChoiceOption.key`）。表示名を変えてもデータが壊れないようにするため。
     */
    data class ChoiceV(val key: String) : SetupValue

    /** 自由入力。銘柄名・ボディ名など */
    data class TextV(val value: String) : SetupValue

    companion object {

        /**
         * 数値を項目の小数桁に合わせて [IntV] / [DecimalV] に振り分ける。
         * エクスポート JSON からの復元（M-6）のように、型が数値としか分からない場面で使う。
         */
        fun number(value: Double, decimals: Int): SetupValue =
            if (decimals == 0) IntV(value.toInt()) else DecimalV(value)
    }
}
