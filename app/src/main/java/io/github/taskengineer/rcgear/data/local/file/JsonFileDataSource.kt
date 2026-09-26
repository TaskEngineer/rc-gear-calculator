package io.github.taskengineer.rcgear.data.local.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.taskengineer.rcgear.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF（Storage Access Framework）で取得した Uri へのテキスト読み書き（PLAN 9.3）。
 *
 * Uri の取得自体（ファイルピッカーの起動）は UI 層の
 * ActivityResultContract（CreateDocument / OpenDocument）が行い、
 * このクラスは渡された Uri に対する I/O だけを担当する。
 *
 * 例外メッセージは ConfigViewModel 経由でスナックバーに出る（ユーザーが読む）ので、
 * リソースから引く（S-11）。Uri は文字列に含めない（ユーザーには意味が無く、
 * SAF の Uri はストレージ構成が透けるため）。
 */
@Singleton
class JsonFileDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Uri にテキストを書き込む（上書き） */
    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        } ?: throw IOException(context.getString(R.string.io_error_open_output))
    }

    /** Uri からテキストを読み込む */
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IOException(context.getString(R.string.io_error_open_input))
    }
}
