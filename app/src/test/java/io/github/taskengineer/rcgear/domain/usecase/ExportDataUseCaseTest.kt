package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.JsonBackupCodec
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.fake.FakeCarRepository
import io.github.taskengineer.rcgear.fake.FakeChassisRepository
import io.github.taskengineer.rcgear.fake.FakeIdGenerator
import io.github.taskengineer.rcgear.fake.FakeSetupSheetRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExportDataUseCase] のテスト（REF-3 / S-6、M-6 で v2 化）。
 *
 * 一番守りたいのは **エクスポートとインポートが往復すること**。
 * 片方だけ直してもう片方を忘れる、という壊れ方が一番起きやすく、
 * しかもユーザーから見ると「バックアップしたのに戻せない」という最悪の症状になる。
 * そのため往復テストを置いて、両 UseCase を同時に縛る。
 *
 * メソッド名のプレフィクスでカテゴリを表現 (export_, roundtrip_)。
 */
class ExportDataUseCaseTest {

    private val codec = JsonBackupCodec(FakeIdGenerator())
    private val fixedTime = TimeProvider { EXPORTED_AT }

    private val sheets = FakeSetupSheetRepository()
    private val cars = FakeCarRepository(sheetRepository = sheets)
    private val chassis = FakeChassisRepository()

    // ----- export_ -----

    @Test
    fun `export_空のDBでも有効な JSON を書き出す`() = runTest {
        val json = export()

        assertTrue(json.contains("\"schemaVersion\": 2"))
        assertTrue(json.contains("\"exportedAt\": $EXPORTED_AT"))
    }

    @Test
    fun `export_書き出し時刻は TimeProvider の値になる`() = runTest {
        // 壁時計を直接読んでいたらこの値にはならない
        assertTrue(export().contains("\"exportedAt\": $EXPORTED_AT"))
    }

    @Test
    fun `export_車とシートの UUID は書き出される`() = runTest {
        // v1 は端末固有の自動採番 id だったので落としていた。
        // UUID は端末固有ではないので出す = 取り込みが id upsert になり冪等になる
        val carId = cars.createCar("TT-02 #1", "tamiya_tt02")
        val sheetId = sheets.createSheet(carId, "Rd1", SetupValues.EMPTY)

        val json = export()

        assertTrue(json.contains("\"$carId\""))
        assertTrue(json.contains("\"$sheetId\""))
    }

    @Test
    fun `export_アーカイブした車も書き出す`() = runTest {
        // 隠しているだけで消したわけではない
        val carId = cars.createCar("引退", "tamiya_tt02")
        cars.updateCar(cars.getCar(carId)!!.copy(isArchived = true))

        assertTrue(export().contains("\"引退\""))
    }

    // ----- roundtrip_ -----

    @Test
    fun `roundtrip_エクスポートした JSON をインポートすると同じ内容が復元される`() = runTest {
        val carId = cars.createCar("TT-02 #1", "tamiya_tt02")
        sheets.createSheet(
            carId,
            "Rd1",
            SetupValues.of(
                "pinion" to SetupValue.IntV(22),
                "front.camberDeg" to SetupValue.DecimalV(-1.5)
            )
        )
        chassis.overrideChassis("tamiya_tt02", internalRatio = 2.7, defaultTireMm = null, note = null)

        val json = export()

        // まっさらな DB に戻す
        val restoredSheets = FakeSetupSheetRepository()
        val restoredCars = FakeCarRepository(sheetRepository = restoredSheets)
        val restoredChassis = FakeChassisRepository()
        val result = ImportDataUseCase(restoredCars, restoredSheets, restoredChassis, codec)(json)

        assertEquals(
            ImportDataUseCase.Result.Success(
                importedCars = 1,
                importedSheets = 1,
                importedOverrides = 1
            ),
            result
        )
        assertEquals(cars.getAllOnce(), restoredCars.getAllOnce())
        assertEquals(sheets.getAllOnce(), restoredSheets.getAllOnce())
        assertEquals(chassis.storedOverrides, restoredChassis.storedOverrides)
    }

    @Test
    fun `roundtrip_同じファイルを 2 回取り込んでも増えない`() = runTest {
        // id による upsert なので冪等。v1 の「同名スキップ」はリネームで往復不能だった
        val carId = cars.createCar("TT-02 #1", "tamiya_tt02")
        sheets.createSheet(carId, "Rd1", SetupValues.EMPTY)
        val json = export()

        val importer = ImportDataUseCase(cars, sheets, chassis, codec)
        importer(json)
        importer(json)

        assertEquals(1, cars.getAllOnce().size)
        assertEquals(1, sheets.getAllOnce().size)
    }

    @Test
    fun `roundtrip_2回書き出した JSON は同一になる`() = runTest {
        val carId = cars.createCar("TT-02 #1", "tamiya_tt02")
        sheets.createSheet(carId, "Rd1", SetupValues.of("pinion" to SetupValue.IntV(22)))

        assertEquals(export(), export())
    }

    // ----- ヘルパー -----

    private suspend fun export(): String =
        ExportDataUseCase(cars, sheets, chassis, codec, fixedTime)()

    private companion object {
        const val EXPORTED_AT = 1_750_000_000_000L
    }
}
