package io.github.taskengineer.rcgear.data.repository

import io.github.taskengineer.rcgear.data.local.room.dao.CarDao
import io.github.taskengineer.rcgear.data.local.room.entity.CarEntity
import io.github.taskengineer.rcgear.domain.common.IdGenerator
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [CarRepository] の Room 実装（M-4） */
@Singleton
class CarRepositoryImpl @Inject constructor(
    private val carDao: CarDao,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator
) : CarRepository {

    override fun observeCars(includeArchived: Boolean): Flow<List<Car>> =
        (if (includeArchived) carDao.observeAll() else carDao.observeActive())
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeCar(id: String): Flow<Car?> =
        carDao.observeById(id).map { it?.toDomain() }

    override suspend fun getCar(id: String): Car? = carDao.getById(id)?.toDomain()

    override suspend fun createCar(name: String, chassisId: String, note: String?): String {
        val now = timeProvider.now()
        val id = idGenerator.newId()
        carDao.insert(
            CarEntity(
                id = id,
                name = name,
                chassisId = chassisId,
                note = note,
                isArchived = false,
                createdAt = now,
                updatedAt = now
            )
        )
        return id
    }

    override suspend fun updateCar(car: Car) {
        // createdAt は呼び出し側から来た値をそのまま信じず、保存済みの値を維持する。
        // 画面が古い Car を持ったまま保存しても作成日時が動かないようにするため
        val stored = carDao.getById(car.id) ?: return
        carDao.update(
            car.toEntity(createdAt = stored.createdAt, updatedAt = timeProvider.now())
        )
    }

    override suspend fun deleteCar(id: String) = carDao.deleteById(id)

    override suspend fun getAllOnce(): List<Car> = carDao.getAllOnce().map { it.toDomain() }

    override suspend fun restoreAll(cars: List<Car>) {
        if (cars.isEmpty()) return
        // createdAt / updatedAt は元データのまま入れる（エクスポートの往復で時刻が動かない）
        carDao.upsertAll(cars.map { it.toEntity(it.createdAt, it.updatedAt) })
    }

    override suspend fun deleteAll() = carDao.deleteAll()

    private fun CarEntity.toDomain() = Car(
        id = id,
        name = name,
        chassisId = chassisId,
        note = note,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Car.toEntity(createdAt: Long, updatedAt: Long) = CarEntity(
        id = id,
        name = name,
        chassisId = chassisId,
        note = note,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
