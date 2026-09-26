package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.room.dao.CalculationHistoryDao
import io.github.taskengineer.rcgear.data.local.room.entity.CalculationHistoryEntity
import io.github.taskengineer.rcgear.domain.repository.CalculationHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CalculationHistoryRepository] の Room 実装。
 *
 * MVP では記録（Insert）のみ。閲覧UIは Phase 2 で追加する（PLAN 2.1.3）。
 * 記録タイミングは CALC 画面実装時（Step 8）に決めるが、
 * スライダー操作のたびに書くと膨大になるため「保存時 or 一定の区切り」を想定。
 */
@Singleton
class CalculationHistoryRepositoryImpl @Inject constructor(
    private val historyDao: CalculationHistoryDao
) : CalculationHistoryRepository {

    override suspend fun record(
        chassisId: String,
        pinion: Int,
        spur: Int,
        topSpeedKmh: Double
    ) {
        historyDao.insert(
            CalculationHistoryEntity(
                chassisId = chassisId,
                pinion = pinion,
                spur = spur,
                topSpeedKmh = topSpeedKmh,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteAll() {
        historyDao.deleteAll()
    }
}
