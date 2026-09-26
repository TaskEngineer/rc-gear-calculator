package io.github.taskengineer.rcgear.domain.schema

/**
 * セッティングシートの 1 セクション（M-1）。
 *
 * [columns] があるセクションは、実物のセッティングシート用紙と同じく
 * 「行＝項目 / 列＝フロント・センター・リア」のグリッドで描く。
 * その場合、配下の [FieldDef.key] は必ず `"<columnKey>.<行キー>"` の形にする
 * （`SchemaTest` が強制する）。キーの接頭辞がそのまま列になるので、
 * 「axis = fr で自動展開」のような機構は要らない。
 *
 * @property key      永続キー。UI の状態保存やディープリンクに使う
 * @property columns  F/C/R の列。単一列のセクションは `null`
 * @property fields   このセクションの項目。**表示順はこのリスト順**
 */
data class SectionDef(
    val key: String,
    val fields: List<FieldDef>,
    val columns: List<ColumnDef>? = null
) {

    /**
     * グリッド表示用に「行キー → 列キー → 項目」へ組み替える。
     * 列を持たないセクションでは空の Map を返す（そのまま [fields] を縦に並べればよい）。
     *
     * フロントにしか無い項目（キャスターなど）は、リア列が欠けた行として返る。
     * 表示側は欠けたセルを空欄にする。
     */
    fun grid(): List<GridRow> {
        if (columns == null) return emptyList()
        val columnKeys = columns.map { it.key }
        // 行の順序は fields の宣言順から拾う（最初に現れた行キーの順）。
        val rowOrder = LinkedHashSet<String>()
        val cells = mutableMapOf<String, MutableMap<String, FieldDef>>()
        for (field in fields) {
            val columnKey = columnKeys.firstOrNull { field.key.startsWith("$it.") } ?: continue
            val rowKey = field.key.removePrefix("$columnKey.")
            rowOrder += rowKey
            cells.getOrPut(rowKey) { mutableMapOf() }[columnKey] = field
        }
        return rowOrder.map { rowKey -> GridRow(rowKey, cells[rowKey].orEmpty()) }
    }
}

/**
 * グリッドの 1 行。
 *
 * @property rowKey  列接頭辞を除いた項目キー（例 `camberDeg`）。ラベル解決に使う
 * @property byColumn 列キー → その列の項目。欠けている列は入っていない
 */
data class GridRow(val rowKey: String, val byColumn: Map<String, FieldDef>)

/**
 * グリッドの列。フロント / センター / リア。
 *
 * @property key 永続キー。[FieldDef.key] の接頭辞と一致させる
 */
data class ColumnDef(val key: String) {
    companion object {
        val FRONT = ColumnDef("front")
        val CENTER = ColumnDef("center")
        val REAR = ColumnDef("rear")
    }
}
