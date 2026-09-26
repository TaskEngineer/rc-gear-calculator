package io.github.taskengineer.rcgear.data.local.room

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.taskengineer.rcgear.data.local.room.entity.CarEntity
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SetupSheetEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SetupValueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room の DAO テスト（S-10 の足場を M-3 の v2 スキーマに合わせて更新）。
 *
 * JVM 単体テストでは見えないもの — 外部キーの CASCADE / SET NULL、
 * 複合主キーの upsert、一括挿入のロールバック — をここで見る。
 *
 * 実行にはエミュレータ/実機が必要:
 *   .\gradlew.bat :app:connectedDebugAndroidTest
 *
 * 注意: instrumented テストのメソッド名に **スペースを含めてはいけない**。
 * DEX 040 未満（minSdk 26）は SimpleName の空白を許さず、dexBuilder で
 * 失敗する。JVM 単体テスト（app/src/test）はバッククォート内にスペースを
 * 置けるが、ここでは使えないので単語を詰めて書く。
 */
@RunWith(AndroidJUnit4::class)
class RcGearDatabaseTest {

    private lateinit var db: RcGearDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RcGearDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ----- cars -----

    @Test
    fun `car_挿入した内容がそのまま読み出せる`() = runTest {
        db.carDao().insert(car(id = "car1", name = "TA08 #1"))

        val loaded = db.carDao().getById("car1")

        assertEquals("TA08 #1", loaded?.name)
        assertEquals("tamiya_ta08", loaded?.chassisId)
    }

    @Test
    fun `car_同じ名前の車を2台作れる`() = runTest {
        // 旧 saved_setups は name に UNIQUE があった。車には持ち込まない
        db.carDao().insert(car(id = "car1", name = "TA08"))
        db.carDao().insert(car(id = "car2", name = "TA08"))

        assertEquals(2, db.carDao().observeActive().first().size)
    }

    @Test
    fun `car_アーカイブした車は一覧から外れる`() = runTest {
        db.carDao().insert(car(id = "car1", name = "現役"))
        db.carDao().insert(car(id = "car2", name = "引退", isArchived = true))

        assertEquals(listOf("現役"), db.carDao().observeActive().first().map { it.name })
        assertEquals(2, db.carDao().observeAll().first().size)
    }

    // ----- setup_sheets -----

    @Test
    fun `sheet_車ごとに同じ名前のシートを作れる`() = runTest {
        db.carDao().insert(car(id = "car1", name = "1号車"))
        db.carDao().insert(car(id = "car2", name = "2号車"))
        db.setupSheetDao().insert(sheet(id = "s1", carId = "car1", name = "Rd1"))
        db.setupSheetDao().insert(sheet(id = "s2", carId = "car2", name = "Rd1"))

        assertEquals(1, db.setupSheetDao().observeByCar("car1").first().size)
        assertEquals(1, db.setupSheetDao().observeByCar("car2").first().size)
    }

    @Test
    fun `sheet_走行日の新しい順に並び日付なしは後ろ`() = runTest {
        db.carDao().insert(car(id = "car1", name = "1号車"))
        db.setupSheetDao().insert(sheet(id = "s1", carId = "car1", name = "古い", sessionDate = 100L))
        db.setupSheetDao().insert(sheet(id = "s2", carId = "car1", name = "日付なし", sessionDate = null))
        db.setupSheetDao().insert(sheet(id = "s3", carId = "car1", name = "新しい", sessionDate = 300L))

        assertEquals(
            listOf("新しい", "古い", "日付なし"),
            db.setupSheetDao().observeByCar("car1").first().map { it.name }
        )
    }

    @Test
    fun `sheet_存在しない車のシートは作れない`() = runTest {
        var thrown: Throwable? = null
        try {
            db.setupSheetDao().insert(sheet(id = "s1", carId = "居ない車", name = "Rd1"))
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }

        assertNotNull("外部キーが効いていない", thrown)
    }

    @Test
    fun `sheet_車を消すとシートも消える`() = runTest {
        db.carDao().insert(car(id = "car1", name = "1号車"))
        db.setupSheetDao().insert(sheet(id = "s1", carId = "car1", name = "Rd1"))

        db.carDao().deleteById("car1")

        assertTrue(db.setupSheetDao().observeByCar("car1").first().isEmpty())
    }

    @Test
    fun `sheet_ベースラインのシートを消しても本体は残る`() = runTest {
        // SET NULL。「前回のセット」が消えても、今のシートまで消えては困る
        db.carDao().insert(car(id = "car1", name = "1号車"))
        db.setupSheetDao().insert(sheet(id = "base", carId = "car1", name = "Rd1"))
        db.setupSheetDao().insert(sheet(id = "s2", carId = "car1", name = "Rd2", baselineId = "base"))

        db.setupSheetDao().deleteById("base")

        val remaining = db.setupSheetDao().getById("s2")
        assertNotNull("本体まで消えている", remaining)
        assertNull("ベースラインの参照が残っている", remaining?.baselineId)
    }

    // ----- setup_values -----

