package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.CELLS_RANGE
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.KV_RANGE
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MAX_CELLS
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MAX_KV
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MAX_PINION
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MAX_SPUR
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MAX_TIRE_MM
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MIN_CELLS
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MIN_KV
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MIN_PINION
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MIN_SPUR
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.MIN_TIRE_MM
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.PINION_RANGE
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.SPUR_RANGE
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.TIRE_MM_RANGE
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.clampCells
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.clampKv
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.clampPinion
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.clampSpur
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.clampTireMm
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.isValid
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput.Companion.isValidInternalRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GearCalculationInput] の範囲判定・クランプのテスト（REF-1）。
 *
 * ここで守りたいのは「範囲の定義がこのクラス 1 箇所に集約されている」こと。
 * 呼び出し側（ChassisEditViewModel / ImportDataUseCase / CalcViewModel）が
 * 自前で min/max を書くと必ずズレるため、境界値をここで固定する。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (range_, clamp_, isValid_, ctor_)。
 */
class GearCalculationInputTest {

    // ----- range_: 範囲定数が MIN/MAX と一致していること -----

    @Test
    fun `range_各レンジは対応する MIN・MAX 定数と一致する`() {
        assertEquals(MIN_PINION..MAX_PINION, PINION_RANGE)
        assertEquals(MIN_SPUR..MAX_SPUR, SPUR_RANGE)
        assertEquals(MIN_KV..MAX_KV, KV_RANGE)
        assertEquals(MIN_CELLS..MAX_CELLS, CELLS_RANGE)
        assertEquals(MIN_TIRE_MM..MAX_TIRE_MM, TIRE_MM_RANGE)
    }

    @Test
    fun `range_デフォルト値はすべて有効範囲内にある`() {
        assertTrue(GearCalculationInput.DEFAULT_PINION in PINION_RANGE)
        assertTrue(GearCalculationInput.DEFAULT_SPUR in SPUR_RANGE)
        assertTrue(GearCalculationInput.DEFAULT_KV in KV_RANGE)
        assertTrue(GearCalculationInput.DEFAULT_CELLS in CELLS_RANGE)
        assertTrue(GearCalculationInput.DEFAULT_TIRE_MM in TIRE_MM_RANGE)
    }

    // ----- clamp_: 境界の内・外・ちょうど -----

    @Test
    fun `clamp_範囲内の値はそのまま返る`() {
        assertEquals(22, clampPinion(22))
        assertEquals(84, clampSpur(84))
        assertEquals(6500, clampKv(6500))
        assertEquals(2, clampCells(2))
        assertEquals(63, clampTireMm(63))
    }

    @Test
    fun `clamp_境界値ちょうどはそのまま返る`() {
        assertEquals(MIN_PINION, clampPinion(MIN_PINION))
        assertEquals(MAX_PINION, clampPinion(MAX_PINION))
        assertEquals(MIN_TIRE_MM, clampTireMm(MIN_TIRE_MM))
        assertEquals(MAX_TIRE_MM, clampTireMm(MAX_TIRE_MM))
    }

    @Test
    fun `clamp_下限未満は下限に丸められる`() {
        assertEquals(MIN_PINION, clampPinion(MIN_PINION - 1))
        assertEquals(MIN_PINION, clampPinion(0))
        assertEquals(MIN_SPUR, clampSpur(-100))
        assertEquals(MIN_KV, clampKv(0))
        assertEquals(MIN_CELLS, clampCells(0))
        assertEquals(MIN_TIRE_MM, clampTireMm(1))
    }

    @Test
    fun `clamp_上限超過は上限に丸められる`() {
        assertEquals(MAX_PINION, clampPinion(MAX_PINION + 1))
        assertEquals(MAX_SPUR, clampSpur(9999))
        assertEquals(MAX_KV, clampKv(99999))
        assertEquals(MAX_CELLS, clampCells(10))
        // BUG-1 の再現値: DB 画面でタイヤ径 200 を保存しても CALC は落ちない
        assertEquals(MAX_TIRE_MM, clampTireMm(200))
    }

    // ----- isValid_ -----

    @Test
    fun `isValid_内部減速比は正の有限値のみ有効`() {
        assertTrue(isValidInternalRatio(2.6))
        // ベルト直結シャーシで実在する値
        assertTrue(isValidInternalRatio(1.0))
        assertFalse(isValidInternalRatio(0.0))
        assertFalse(isValidInternalRatio(-1.0))
        assertFalse(isValidInternalRatio(Double.NaN))
        assertFalse(isValidInternalRatio(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `isValid_全項目が範囲内なら true`() {
        assertTrue(isValid(pinion = 22, spur = 84, internalRatio = 2.6, kv = 6500, cells = 2, tireMm = 63))
    }

    @Test
    fun `isValid_いずれか1項目でも範囲外なら false`() {
        // BUG-2 の再現値: エクスポート JSON の pinion を 5 に書き換えたケース
        assertFalse(isValid(pinion = 5, spur = 84, internalRatio = 2.6, kv = 6500, cells = 2, tireMm = 63))
        assertFalse(isValid(pinion = 22, spur = 10, internalRatio = 2.6, kv = 6500, cells = 2, tireMm = 63))
        assertFalse(isValid(pinion = 22, spur = 84, internalRatio = 0.0, kv = 6500, cells = 2, tireMm = 63))
        assertFalse(isValid(pinion = 22, spur = 84, internalRatio = 2.6, kv = 100, cells = 2, tireMm = 63))
        assertFalse(isValid(pinion = 22, spur = 84, internalRatio = 2.6, kv = 6500, cells = 0, tireMm = 63))
        assertFalse(isValid(pinion = 22, spur = 84, internalRatio = 2.6, kv = 6500, cells = 2, tireMm = 200))
    }

    @Test
    fun `isValid_境界値ちょうどは有効`() {
        assertTrue(
            isValid(
                pinion = MIN_PINION,
                spur = MIN_SPUR,
                internalRatio = 0.001,
                kv = MIN_KV,
                cells = MIN_CELLS,
                tireMm = MIN_TIRE_MM
            )
        )
        assertTrue(
            isValid(
                pinion = MAX_PINION,
                spur = MAX_SPUR,
                internalRatio = 100.0,
                kv = MAX_KV,
                cells = MAX_CELLS,
                tireMm = MAX_TIRE_MM
            )
        )
    }

    // ----- ctor_: isValid と コンストラクタの判定が一致していること -----

    @Test
    fun `ctor_isValid が true の値はコンストラクタを通る`() {
        // isValid を通したのに init で落ちる、という食い違いがないことを保証する
        val input = GearCalculationInput(
            pinion = MIN_PINION,
            spur = MAX_SPUR,
            internalRatio = 1.0,
            kv = MAX_KV,
            cells = MIN_CELLS,
            tireMm = MAX_TIRE_MM
        )
        assertEquals(MIN_PINION, input.pinion)
    }
}
