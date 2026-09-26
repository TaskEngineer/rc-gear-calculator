package io.github.taskengineer.rcgear.domain.model

/**
 * ギア比計算の入力パラメータ。
 *
 * Web版（index.html）の `update()` 関数で使われていた入力値に対応する。
 * すべて Int / Double の値型のみを保持する単純な immutable データクラス。
 *
 * 妥当性チェックについて（M-2 で方針変更）:
 *   - **範囲の検証はここではなく `validation/FieldValidator` が持つ。**
 *     範囲を知っているのはレジストリ（`TouringSetupSchema`）だけ、という状態にするため。
 *     init で範囲を require していた頃は、スライダー以外の経路（シャーシ上書き /
 *     JSON インポート / DataStore の古い値）から範囲外の値が来るとアプリが落ちていた
 *     （BUG-1 / BUG-2）。セッティングシートは実測値も書ける場所なので、
 *     「スライダーの外＝存在してはいけない値」ではない。
 *   - init に残すのは **計算が成立しない値だけ**。ゼロ除算になる 2 つに限る。
 *     これにより `GearCalculator` は非 null の結果を返す全域関数のままでいられる。
 *   - 上下限の定数と clamp / isValid はここに残す。スライダーの range と
 *     レジストリのギアセクションが同じ値を指していることは `TouringSetupSchemaTest` が見る。
 */
data class GearCalculationInput(
    /** ピニオン歯数。Web版スライダー: min=14, max=40, step=1 */
    val pinion: Int,

    /** スパー歯数。Web版スライダー: min=60, max=120, step=1 */
    val spur: Int,

    /** シャーシ内部減速比。シャーシ DB から取得した値、またはユーザー上書き値 */
    val internalRatio: Double,

    /** モーター KV 値。Web版スライダー: min=1500, max=13500, step=100 */
    val kv: Int,

    /** バッテリーセル数（S 数）。Web版スライダー: min=1, max=4 */
    val cells: Int,

    /** タイヤ径[mm]。Web版スライダー: min=40, max=120, step=1 */
    val tireMm: Int
) {
    init {
        // ここに残すのは「計算式が成立しない値」だけ。範囲は FieldValidator の担当。
        // pinion が 0 だと spur ÷ pinion で、spur か internalRatio が 0 だと
        // モーターRPM ÷ FDR でゼロ除算になり、結果が Infinity / NaN になる。
        require(pinion > 0) { "pinion must be > 0 but was $pinion" }
        require(spur > 0) { "spur must be > 0 but was $spur" }
        // 内部減速比は正の有限値。1.0 はベルト直結シャーシなどで実在する値。
        require(isValidInternalRatio(internalRatio)) {
            "internalRatio must be a positive finite number but was $internalRatio"
        }
    }

    /**
     * 入力の上下限定数。UI 層（スライダーの range 設定）と Repository 層
     * （JSON インポート時の clamp 処理）から共通で参照する想定。
     * Web 版の index.html と完全に同じ値。
     */
    companion object {
        const val MIN_PINION = 14
        const val MAX_PINION = 40
        const val DEFAULT_PINION = 22

        const val MIN_SPUR = 60
        const val MAX_SPUR = 120
        const val DEFAULT_SPUR = 84

        const val MIN_KV = 1500
        const val MAX_KV = 13500
        const val KV_STEP = 100
        const val DEFAULT_KV = 6500

        const val MIN_CELLS = 1
        const val MAX_CELLS = 4
        const val DEFAULT_CELLS = 2

        const val MIN_TIRE_MM = 40
        const val MAX_TIRE_MM = 120
        const val DEFAULT_TIRE_MM = 63

        /** LiPo セル 1 本の公称電圧[V] */
        const val LIPO_CELL_VOLTAGE = 3.7

        // ----- 範囲判定・クランプ（REF-1: 入力値検証の一元化） -----
        //
        // 値の供給元はスライダーだけではなく、シャーシ DB の上書き（BUG-1）・
        // エクスポート JSON のインポート（BUG-2）・DataStore に残った過去の値もある。
        // 各呼び出し側が自前で min/max を書くと必ずズレるため、範囲の定義と
        // 判定・クランプはここに集約する。
        //
        // M-2 以降、これは「CALC 画面のスライダーが動ける範囲」を表す。
        // シートに入る値の検証はレジストリ駆動の FieldValidator が行い、
        // 両者が同じ値を指していることは TouringSetupSchemaTest が保証する。

        val PINION_RANGE: IntRange = MIN_PINION..MAX_PINION
        val SPUR_RANGE: IntRange = MIN_SPUR..MAX_SPUR
        val KV_RANGE: IntRange = MIN_KV..MAX_KV
        val CELLS_RANGE: IntRange = MIN_CELLS..MAX_CELLS
        val TIRE_MM_RANGE: IntRange = MIN_TIRE_MM..MAX_TIRE_MM

        /** 内部減速比は正の値のみ有効（上限は設けない） */
        fun isValidInternalRatio(value: Double): Boolean = value > 0.0 && value.isFinite()

        /**
         * 全フィールドが有効範囲に収まっているか。
         * インポート時の 1 行検証など「クランプではなく棄却したい」場面で使う。
         */
        fun isValid(
            pinion: Int,
            spur: Int,
            internalRatio: Double,
            kv: Int,
            cells: Int,
            tireMm: Int
        ): Boolean =
            pinion in PINION_RANGE &&
                spur in SPUR_RANGE &&
                isValidInternalRatio(internalRatio) &&
                kv in KV_RANGE &&
                cells in CELLS_RANGE &&
                tireMm in TIRE_MM_RANGE

        /**
         * 範囲外の値を有効範囲に丸める。
         * 「UI に出す値」を作る場面で使う（棄却すると操作不能になるため）。
         */
        fun clampPinion(value: Int): Int = value.coerceIn(PINION_RANGE)
        fun clampSpur(value: Int): Int = value.coerceIn(SPUR_RANGE)
        fun clampKv(value: Int): Int = value.coerceIn(KV_RANGE)
        fun clampCells(value: Int): Int = value.coerceIn(CELLS_RANGE)
        fun clampTireMm(value: Int): Int = value.coerceIn(TIRE_MM_RANGE)
    }
}