    @Test
    fun `value_同じキーへの2回目の書き込みは置換になる`() = runTest {
        // 主キーが (sheetId, fieldKey) なので REPLACE がそのまま upsert になる
        prepareSheet()
        db.setupValueDao().upsert(value(fieldKey = "pinion", num = 22.0))
        db.setupValueDao().upsert(value(fieldKey = "pinion", num = 28.0))

        val values = db.setupValueDao().getBySheet(SHEET_ID)

        assertEquals(1, values.size)
        assertEquals(28.0, values.single().num ?: 0.0, 1e-9)
    }

    @Test
    fun `value_シートを消すと値も消える`() = runTest {
        prepareSheet()
        db.setupValueDao().upsert(value(fieldKey = "pinion", num = 22.0))

        db.setupSheetDao().deleteById(SHEET_ID)

        assertTrue(db.setupValueDao().getBySheet(SHEET_ID).isEmpty())
    }

    @Test
    fun `value_車を消すと値まで連鎖して消える`() = runTest {
        prepareSheet()
        db.setupValueDao().upsert(value(fieldKey = "pinion", num = 22.0))

        db.carDao().deleteById(CAR_ID)

        assertTrue(db.setupValueDao().getBySheet(SHEET_ID).isEmpty())
    }

    @Test
    fun `value_残すキー以外を消せる`() = runTest {
        prepareSheet()
        db.setupValueDao().upsertAll(
            listOf(
                value(fieldKey = "pinion", num = 22.0),
                value(fieldKey = "spur", num = 84.0),
                value(fieldKey = "tireMm", num = 63.0)
            )
        )

        db.setupValueDao().deleteBySheetExcept(SHEET_ID, listOf("pinion", "spur"))

        assertEquals(
            listOf("pinion", "spur"),
            db.setupValueDao().getBySheet(SHEET_ID).map { it.fieldKey }.sorted()
        )
    }

    @Test
    fun `sheet_ヘッダと値が1回のクエリで揃って取れる`() = runTest {
        prepareSheet()
        db.setupValueDao().upsertAll(
            listOf(value(fieldKey = "pinion", num = 22.0), value(fieldKey = "spur", num = 84.0))
        )

        val loaded = db.setupSheetDao().getWithValues(SHEET_ID)

        assertEquals("Rd1", loaded?.sheet?.name)
        assertEquals(2, loaded?.values?.size)
    }

    // ----- chassis_overrides（v1 から据え置き） -----

    @Test
    fun `chassisOverride_upsertは同じchassisIdを置換する`() = runTest {
        val dao = db.chassisOverrideDao()
        dao.upsert(ChassisOverrideEntity("tamiya_tt02", 2.6, 63, null, 1L))
        dao.upsert(ChassisOverrideEntity("tamiya_tt02", 2.7, null, "メモ", 2L))

        val loaded = dao.getByChassisId("tamiya_tt02")

        assertEquals(1, dao.observeAll().first().size)
        assertEquals(2.7, loaded?.internalRatio ?: 0.0, 1e-9)
        // null は「このフィールドは上書きしない」の意味。置換なので null に戻る
        assertNull(loaded?.defaultTireMm)
    }

    @Test
    fun `chassisOverride_削除すると標準値に戻る`() = runTest {
        val dao = db.chassisOverrideDao()
        dao.upsert(ChassisOverrideEntity("tamiya_tt02", 2.7, null, null, 1L))
        dao.deleteByChassisId("tamiya_tt02")

        assertNull(dao.getByChassisId("tamiya_tt02"))
    }

    @Test
    fun `chassisOverride_一括挿入は1トランザクションで全件入る`() = runTest {
        val dao = db.chassisOverrideDao()
        dao.upsertAll(List(3) { ChassisOverrideEntity("chassis$it", 2.6, 63, null, 1L) })

        assertEquals(3, dao.observeAll().first().size)
    }

    // ----- ヘルパー -----

    private suspend fun prepareSheet() {
        db.carDao().insert(car(id = CAR_ID, name = "1号車"))
        db.setupSheetDao().insert(sheet(id = SHEET_ID, carId = CAR_ID, name = "Rd1"))
    }

    private fun car(
        id: String,
        name: String,
        isArchived: Boolean = false
    ) = CarEntity(
        id = id,
        name = name,
        chassisId = "tamiya_ta08",
        note = null,
        isArchived = isArchived,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun sheet(
        id: String,
        carId: String,
        name: String,
        baselineId: String? = null,
        sessionDate: Long? = null
    ) = SetupSheetEntity(
        id = id,
        carId = carId,
        name = name,
        baselineId = baselineId,
        sessionDate = sessionDate,
        trackName = null,
        surface = null,
        airTempC = null,
        trackTempC = null,
        humidityPct = null,
        bestLapMs = null,
        note = null,
        isFavorite = false,
        schemaId = "touring",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun value(
        fieldKey: String,
        num: Double? = null,
        text: String? = null
    ) = SetupValueEntity(
        sheetId = SHEET_ID,
        fieldKey = fieldKey,
        num = num,
        text = text,
        updatedAt = 1L
    )

    private companion object {
        const val CAR_ID = "car1"
        const val SHEET_ID = "sheet1"
    }
}
