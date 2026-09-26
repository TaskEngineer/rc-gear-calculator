package io.github.taskengineer.rcgear.domain.common

/**
 * 複数の書き込みを 1 つの原子的な単位にまとめる窓口（M-8 / BUG-3）。
 *
 * インポートは車・シート・上書きという **3 つの Repository** に書き込む。
 * それぞれの一括メソッドは Room が 1 トランザクションで実行するが、
 * 3 つの間に境界が無いままだと「車とシートは入ったが上書きの途中で失敗」という
 * 半端な状態が残りうる。ファイル 1 つの取り込みは全部入るか 1 行も入らないかの
 * どちらかであるべきなので、UseCase 側で境界を張れるようにする。
 *
 * ドメインは Room を知らないので interface をここに置き、実装
 * （`data/local/room/RoomTransactionRunner`）が `RoomDatabase.withTransaction` に委ねる。
 */
interface TransactionRunner {

    /**
     * [block] を 1 トランザクションで実行する。
     * [block] が例外を投げたら、その中の書き込みは**すべて取り消される**。
     */
    suspend operator fun <T> invoke(block: suspend () -> T): T
}
