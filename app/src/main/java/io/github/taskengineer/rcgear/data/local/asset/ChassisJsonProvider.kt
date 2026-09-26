package io.github.taskengineer.rcgear.data.local.asset

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.taskengineer.rcgear.data.local.asset.dto.ChassisDatabaseDto
import io.github.taskengineer.rcgear.data.local.asset.dto.ChassisDto
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.Maker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * assets/chassis-db.json を読み込み、ドメインモデルに変換する。
 *
 * - 起動後に初めて呼ばれた時点で読み込み、以降はメモリにキャッシュする
 * - スレッドセーフ。Mutex で多重読み込みを防ぐ
 * - **id での検索用に Map を持つ**（M-7）。v1 はメーカーごとのリストを総なめしていたので、
 *   シャーシ 1 台を引くたびに全件走査していた
 */
@Singleton
class ChassisJsonProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // null = 未ロード、非null = ロード済み
    @Volatile
    private var cached: Loaded? = null

    // 多重読み込み防止用
    private val mutex = Mutex()

    // - ignoreUnknownKeys: 将来 JSON に新フィールドが増えてもアプリが落ちないように
    // - prettyPrint は不要（読み込み専用なので）
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * シャーシDBを取得する（メーカー単位のリスト）。
     * メーカーの並び順・各メーカー内の並び順は JSON の定義順を保つ。
     */
    suspend fun getMakers(): List<Maker> = load().makers

    /** id での単発取得。Map 引きなので O(1)（M-7） */
    suspend fun getById(chassisId: String): Chassis? = load().byId[chassisId]

    /** 出典表記（ROADMAP P-1 で CONFIG > ABOUT に出す） */
    suspend fun getSources(): List<String> = load().sources

    private suspend fun load(): Loaded {
        // 既にキャッシュがあれば即返す（ロックを取らずに済むので軽い）
        cached?.let { return it }

        // ロックを取って再チェック → 読み込み（double-checked locking）
        return mutex.withLock {
            cached ?: loadFromAssets().also { cached = it }
        }
    }

    /** assets から JSON を読み込み、DTO → ドメインモデルへ変換する。IO スレッドで実行 */
    private suspend fun loadFromAssets(): Loaded = withContext(Dispatchers.IO) {
        val jsonText = context.assets.open(ASSET_FILE_NAME).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
        val dto = json.decodeFromString<ChassisDatabaseDto>(jsonText)
        val chassis = dto.chassis.map { it.toDomain() }

        Loaded(
            // メーカーの並びは JSON に現れた順。groupBy は LinkedHashMap を返すので順序が保たれる
            makers = chassis.groupBy { it.makerName }
                .map { (makerName, entries) -> Maker(name = makerName, chassis = entries) },
            byId = chassis.associateBy { it.id },
            sources = dto.sources
        )
    }

    /**
     * デバッグや差し替え時用。テストでキャッシュをリセットしたい場合に使う。
     * 本番コードからは原則呼ばない。
     */
    internal suspend fun invalidate() {
        mutex.withLock { cached = null }
    }

    /** 1 回のパースから作る 3 つのビュー。どれも同じ Chassis インスタンスを指す */
    private data class Loaded(
        val makers: List<Maker>,
        val byId: Map<String, Chassis>,
        val sources: List<String>
    )

    companion object {
        private const val ASSET_FILE_NAME = "chassis-db.json"
    }
}

// ----- DTO → ドメインの変換 -----
// この階層に置くことで、ドメイン層は DTO の存在を知らずに済む。

private fun ChassisDto.toDomain(): Chassis = Chassis(
    id = id,
    name = name,
    internalRatio = internalRatio,
    defaultTireMm = defaultTireMm,
    makerName = maker,
    category = ChassisCategory.fromKey(category),
    traits = ChassisTraits(
        drive = ChassisDrive.fromKey(drive),
        hasCenterDiff = hasCenterDiff
    ),
    note = note,
    // JSON 由来の時点では常に false。Step 5 で合成時に上書き判定する
    isUserEdited = false
)
