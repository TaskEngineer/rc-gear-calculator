package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.repository.CalculationHistoryRepository

/**
 * [CalculationHistoryRepository] の Fake（REF-3 / S-6）。
 *
 * 本物が書き込み専用なので、Fake も「何が記録されたか」を溜めるだけでよい。
 * M-3 でこのリポジトリごと消える予定（計画 §7.5）。
 */
class FakeCalculationHistoryRepository : CalculationHistoryRepository {

    data class Record(
        val chassisId: String,
        val pinion: Int,
        val spur: Int,
        val topSpeedKmh: Double
    )

    val records = mutableListOf<Record>()

    override suspend fun record(chassisId: String, pinion: Int, spur: Int, topSpeedKmh: Double) {
        records += Record(chassisId, pinion, spur, topSpeedKmh)
    }

    override suspend fun deleteAll() {
        records.clear()
    }
}
