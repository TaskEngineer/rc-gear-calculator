package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.ChassisDefaults
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.initialValues
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 車とシートの Repository が満たすべき契約（M-4）。
 *
 * ここで検証しているのは Fake だが、**Fake が本物の制約を再現していること**自体が
 * 目的になっている。Fake が緩いと、それに乗った ViewModel のテストが
 * 「本物なら落ちる書き方」を通してしまう（S-6 で同名制約を再現したのと同じ理由）。
 *
 * 本物の SQLite でしか確かめられないもの（CASCADE / SET NULL / 複合主キー）は
 * `app/src/androidTest` の `RcGearDatabaseTest` が見る。ここはその写し。
 */
class RepositoryContractTest {

    private val sheets = FakeSetupSheetRepository()
    private val cars = FakeCarRepository(sheetRepository = sheets)

    // ----- 車 -----

    @Test
    fun `車は同じ名前で 2 台作れる`() = runTest {
        // 旧 saved_setups は name に UNIQUE があった。車には持ち込まない
        cars.createCar("TA08", "tamiya_ta08")
        cars.createCar("TA08", "tamiya_ta08")

        assertEquals(2, cars.observeCars().first().size)
    }

    @Test
    fun `一覧は更新の新しい順`() = runTest {
        cars.now = 100L
        cars.createCar("古い", "tamiya_tt02")
        cars.now = 300L
        cars.createCar("新しい", "tamiya_ta08")

        assertEquals(listOf("新しい", "古い"), cars.observeCars().first().map { it.name })
    }

    @Test
    fun `アーカイブした車は既定の一覧から外れる`() = runTest {
        val id = cars.createCar("引退", "tamiya_tt02")
        cars.updateCar(cars.getCar(id)!!.copy(isArchived = true))

        assertTrue(cars.observeCars().first().isEmpty())
        assertEquals(1, cars.observeCars(includeArchived = true).first().size)
    }

    @Test
    fun `更新しても作成日時は動かない`() = runTest {
        cars.now = 100L
        val id = cars.createCar("1号車", "tamiya_tt02")
        cars.now = 500L
        cars.updateCar(cars.getCar(id)!!.copy(name = "改名"))

        val updated = cars.getCar(id)!!
        assertEquals(100L, updated.createdAt)
        assertEquals(500L, updated.updatedAt)
    }

    @Test
    fun `車を消すとシートも消える`() = runTest {
        val carId = cars.createCar("1号車", "tamiya_tt02")
        sheets.createSheet(carId, "Rd1", SetupValues.EMPTY)

        cars.deleteCar(carId)

        assertTrue(sheets.observeSheets(carId).first().isEmpty())
    }

    // ----- シート -----

    @Test
    fun `シートは走行日の新しい順で日付なしは後ろ`() = runTest {
        val carId = cars.createCar("1号車", "tamiya_tt02")
        val old = sheets.createSheet(carId, "古い", SetupValues.EMPTY)
        sheets.createSheet(carId, "日付なし", SetupValues.EMPTY)
        val new = sheets.createSheet(carId, "新しい", SetupValues.EMPTY)
        sheets.setSessionDate(old, 100L)
        sheets.setSessionDate(new, 300L)

        assertEquals(
            listOf("新しい", "古い", "日付なし"),
            sheets.observeSheets(carId).first().map { it.name }
        )
    }

    @Test
    fun `新規シートにはシャーシ標準の値が焼き込まれる`() = runTest {
        val carId = cars.createCar("1号車", "tamiya_tt02")
        val id = sheets.createSheet(
            carId,
            "Rd1",
            TouringSetupSchema.initialValues(ChassisDefaults(internalRatio = 2.6, defaultTireMm = 63))
        )

        val values = sheets.getSheet(id)!!.values
        assertEquals(2.6, values.decimalOf("internalRatio")!!, 1e-9)
        assertEquals(63, values.intOf("tireMm"))
    }

    @Test
    fun `1 項目だけ書き換えても他の値は残る`() = runTest {
        val id = sheetWithGear()

        sheets.setValue(id, "pinion", SetupValue.IntV(28))

        val values = sheets.getSheet(id)!!.values
        assertEquals(28, values.intOf("pinion"))
        assertEquals(84, values.intOf("spur"))
    }

