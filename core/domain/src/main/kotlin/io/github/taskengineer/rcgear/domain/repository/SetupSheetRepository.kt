package io.github.taskengineer.rcgear.domain.repository

import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import kotlinx.coroutines.flow.Flow

/**
 * セッティングシートの永続化窓口（M-4）。実装は `data/repository/SetupSheetRepositoryImpl`。
 *
 * 呼び出し側は保存形式（EAV か JSON 列か）を知らない。[SetupValues] しか見えないので、
 * 重いと分かったら実装の中だけで差し替えられる（計画 §4.1）。
 */
interface SetupSheetRepository {

    /** 1 台ぶんのシート一覧（ヘッダのみ）。走行日の新しい順 */
    fun observeSheets(carId: String): Flow<List<SetupSheet>>

    /** シート 1 枚を値ごと監視する */
    fun observeSheet(id: String): Flow<SetupSheetWithValues?>

    suspend fun getSheet(id: String): SetupSheetWithValues?

    /**
     * 新規シート。
     *
     * @param values     初期値。新規なら `TouringSetupSchema.initialValues(...)`、
     *   複製なら元シートの値をそのまま渡す
     * @param baselineId 「前回のセット」。複製元をここに入れると差分の軸 B が効く
     * @return 採番された id
     */
    suspend fun createSheet(
        carId: String,
        name: String,
        values: SetupValues,
        baselineId: String? = null
    ): String

    /** ヘッダだけ更新する。値は触らない */
    suspend fun updateSheet(sheet: SetupSheet)

    /**
     * 1 項目だけ更新する。`value` が null なら**行ごと削除**（＝空欄に戻す）。
     *
     * EAV を採った理由がこれ。値が `(sheetId, fieldKey)` でアドレスできるので、
     * 束全体を読んで書き戻さずに 1 行の upsert で済む。
     */
    suspend fun setValue(sheetId: String, fieldKey: String, value: SetupValue?)

    /** 束ごと入れ替える。渡されなかったキーは空欄になる */
    suspend fun replaceValues(sheetId: String, values: SetupValues)

    suspend fun deleteSheet(id: String)

    /** エクスポート用の単発取得（値つき） */
    suspend fun getAllOnce(): List<SetupSheetWithValues>

    /** インポートの一括復元。id による upsert で冪等 */
    suspend fun restoreAll(sheets: List<SetupSheetWithValues>)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    suspend fun deleteAll()
}
