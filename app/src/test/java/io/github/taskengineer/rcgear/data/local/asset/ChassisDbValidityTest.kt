package io.github.taskengineer.rcgear.data.local.asset

import io.github.taskengineer.rcgear.data.local.asset.dto.ChassisDatabaseDto
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同梱シャーシ DB（`assets/chassis-db.json`）の妥当性（M-7 / 計画 §7.4）。
 *
 * セッティングシートの項目定義は Kotlin のレジストリにしたので、キーの誤りは
 * コンパイラが捕まえる。一方 **`chassis-db.json` は実行時パースのまま**なので、
 * 壊れても気づけるのはアプリを動かした時になる。そこで
 * `src/main/assets` を JVM 単体テストのリソースに登録し（`app/build.gradle.kts`）、
 * **出荷される現物**をここで読む。
 *
 * 一番大事なのは [`既存の 45 件の id が 1 つも変わっていない`]。
 * `id` は保存データ・上書き・エクスポート JSON の外部キーなので、改名も削除もできない
 * （AGENTS.md §4）。一覧から外したいときは `category` で絞る。
 */
class ChassisDbValidityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val db: ChassisDatabaseDto by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("chassis-db.json")) {
            "chassis-db.json が test のリソースに無い。app/build.gradle.kts の sourceSets を確認すること"
        }.use { it.reader(Charsets.UTF_8).readText() }
        json.decodeFromString<ChassisDatabaseDto>(text)
    }

    @Test
    fun `出荷される現物がパースできる`() {
        assertEquals(2, db.schemaVersion)
        assertTrue("エントリが空", db.chassis.isNotEmpty())
        assertTrue("出典が消えている", db.sources.isNotEmpty())
    }

    /**
     * **id は絶対に変えない。**
     * 保存セッティング・上書き・エクスポート JSON の外部キーなので、
     * 1 つでも消えると既存データが行き場を失う。追加は可、改名・削除は不可。
     */
    @Test
    fun `既存の 45 件の id が 1 つも変わっていない`() {
        val actual = db.chassis.map { it.id }.toSet()
        val missing = KNOWN_IDS - actual
        assertEquals("id が消えている（改名・削除は不可）: $missing", emptySet<String>(), missing)
    }

    @Test
    fun `id は一意`() {
        val ids = db.chassis.map { it.id }
        val duplicated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals("重複した id: $duplicated", emptySet<String>(), duplicated)
    }

    @Test
    fun `id はメーカー_型番のスネークケース`() {
        val invalid = db.chassis.map { it.id }.filterNot { it.matches(Regex("[a-z0-9]+(_[a-z0-9]+)+")) }
        assertEquals("命名規則から外れた id: $invalid", emptyList<String>(), invalid)
    }

    @Test
    fun `名前とメーカーが空でない`() {
        for (chassis in db.chassis) {
            assertTrue("${chassis.id}: name が空", chassis.name.isNotBlank())
            assertTrue("${chassis.id}: maker が空", chassis.maker.isNotBlank())
        }
    }

    @Test
    fun `category は既知の値`() {
        for (chassis in db.chassis) {
            val known = ChassisCategory.entries.any { it.name.equals(chassis.category, ignoreCase = true) }
            assertTrue("${chassis.id}: 未知の category '${chassis.category}'", known)
        }
    }

    @Test
    fun `drive は既知の値か省略`() {
        // 裏が取れていないものは省略する方針。書いてあるなら解釈できること
        for (chassis in db.chassis) {
            val drive = chassis.drive ?: continue
            assertNotNull("${chassis.id}: 未知の drive '$drive'", ChassisDrive.fromKey(drive))
        }
    }

    @Test
    fun `ツーリングのエントリが存在する`() {
        // ツーリング専用アプリなので、絞り込んだ結果が空になっては困る
        val touring = db.chassis.filter { it.category.equals("touring", ignoreCase = true) }
        assertTrue("ツーリングのエントリが無い", touring.size >= 10)
    }

    @Test
    fun `内部減速比は正の値`() {
        for (chassis in db.chassis) {
            assertTrue(
                "${chassis.id}: internalRatio=${chassis.internalRatio}",
                GearCalculationInput.isValidInternalRatio(chassis.internalRatio)
            )
        }
    }

    /**
     * シートの既定値は `defaultFrom` でここから引かれる。
     * レジストリの範囲外だと、新規シートを作った瞬間に「範囲外の値が入った状態」で始まる。
     */
    @Test
    fun `内部減速比とタイヤ径がレジストリの範囲に収まる`() {
        val internalRatio = TouringSetupSchema.byKey.getValue("internalRatio") as NumberFieldDef
        val tireMm = TouringSetupSchema.byKey.getValue("tireMm") as NumberFieldDef

        // ツーリング以外（ミニッツ 25mm・1/8 バギー 117mm 等）はスライダーの外に出るので対象外
        for (chassis in db.chassis.filter { it.category.equals("touring", ignoreCase = true) }) {
            assertTrue(
                "${chassis.id}: internalRatio=${chassis.internalRatio} が" +
                    " ${internalRatio.min}..${internalRatio.max} の外",
                chassis.internalRatio in internalRatio.min..internalRatio.max
            )
            assertTrue(
                "${chassis.id}: defaultTireMm=${chassis.defaultTireMm} が" +
                    " ${tireMm.min}..${tireMm.max} の外",
                chassis.defaultTireMm.toDouble() in tireMm.min..tireMm.max
            )
        }
    }

    private companion object {
        /**
         * v1 時点（MVP 出荷時）に存在した 45 件。**この一覧は減らさない。**
         * 追加したときは足すが、消したくなったら `category` で隠すこと。
         */
        val KNOWN_IDS = setOf(
            "tamiya_tt01_tt01e", "tamiya_tt02", "tamiya_tt02b", "tamiya_tb02_tbevo_iii",
            "tamiya_ta07_ta08_trf420", "tamiya_tl01", "tamiya_m01_m02", "tamiya_m03_m04",
            "tamiya_f103_f104_f1", "tamiya_trf414m",
            "yokomo_bd9_bd10_bd11_bd12", "yokomo_yd2_rwd", "yokomo_yd4_4wd", "yokomo_bmax2_bmax4",
            "associated_tc3", "associated_tc4", "associated_tc6_tc7", "associated_b6_x_2wd",
            "associated_b7_2wd", "associated_rc10_classic",
            "tlr_22", "tlr_22_2_0_3_0_4_0_5_0", "tlr_224_4wd", "tlr_8ightx_1_8",
            "kyosho_miniz", "kyosho_miniz_monster", "kyosho_lazer_zx", "kyosho_optima_rb",
            "hpi_cyclone_16t", "hpi_cyclone_tc_18t", "hpi_cyclone_s_12_d4", "hpi_micro_rs4",
            "hpi_rs4_sport", "hpi_rs4_mt", "hpi_pro3", "hpi_pro4",
            "schumacher_mi2_17t", "schumacher_mi2_20t", "schumacher_cougar_sv",
            "traxxas_stampede_rustler_bandit", "traxxas_slash_2wd", "traxxas_4tec",
            "xray_x4", "xray_xb4_4wd", "xray_xb2_2wd"
        )
    }
}
