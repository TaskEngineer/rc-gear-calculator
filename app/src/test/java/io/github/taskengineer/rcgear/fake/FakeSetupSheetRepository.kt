package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [SetupSheetRepository] の in-memory Fake（M-4）。
 *
 * 再現する本物の制約:
 * - 一覧は走行日の新しい順、日付未入力は作成順で後ろ（本物の ORDER BY と同じ）
 * - `setValue(null)` は行ごと削除（＝空欄）。null が入った行は作らない
 * - `replaceValues` は渡されなかったキーを落とす
 * - 値を書き換えるとシートの `updatedAt` が進む
 * - `restoreAll` は id で upsert（冪等）
 */
class FakeSetupSheetRepository(
    initial: List<SetupSheetWithValues> = emptyList()
) : SetupSheetRepository {

    private val sheets = MutableStateFlow(initial.associateBy { it.id })

    var now: Long = 1_000L
    var nextIdSeed: Int = 1

    val stored: List<SetupSheetWithValues> get() = sheets.value.values.toList()

    override fun observeSheets(carId: String): Flow<List<SetupSheet>> =
        sheets.map { map -> map.values.map { it.sheet }.filter { it.carId == carId }.sorted() }

    override fun observeSheet(id: String): Flow<SetupSheetWithValues?> = sheets.map { it[id] }

    override suspend fun getSheet(id: String): SetupSheetWithValues? = sheets.value[id]

    override suspend fun createSheet(
        carId: String,
        name: String,
        values: SetupValues,
        baselineId: String?
    ): String {
        val id = "sheet-${nextIdSeed++}"
        sheets.value += id to SetupSheetWithValues(
            sheet = SetupSheet(
                id = id,
                carId = carId,
                name = name,
                baselineId = baselineId,
                createdAt = now,
                updatedAt = now
            ),
            values = values
        )
        return id
    }

    override suspend fun updateSheet(sheet: SetupSheet) {
        val stored = sheets.value[sheet.id] ?: return
        sheets.value += sheet.id to stored.copy(
            sheet = sheet.copy(createdAt = stored.sheet.createdAt, updatedAt = now)
        )
    }

    override suspend fun setValue(sheetId: String, fieldKey: String, value: SetupValue?) {
        val stored = sheets.value[sheetId] ?: return
        val newValues = if (value == null) {
            stored.values.without(fieldKey)
        } else {
            stored.values.with(fieldKey, value)
        }
        sheets.value += sheetId to stored.copy(
            sheet = stored.sheet.copy(updatedAt = now),
            values = newValues
        )
    }

    override suspend fun replaceValues(sheetId: String, values: SetupValues) {
        val stored = sheets.value[sheetId] ?: return
        sheets.value += sheetId to stored.copy(
            sheet = stored.sheet.copy(updatedAt = now),
            values = values
        )
    }

    override suspend fun deleteSheet(id: String) {
        sheets.value -= id
        // 本物は自己参照 FK の ON DELETE SET NULL。ベースラインが消えても本体は残る
        sheets.value = sheets.value.mapValues { (_, sheet) ->
            if (sheet.sheet.baselineId == id) {
                sheet.copy(sheet = sheet.sheet.copy(baselineId = null))
            } else {
                sheet
            }
        }
    }

    override suspend fun getAllOnce(): List<SetupSheetWithValues> =
        sheets.value.values.sortedBy { it.sheet.createdAt }

    override suspend fun restoreAll(sheets: List<SetupSheetWithValues>) {
        restoreAllCallCount++
        if (sheets.isEmpty()) return
        this.sheets.value += sheets.associateBy { it.id }
    }

    override suspend fun deleteAll() {
        sheets.value = emptyMap()
    }

    /** 本物の CASCADE（車を消すとシートも消える）に相当。[FakeCarRepository] から呼ばれる */
    fun deleteByCar(carId: String) {
        sheets.value = sheets.value.filterValues { it.sheet.carId != carId }
    }

    /** 一括で 1 回だけ呼ばれていることの確認用（BUG-3） */
    var restoreAllCallCount: Int = 0
        private set

    /** 走行日の新しい順、日付未入力は作成順で後ろ */
    private fun List<SetupSheet>.sorted(): List<SetupSheet> = sortedWith(
        compareBy<SetupSheet> { it.conditions.sessionDate == null }
            .thenByDescending { it.conditions.sessionDate ?: 0L }
            .thenByDescending { it.createdAt }
    )
}
