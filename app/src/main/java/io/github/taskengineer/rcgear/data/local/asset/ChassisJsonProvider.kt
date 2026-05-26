package io.github.taskengineer.rcgear.data.local.asset

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.taskengineer.rcgear.data.local.asset.dto.ChassisDatabaseDto
import io.github.taskengineer.rcgear.domain.model.Chassis
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
 * - 起動後に初めて呼ばれた時点で読み込み、以降はメモリにキャッシュする。
 * - スレッドセーフ。Mutex で多重読み込みを防ぐ。
 * - Step 5 で ChassisRepository から DAO の Flow と合成して使う。
 */
@Singleton
class ChassisJsonProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ----- 内部状態 -----
    // null = 未ロード、非null = ロード済み
    @Volatile
    private var cached: List<Maker>? = null

    // 多重読み込み防止用
    private val mutex = Mutex()

    // kotlinx.serialization の Json インスタンス
    // - ignoreUnknownKeys: 将来 JSON に新フィールドが増えてもアプリが落ちないように
    // - prettyPrint は不要（読み込み専用なので）
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * シャーシDBを取得する（メーカー単位のリスト）。
     * 初回呼び出し時にファイルを読み込み、以降はキャッシュを返す。
     */
    suspend fun getMakers(): List<Maker> {
        // 既にキャッシュがあれば即返す（ロックを取らずに済むので軽い）
        cached?.let { return it }

        // ロックを取って再チェック → 読み込み（double-checked locking）
        return mutex.withLock {
            cached ?: loadFromAssets().also { cached = it }
        }
    }

    /**
     * assets から JSON を読み込み、DTO → ドメインモデルへ変換する。
     * IO スレッドで実行。
     */
    private suspend fun loadFromAssets(): List<Maker> = withContext(Dispatchers.IO) {
        // assets/chassis-db.json を文字列として読み込む
        // use{} で AutoCloseable を確実に閉じる
        val jsonText = context.assets.open(ASSET_FILE_NAME).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }

        // JSON → DTO
        val dto = json.decodeFromString<ChassisDatabaseDto>(jsonText)

        // DTO → ドメインモデルへマッピング
        // Map<String, List<ChassisDto>> の順序を保つため LinkedHashMap になっているはず
        // （kotlinx.serialization 1.7 系はデフォルトで順序保持）
        dto.makers.map { (makerName, entries) ->
            Maker(
                name = makerName,
                chassis = entries.map { it.toDomain() }
            )
        }
    }

    /**
     * デバッグや差し替え時用。テストでキャッシュをリセットしたい場合に使う。
     * 本番コードからは原則呼ばない。
     */
    internal suspend fun invalidate() {
        mutex.withLock { cached = null }
    }

    companion object {
        private const val ASSET_FILE_NAME = "chassis-db.json"
    }
}

// ----- DTO → ドメインの変換拡張関数 -----
// この階層に置くことで、ドメイン層は DTO の存在を知らずに済む。

private fun io.github.taskengineer.rcgear.data.local.asset.dto.ChassisDto.toDomain(): Chassis =
    Chassis(
        id = id,
        name = name,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        note = note,
        isUserEdited = false  // JSON 由来の時点では常に false。Step 5 で合成時に上書き判定する
    )