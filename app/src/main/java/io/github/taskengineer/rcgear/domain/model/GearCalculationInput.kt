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
        require(pinion >= MIN_PINION) { "pinion must be >= $MIN_PINION but was $pinion" }
        require(pinion <= MAX_PINION) { "pinion must be <= $MAX_PINION but was $pinion" }
        require(spur >= MIN_SPUR) { "spur must be >= $MIN_SPUR but was $spur" }
        require(spur <= MAX_SPUR) { "spur must be <= $MAX_SPUR but was $spur" }
        // 内部減速比は正の値。1.0 はベルト直結シャーシなどで実在する値。
        require(internalRatio > 0.0) { "internalRatio must be > 0 but was $internalRatio" }
        require(kv >= MIN_KV) { "kv must be >= $MIN_KV but was $kv" }
        require(kv <= MAX_KV) { "kv must be <= $MAX_KV but was $kv" }
        require(cells in MIN_CELLS..MAX_CELLS) {
            "cells must be in $MIN_CELLS..$MAX_CELLS but was $cells"
        }
        require(tireMm >= MIN_TIRE_MM) { "tireMm must be >= $MIN_TIRE_MM but was $tireMm" }
        require(tireMm <= MAX_TIRE_MM) { "tireMm must be <= $MAX_TIRE_MM but was $tireMm" }
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
    }
}