package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.common.TransactionRunner

/**
 * ロールバックを再現する [TransactionRunner] の Fake（M-8）。
 *
 * 「ただブロックを実行するだけ」の Fake にすると、**取り消しが効いていなくても
 * テストが通ってしまう** — つまり M-8 で入れたトランザクション境界を検証できない。
 * そこで、渡された Fake の状態を開始時に控え、例外が出たら書き戻す。
 */
class FakeTransactionRunner(private val participants: List<Snapshotable>) : TransactionRunner {

    constructor(vararg participants: Snapshotable) : this(participants.toList())

    /** ロールバックした回数。境界が効いたことの確認に使う */
    var rollbackCount: Int = 0
        private set

    override suspend fun <T> invoke(block: suspend () -> T): T {
        val saved = participants.map { it.captureState() }
        return try {
            block()
        } catch (e: Throwable) {
            rollbackCount++
            participants.zip(saved).forEach { (participant, state) -> participant.restoreState(state) }
            throw e
        }
    }
}

/** トランザクションに参加できる Fake。状態を丸ごと控えて書き戻せる */
interface Snapshotable {
    fun captureState(): Any
    fun restoreState(state: Any)
}
