package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.common.IdGenerator

/**
 * 連番の [IdGenerator]。
 *
 * 本物は UUID なので、採番結果が絡むテスト（v1 → v2 変換など）は
 * これを挟まないとアサートできない。
 */
class FakeIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var next = 1
    override fun newId(): String = "$prefix-${next++}"
}
