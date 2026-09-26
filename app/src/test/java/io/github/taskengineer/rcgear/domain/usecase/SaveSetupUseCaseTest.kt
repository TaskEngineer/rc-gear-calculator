package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.domain.calculator.GearCalculator
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.fake.FakeCalculationHistoryRepository
import io.github.taskengineer.rcgear.fake.FakeSetupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SaveSetupUseCase] のテスト（REF-3 / S-6）。
 *
 * この UseCase の存在理由は「保存 + 履歴記録」を 1 つにまとめて、
 * ViewModel に副作用を知らせないこと。テストで固定したいのもそこ:
 *  - 却下したときは **どちらにも書かない**（名前が空 / 同名）
 *  - 受理したときは **両方に書く**
 *  - 名前の前後空白はトリムされて保存される
 *
 * メソッド名のプレフィクスでカテゴリを表現 (success_, reject_, history_)。
 */
class SaveSetupUseCaseTest {

    private lateinit var setupRepository: FakeSetupRepository
    private lateinit var historyRepository: FakeCalculationHistoryRepository
    private lateinit var useCase: SaveSetupUseCase

    @Before
    fun setUp() {
        setupRepository = FakeSetupRepository()
        historyRepository = FakeCalculationHistoryRepository()
        useCase = SaveSetupUseCase(setupRepository, historyRepository)
    }

    // ----- success_ -----

    @Test
    fun `success_保存すると採番された id が返る`() = runTest {
        val result = save(name = "Rd1")

        assertTrue(result is SaveSetupUseCase.Result.Success)
        assertEquals("Rd1", setupRepository.stored.single().name)
    }

    @Test
    fun `success_名前の前後の空白はトリムされる`() = runTest {
        save(name = "  Rd1  ")

        assertEquals("Rd1", setupRepository.stored.single().name)
    }

    @Test
    fun `success_計算に使った内部減速比がスナップショットとして凍結される`() = runTest {
        // 保存後にシャーシDB側が変わっても、この値は動かないことが前提の設計。
        save(name = "Rd1", input = input(internalRatio = 2.6))

        assertEquals(2.6, setupRepository.stored.single().internalRatioSnapshot, 1e-9)
    }

    // ----- reject_ -----

    @Test
    fun `reject_空白のみの名前は BlankName で何も書かれない`() = runTest {
        val result = save(name = "   ")

        assertEquals(SaveSetupUseCase.Result.BlankName, result)
        assertTrue(setupRepository.stored.isEmpty())
        assertTrue("却下したのに履歴だけ残っている", historyRepository.records.isEmpty())
    }

    @Test
    fun `reject_同名が既にあると DuplicateName で何も書かれない`() = runTest {
        save(name = "Rd1")
        historyRepository.records.clear()

        val result = save(name = "Rd1")

        assertEquals(SaveSetupUseCase.Result.DuplicateName, result)
        assertEquals(1, setupRepository.stored.size)
        assertTrue("却下したのに履歴だけ残っている", historyRepository.records.isEmpty())
    }

    @Test
    fun `reject_同名判定はトリム後の名前で行う`() = runTest {
        save(name = "Rd1")

        assertEquals(SaveSetupUseCase.Result.DuplicateName, save(name = "  Rd1 "))
    }

    // ----- history_ -----

    @Test
    fun `history_保存に成功すると計算履歴も1件記録される`() = runTest {
        val input = input(pinion = 24, spur = 90)
        save(name = "Rd1", input = input)

        val record = historyRepository.records.single()
        assertEquals("tamiya_tt02", record.chassisId)
        assertEquals(24, record.pinion)
        assertEquals(90, record.spur)
        assertEquals(GearCalculator.calculate(input).topSpeedKmh, record.topSpeedKmh, 1e-9)
    }

    // ----- ヘルパー -----

    private suspend fun save(
        name: String,
        chassisId: String = "tamiya_tt02",
        input: GearCalculationInput = input()
    ) = useCase(name, chassisId, input, GearCalculator.calculate(input))

    private fun input(
        pinion: Int = 22,
        spur: Int = 84,
        internalRatio: Double = 2.6,
        kv: Int = 6500,
        cells: Int = 2,
        tireMm: Int = 63
    ) = GearCalculationInput(
        pinion = pinion,
        spur = spur,
        internalRatio = internalRatio,
        kv = kv,
        cells = cells,
        tireMm = tireMm
    )
}
