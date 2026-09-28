package io.github.taskengineer.rcgear.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.taskengineer.rcgear.data.local.room.RcGearDatabase
import io.github.taskengineer.rcgear.data.local.room.entity.CarEntity
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SetupSheetRepositoryImpl] を**本物の SQLite** の上で確かめる（BUG-6）。
 *
 * Fake を使う JVM 単体テスト（`RepositoryContractTest`）は、Fake が同じ契約を
 * 再現している限りにおいて正しい。その「限りにおいて」が破れていたのが BUG-6 で、
 * Fake は消えたシートへの書き込みを黙って握り潰していたのに、本物は
 * `setup_values.sheetId` の外部キー違反で `SQLiteConstraintException` を投げ、
 * アプリごと落ちていた。ここは本物側の振る舞いを直接押さえる。
 *
 * 実行にはエミュレータ/実機が必要:
 *   .\gradlew.bat :app:connectedDebugAndroidTest
 *
 * メソッド名にスペースを入れないこと（minSdk 26 の DEX 制約。`RcGearDatabaseTest` 参照）。
 */
@RunWith(AndroidJUnit4::class)
class SetupSheetRepositoryImplTest {

    private lateinit var db: RcGearDatabase
    private lateinit var repository: SetupSheetRepositoryImpl

    /** 時刻は固定。updatedAt が進んだかを見たいときだけ動かす */
    private var now: Long = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RcGearDatabase::class.java
        ).build()
        var seed = 0
        repository = SetupSheetRepositoryImpl(
            db = db,
            sheetDao = db.setupSheetDao(),
            valueDao = db.setupValueDao(),
            timeProvider = { now },
            idGenerator = { "sheet-${seed++}" }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ----- 正常系（土台が効いていることの確認） -----

    @Test
    fun `setValue_書けたらtrueを返し値が読み戻せる`() = runTest {
        val sheetId = prepareSheet()

        val written = repository.setValue(sheetId, "pinion", SetupValue.IntV(28))

        assertTrue(written)
        assertEquals(28, repository.getSheet(sheetId)?.values?.intOf("pinion"))
    }

    @Test
    fun `setValue_値を書くとシートの更新日時が進む`() = runTest {
        val sheetId = prepareSheet()
        now = 9_999L

        repository.setValue(sheetId, "pinion", SetupValue.IntV(28))

        assertEquals(9_999L, repository.getSheet(sheetId)?.sheet?.updatedAt)
    }

    // ----- BUG-6: 消えたシートへの書き込み -----

    @Test
    fun `setValue_シートが消えていても落ちずにfalseを返す`() = runTest {
        // CALC のバナーが消えたシートを指したまま「反映」を押した状況。
        // 修正前はここで SQLiteConstraintException が飛んでアプリが落ちた
        val sheetId = prepareSheet()
        repository.deleteSheet(sheetId)

        val written = repository.setValue(sheetId, "pinion", SetupValue.IntV(28))

        assertFalse("消えたシートに書けてしまっている", written)
        assertNull(repository.getSheet(sheetId))
    }

    @Test
    fun `setValue_車ごと消えていても落ちずにfalseを返す`() = runTest {
        // CONFIG の「全データ削除」と車の削除はここを通る（cars → setup_sheets の CASCADE）。
        // シートを名指しで消す経路とは別なので分けて押さえる
        val sheetId = prepareSheet()
        db.carDao().deleteById(CAR_ID)

        assertFalse(repository.setValue(sheetId, "pinion", SetupValue.IntV(28)))
    }

    @Test
    fun `setValue_空欄に戻す場合もシートが無ければfalse`() = runTest {
        // value = null は DELETE なので外部キー違反にはならないが、
        // 「書けたか」の答えは同じでないと呼び出し側が分岐できない
        val sheetId = prepareSheet()
        repository.setValue(sheetId, "pinion", SetupValue.IntV(28))
        repository.deleteSheet(sheetId)

        assertFalse(repository.setValue(sheetId, "pinion", null))
    }

    @Test
    fun `replaceValues_シートが消えていても落ちずにfalseを返す`() = runTest {
        val sheetId = prepareSheet()
        repository.deleteSheet(sheetId)

        val written = repository.replaceValues(
            sheetId,
            SetupValues.of("pinion" to SetupValue.IntV(28))
        )

        assertFalse(written)
        assertNull(repository.getSheet(sheetId))
    }

    @Test
    fun `setValue_失敗しても他のシートには影響しない`() = runTest {
        // トランザクションが中途半端に効いて巻き添えを出していないかを見る
        val gone = prepareSheet()
        val alive = repository.createSheet(CAR_ID, "Rd2", SetupValues.EMPTY)
        repository.deleteSheet(gone)

        assertFalse(repository.setValue(gone, "pinion", SetupValue.IntV(28)))
        assertTrue(repository.setValue(alive, "pinion", SetupValue.IntV(30)))
        assertEquals(30, repository.getSheet(alive)?.values?.intOf("pinion"))
    }

    // ----- ヘルパー -----

    /** 車 1 台とそのシート 1 枚を作り、シート id を返す */
    private suspend fun prepareSheet(): String {
        db.carDao().insert(
            CarEntity(
                id = CAR_ID,
                name = "1号車",
                chassisId = "tamiya_tt02",
                note = null,
                isArchived = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        return repository.createSheet(CAR_ID, "Rd1", SetupValues.EMPTY)
    }

    private companion object {
        const val CAR_ID = "car1"
    }
}
