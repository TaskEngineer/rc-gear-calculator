package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.SavedSetup
import io.github.taskengineer.rcgear.domain.repository.SetupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [SetupRepository] のインメモリ Fake（REF-3 / S-6）。
 *
 * MockK ではなく Fake を使うのは、**本物と同じ制約を再現できる**から。
 * ここでは Room 側の 2 つの性質を写している:
 *  - `saved_setups.name` のユニークインデックス → 重複を渡すと例外
 *  - 一覧は updatedAt の降順
 *
 * MockK の `coEvery { ... } returns` ではこの制約が消えるため、
 * 「重複名を一括 insert すると全滅する」というバグをテストが素通りさせてしまう。
 *
 * @param failOnWrite true にすると書き込みが例外を投げる。
 *                    トランザクション境界のテスト（M-8）で使う。
 */
class FakeSetupRepository(
    initial: List<SavedSetup> = emptyList(),
    var failOnWrite: Boolean = false
) : SetupRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** save() / update() が刻む時刻。テストから差し替えられる */
    var now: Long = 1_000L

    /** アサーション用。現在 DB に入っているもの */
    val stored: List<SavedSetup> get() = state.value

    /** restoreAll が呼ばれた回数。一括登録であることの確認に使う */
    var restoreAllCallCount: Int = 0
        private set

    override fun observeAll(): Flow<List<SavedSetup>> =
        state.map { list -> list.sortedByDescending { it.updatedAt } }

    override suspend fun getById(id: Long): SavedSetup? = state.value.firstOrNull { it.id == id }

    override suspend fun existsByName(name: String): Boolean = state.value.any { it.name == name }

    override suspend fun save(
        name: String,
        chassisId: String,
        pinion: Int,
        spur: Int,
        internalRatioSnapshot: Double,
        kv: Int,
        cells: Int,
        tireMm: Int
    ): Long {
        checkWritable()
        requireUniqueNames(state.value.map { it.name } + name)
        val id = nextId++
        state.value += SavedSetup(
            id = id,
            name = name,
            chassisId = chassisId,
            pinion = pinion,
            spur = spur,
            internalRatioSnapshot = internalRatioSnapshot,
            kv = kv,
            cells = cells,
            tireMm = tireMm,
            createdAt = now,
            updatedAt = now
        )
        return id
    }

    override suspend fun getAllOnce(): List<SavedSetup> =
        state.value.sortedByDescending { it.updatedAt }

    override suspend fun restoreAll(setups: List<SavedSetup>) {
        restoreAllCallCount++
        if (setups.isEmpty()) return
        checkWritable()
        requireUniqueNames(state.value.map { it.name } + setups.map { it.name })
        // 一括 insert は 1 トランザクション。ここまで来たら全件入る
        state.value += setups.map { it.copy(id = nextId++) }
    }

    override suspend fun update(setup: SavedSetup) {
        checkWritable()
        state.value = state.value.map { if (it.id == setup.id) setup.copy(updatedAt = now) else it }
    }

    override suspend fun delete(id: Long) {
        checkWritable()
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        checkWritable()
        state.value = emptyList()
    }

    private fun checkWritable() {
        if (failOnWrite) error("FakeSetupRepository: 書き込み失敗を再現しています")
    }

    /** Room のユニークインデックス違反を再現する */
    private fun requireUniqueNames(names: List<String>) {
        val duplicated = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicated.isNotEmpty()) {
            error("UNIQUE constraint failed: saved_setups.name " + duplicated.joinToString())
        }
    }
}
