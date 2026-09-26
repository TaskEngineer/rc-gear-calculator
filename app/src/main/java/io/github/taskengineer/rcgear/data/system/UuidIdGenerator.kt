package io.github.taskengineer.rcgear.data.system

import io.github.taskengineer.rcgear.domain.common.IdGenerator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [IdGenerator] の実装。UUID v4 を文字列で返す。
 *
 * 端末固有ではないのでエクスポート JSON にもそのまま出せる。
 * これにより **インポートが id による upsert になり、書き出し / 読み込みが冪等**になる
 * （v1 の Room 自動採番 id は端末固有だったので出力できず、「同名スキップ」という
 * 奇妙な挙動とリネームで往復不能になる問題を抱えていた）。
 */
@Singleton
class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
