package io.github.taskengineer.rcgear.domain.model

/**
 * ギア比計算の入力パラメータ。
 *
 * Web版（index.html）の `update()` 関数で使われていた入力値に対応する。
 * すべて Int / Double の値型のみを保持する単純な immutable データクラス。
 *
 * 妥当性チェック（init ブロック）について:
 *   - UI 側のスライダーで min/max を制限していても、JSON インポートや
 *     保存値の読み込みなど別経路で生成される可能性があるため、ドメイン層で
 *     値の範囲をガードしておく。範囲外なら IllegalArgumentException を投げる。
 *   - スライダーの上下限は Web 版に合わせている（index.html L426 付近）。
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
        // ピニオンは 1 以上。0 だと spur ÷ pinion でゼロ除算になる。
        require(pinion in PINION_RANGE) { "pinion must be in $PINION_RANGE but was $pinion" }
        require(spur in SPUR_RANGE) { "spur must be in $SPUR_RANGE but was $spur" }
        // 内部減速比は正の値。1.0 はベルト直結シャーシなどで実在する値。
        require(isValidInternalRatio(internalRatio)) {
            "internalRatio must be > 0 but was $internalRatio"
        }
        require(kv in KV_RANGE) { "kv must be in $KV_RANGE but was $kv" }
        require(cells in CELLS_RANGE) { "cells must be in $CELLS_RANGE but was $cells" }
        require(tireMm in TIRE_MM_RANGE) { "tireMm must be in $TIRE_MM_RANGE but was $tireMm" }
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
        // このコンストラクタは範囲外で例外を投げる。しかし値の供給元は
        // スライダーだけではなく、シャーシ DB の上書き（BUG-1）・エクスポート
        // JSON のインポート（BUG-2）・DataStore に残った過去の値 もある。
        // 各呼び出し側が自前で min/max を書くと必ずズレるため、範囲の定義と
        // 判定・クランプはここに集約する。

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
