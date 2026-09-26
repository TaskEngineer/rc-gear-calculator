package io.github.taskengineer.rcgear.fake

import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [CarRepository] の in-memory Fake（M-4）。
 *
 * **本物の制約を再現する**のが Fake を使う理由なので、次は必ず守る:
 * - 一覧は `updatedAt` の新しい順、既定ではアーカイブ済みを含めない
 * - `restoreAll` は id で upsert（同じデータを 2 回入れても増えない）
 * - 車を消すと、その車のシートも消える（本物の CASCADE に相当）。
 *   [sheetRepository] を渡したときだけ連鎖する
 */
class FakeCarRepository(
    initial: List<Car> = emptyList(),
    private val sheetRepository: FakeSetupSheetRepository? = null
) : CarRepository {

    private val cars = MutableStateFlow(initial.associateBy { it.id })

    /** テスト時刻。`createdAt` / `updatedAt` に入る */
    var now: Long = 1_000L

    /** 採番される id。呼ばれるたびに連番が進む */
    var nextIdSeed: Int = 1

    val stored: List<Car> get() = cars.value.values.sortedByDescending { it.updatedAt }

    override fun observeCars(includeArchived: Boolean): Flow<List<Car>> =
        cars.map { map ->
            map.values
                .filter { includeArchived || !it.isArchived }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeCar(id: String): Flow<Car?> = cars.map { it[id] }

    override suspend fun getCar(id: String): Car? = cars.value[id]

    override suspend fun createCar(name: String, chassisId: String, note: String?): String {
        val id = "car-${nextIdSeed++}"
        cars.value += id to Car(
            id = id,
            name = name,
            chassisId = chassisId,
            note = note,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        return id
    }

    override suspend fun updateCar(car: Car) {
        val stored = cars.value[car.id] ?: return
        cars.value += car.id to car.copy(createdAt = stored.createdAt, updatedAt = now)
    }

    override suspend fun deleteCar(id: String) {
        cars.value -= id
        sheetRepository?.deleteByCar(id)
    }

    override suspend fun getAllOnce(): List<Car> = cars.value.values.sortedBy { it.createdAt }

    override suspend fun restoreAll(cars: List<Car>) {
        restoreAllCallCount++
        if (cars.isEmpty()) return
        this.cars.value += cars.associateBy { it.id }
    }

    override suspend fun deleteAll() {
        cars.value = emptyMap()
        sheetRepository?.deleteAll()
    }

    /** 一括で 1 回だけ呼ばれていることの確認用（BUG-3） */
    var restoreAllCallCount: Int = 0
        private set
}
