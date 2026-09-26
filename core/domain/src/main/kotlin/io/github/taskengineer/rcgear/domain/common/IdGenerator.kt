package io.github.taskengineer.rcgear.domain.common

/**
 * 新しい ID を採る窓口（DEBT-11 の積み残し。M-4 で追加）。
 *
 * v2 の主キーは UUID の文字列なので、Repository が `UUID.randomUUID()` を
 * 直接呼ぶと「作った ID が毎回変わる」ためテストで結果を固定できない。
 * [TimeProvider] と同じ理由で注入にしてある。
 *
 * 実装は `:app` の `data/system/UuidIdGenerator`。テストでは連番の Fake を使う。
 */
fun interface IdGenerator {
    fun newId(): String
}
