package io.github.taskengineer.rcgear.data.local.room

import androidx.room.withTransaction
import io.github.taskengineer.rcgear.domain.common.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TransactionRunner] の Room 実装（M-8）。
 *
 * `withTransaction` は内部で専用のディスパッチャに切り替え、同じトランザクション中の
 * 問い合わせが同じ接続を使うようにする。入れ子にしても 1 つのトランザクションとして
 * 扱われるので、Repository 側の `withTransaction` と併用して問題ない。
 */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val db: RcGearDatabase
) : TransactionRunner {

    override suspend fun <T> invoke(block: suspend () -> T): T = db.withTransaction { block() }
}