    @Test
    fun `null を入れると空欄になる`() = runTest {
        val id = sheetWithGear()

        sheets.setValue(id, "pinion", null)

        // 「null が入った行」ではなくキーごと消える
        assertFalse("pinion" in sheets.getSheet(id)!!.values)
    }

    @Test
    fun `値を書き換えるとシートの更新日時が進む`() = runTest {
        val id = sheetWithGear()
        sheets.now = 9_999L

        sheets.setValue(id, "pinion", SetupValue.IntV(28))

        assertEquals(9_999L, sheets.getSheet(id)!!.sheet.updatedAt)
    }

    @Test
    fun `replaceValues は渡されなかったキーを落とす`() = runTest {
        val id = sheetWithGear()

        sheets.replaceValues(id, SetupValues.of("pinion" to SetupValue.IntV(30)))

        val values = sheets.getSheet(id)!!.values
        assertEquals(1, values.size)
        assertEquals(30, values.intOf("pinion"))
    }

    @Test
    fun `未知キーも保存され読み戻せる`() = runTest {
        // スキーマを安全に進化させる担保。将来の項目が入ったデータを読んでも消えない
        val carId = cars.createCar("1号車", "tamiya_tt02")
        val id = sheets.createSheet(
            carId,
            "Rd1",
            SetupValues.of("future.newField" to SetupValue.TextV("将来の値"))
        )

        assertEquals("将来の値", sheets.getSheet(id)!!.values.textOf("future.newField"))
    }

    @Test
    fun `ベースラインのシートを消しても本体は残る`() = runTest {
        val carId = cars.createCar("1号車", "tamiya_tt02")
        val base = sheets.createSheet(carId, "Rd1", SetupValues.EMPTY)
        val current = sheets.createSheet(carId, "Rd2", SetupValues.EMPTY, baselineId = base)

        sheets.deleteSheet(base)

        val remaining = sheets.getSheet(current)
        assertNotNull("本体まで消えている", remaining)
        assertNull("ベースラインの参照が残っている", remaining?.sheet?.baselineId)
    }

    // ----- 復元（インポート） -----

    @Test
    fun `restoreAll は id で upsert するので 2 回読んでも増えない`() = runTest {
        val carId = cars.createCar("1号車", "tamiya_tt02")
        val id = sheetWithGear(carId)
        val snapshot: List<SetupSheetWithValues> = sheets.getAllOnce()
        val carSnapshot = cars.getAllOnce()

        cars.restoreAll(carSnapshot)
        sheets.restoreAll(snapshot)
        cars.restoreAll(carSnapshot)
        sheets.restoreAll(snapshot)

        assertEquals(1, cars.getAllOnce().size)
        assertEquals(1, sheets.getAllOnce().size)
        assertEquals(22, sheets.getSheet(id)!!.values.intOf("pinion"))
    }

    @Test
    fun `restoreAll は元の時刻を保つ`() = runTest {
        val car = cars.getAllOnce()
        assertTrue(car.isEmpty())
        cars.now = 100L
        val carId = cars.createCar("1号車", "tamiya_tt02")
        val stored = cars.getCar(carId)!!

        val fresh = FakeCarRepository()
        fresh.now = 999L
        fresh.restoreAll(listOf(stored))

        assertEquals(100L, fresh.getCar(carId)!!.createdAt)
    }

    // ----- ヘルパー -----

    private suspend fun sheetWithGear(carId: String? = null): String = sheets.createSheet(
        carId ?: cars.createCar("1号車", "tamiya_tt02"),
        "Rd1",
        SetupValues.of(
            "pinion" to SetupValue.IntV(22),
            "spur" to SetupValue.IntV(84)
        )
    )

    private suspend fun FakeSetupSheetRepository.setSessionDate(id: String, date: Long) {
        val sheet = getSheet(id)!!.sheet
        updateSheet(sheet.copy(conditions = sheet.conditions.copy(sessionDate = date)))
    }
}
