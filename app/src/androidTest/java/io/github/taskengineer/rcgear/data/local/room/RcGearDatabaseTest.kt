package io.github.taskengineer.rcgear.data.local.room

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.taskengineer.rcgear.data.local.room.entity.ChassisOverrideEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SavedSetupEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room の DAO テスト（S-10: instrumented テストの足場）。
 *
 * これまで androidTest ディレクトリは空で、`app/schemas/` のエクスポートと
 * assets 登録だけが先行していた。Phase 2 で Room を v2 に作り替えるにあたり、
 * 「DAO を実 SQLite に対して動かせる」状態を先に用意しておく。
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

    @Test
    fun `savedSetup_挿入した内容がそのまま読み出せる`() = runTest {
        val dao = db.savedSetupDao()
        val id = dao.insert(setupEntity(name = "Rd1"))

        val loaded = dao.getById(id)

        assertEquals("Rd1", loaded?.name)
        assertEquals(22, loaded?.pinion)
        assertEquals(2.6, loaded?.internalRatioSnapshot ?: 0.0, 1e-9)
    }

    @Test
    fun `savedSetup_一括挿入は1トランザクションで全件入る`() = runTest {
        // BUG-3 で追加した insertAll の動作確認
        val dao = db.savedSetupDao()
        dao.insertAll(List(3) { setupEntity(name = "S$it") })

        assertEquals(3, dao.observeAll().first().size)
    }

    @Test
    fun `savedSetup_一括挿入が制約違反で失敗すると全件ロールバックされる`() = runTest {
        // BUG-3 の本命。「1 トランザクション」は全件入ることではなく、
        // 途中で落ちたときに半端に残らないことに意味がある（Phase 0 レビュー指摘）。
        val dao = db.savedSetupDao()
        dao.insert(setupEntity(name = "既存"))

        val conflictingEntities = listOf(
            setupEntity(name = "新規1"),
            setupEntity(name = "既存"), // name の UNIQUE 制約に違反する
            setupEntity(name = "新規2")
        )

        var thrown: Throwable? = null
        try {
            dao.insertAll(conflictingEntities)
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }

        assertNotNull("UNIQUE 違反が例外にならなかった", thrown)
        // 違反行の前後にある「新規1」「新規2」も入っていないこと
        assertEquals(listOf("既存"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun `savedSetup_updatedAtの降順で並ぶ`() = runTest {
        val dao = db.savedSetupDao()
        dao.insert(setupEntity(name = "古い", updatedAt = 100L))
        dao.insert(setupEntity(name = "新しい", updatedAt = 300L))
        dao.insert(setupEntity(name = "中間", updatedAt = 200L))

        assertEquals(
            listOf("新しい", "中間", "古い"),
            dao.observeAll().first().map { it.name }
        )
    }

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

    private fun setupEntity(
        name: String,
        updatedAt: Long = 1L
    ) = SavedSetupEntity(
        name = name,
        chassisId = "tamiya_tt02",
        pinion = 22,
        spur = 84,
        internalRatioSnapshot = 2.6,
        kv = 6500,
        cells = 2,
        tireMm = 63,
        createdAt = 1L,
        updatedAt = updatedAt
    )
}
