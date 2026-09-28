package io.github.taskengineer.rcgear.core.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * 他のアプリへ渡す（F-4）。
 *
 * 保存（SAF の `CreateDocument`）と共有（`ACTION_SEND`）は別物として両方置く。
 * 保存は「自分のストレージに残す」、共有は「LINE やメールに投げる」操作で、
 * ピットで使うのは後者のことが多い。
 *
 * ファイルを渡すときは **`cache/shared/` に置いて [FileProvider] の Uri にする**。
 * `file://` を直接渡すと Android 7 以降は `FileUriExposedException` で落ちる。
 */
object Share {

    /** `res/xml/file_paths.xml` の `cache-path` と一致させること */
    private const val SHARED_DIR = "shared"

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /** テキストを共有する。シートのテキスト版（F-4）に使う */
    fun text(context: Context, text: String, subject: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /**
     * ビットマップを PNG にして共有する。CALC の結果画像（Step 12）に使う。
     *
     * **同じファイル名で上書きする。** 共有のたびに溜めても消す機会が無く、
     * キャッシュなので OS に消されても困らない。
     *
     * @return 書き出しに失敗したら false（呼び出し側がスナックバーを出す）
     */
    fun image(context: Context, bitmap: Bitmap, fileName: String, chooserTitle: String): Boolean {
        val uri = try {
            val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                context.packageName + AUTHORITY_SUFFIX,
                file
            )
        } catch (e: Exception) {
            return false
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // 受け取ったアプリがこの Uri を読めるようにする（provider は exported=false）
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        return true
    }
}
