package io.github.taskengineer.rcgear.domain.repository

import io.github.taskengineer.rcgear.domain.model.SavedSetup
import kotlinx.coroutines.flow.Flow

/**
 * 保存セッティングの永続化窓口（REF-2 / S-5）。
 *
 * 実装は `data/repository/SetupRepositoryImpl`。UseCase / ViewModel は
 * この interface だけを見るので、Room を使っているのか、テストの Fake なのかを知らない。
 */
interface SetupRepository {

    /** 全セッティングを更新日時の新しい順で監視する */
    fun observeAll(): Flow<List<SavedSetup>>

    /** ID 指定で1件取得。存在しなければ null */
    suspend fun getById(id: Long): SavedSetup?

    /** 同名セッティングが既に存在するか（保存ダイアログのバリデーション用） */
    suspend fun existsByName(name: String): Boolean

    /**
     * 新規保存。createdAt / updatedAt は実装側が現在時刻を設定する。
     * @return 採番された id
     */
    suspend fun save(
        name: String,
        chassisId: String,
        pinion: Int,
        spur: Int,
        internalRatioSnapshot: Double,
        kv: Int,
        cells: Int,
        tireMm: Int
    ): Long

    /** 全セッティングの単発取得（エクスポート用） */
    suspend fun getAllOnce(): List<SavedSetup>

    /**
     * インポートしたセッティングの一括復元（BUG-3）。
     * createdAt / updatedAt は元データのまま保持し、id は採番し直す。
     * 1 トランザクションで実行されるため、途中で失敗しても半端に取り込まれない。
     */
    suspend fun restoreAll(setups: List<SavedSetup>)

    /** 既存セッティングの上書き保存。createdAt は維持し updatedAt のみ更新する */
    suspend fun update(setup: SavedSetup)

    /** ID 指定で削除 */
    suspend fun delete(id: Long)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    suspend fun deleteAll()
}
