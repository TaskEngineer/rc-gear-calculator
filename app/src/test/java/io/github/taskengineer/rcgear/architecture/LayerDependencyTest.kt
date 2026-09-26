package io.github.taskengineer.rcgear.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ドメイン層の純粋性を固定するテスト（REF-2 / S-5）。
 *
 * S-5 で `domain/` から Android・UI・data への依存を全て剥がしたが、
 * **放っておけば必ず戻る**種類の負債でもある（新しい画面を書いていると
 * UseCase から Repository の実装を呼びたくなる瞬間が来る）。
 *
 * 計画では CI に grep チェックを足す想定だったが、単体テストにした。
 * 理由は (a) `testDebugUnitTest` は既に DoD に入っているので追加の仕掛けが要らない、
 * (b) 手元で即座に落ちる、(c) 違反したファイル名と行がそのまま出る。
 *
 * S-9 で `:core:domain` を純 Kotlin JVM モジュールとして切り出せば、
 * この検査はコンパイラの仕事になる。**そうなったらこのテストは消してよい。**
 */
class LayerDependencyTest {

    @Test
    fun `domain は Android にも UI にも data にも依存しない`() {
        val violations = domainSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filter { (_, line) -> FORBIDDEN_PREFIXES.any { line.trim().startsWith(it) } }
                .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
        }

        assertTrue(
            "domain 層が外側のレイヤを import している:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `domain のソースが実際に見つかる`() {
        // 上のテストは対象ファイルが 0 件でも緑になってしまう。
        // ディレクトリ構成を変えたときに黙って無効化されるのを防ぐ番人。
        assertTrue("domain のソースが 1 つも見つからない", domainSources().size >= 10)
    }

    private fun domainSources(): List<File> {
        // 単体テストの作業ディレクトリは Gradle モジュール（app/）。
        // Studio から実行するとリポジトリルートになることがあるので両方見る。
        val root = listOf("src/main/java", "app/src/main/java")
            .map { File(it, DOMAIN_PACKAGE_PATH) }
            .firstOrNull { it.isDirectory }
            ?: error("domain ディレクトリが見つからない。cwd=${File(".").absolutePath}")
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    private companion object {
        const val DOMAIN_PACKAGE_PATH = "io/github/taskengineer/rcgear/domain"

        /** domain から import してはいけないパッケージ。正規表現より読みやすいので前方一致で書く */
        val FORBIDDEN_PREFIXES = listOf(
            "import android.",
            "import androidx.",
            "import io.github.taskengineer.rcgear.data.",
            "import io.github.taskengineer.rcgear.core.",
            "import io.github.taskengineer.rcgear.feature.",
            "import io.github.taskengineer.rcgear.navigation."
        )
    }
}
