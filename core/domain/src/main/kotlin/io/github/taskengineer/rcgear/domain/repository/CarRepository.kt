package io.github.taskengineer.rcgear.domain.repository

import io.github.taskengineer.rcgear.domain.model.Car
import kotlinx.coroutines.flow.Flow

/**
 * 車の永続化窓口（M-4）。実装は `data/repository/CarRepositoryImpl`。
 *
 * `createdAt` / `updatedAt` と `id` の採番は実装側が
 * `TimeProvider` / `IdGenerator` 経由で行う。呼び出し側は時計も乱数も触らない。
 */
interface CarRepository {

    /** 一覧を監視する。既定ではアーカイブ済みを含めない */
    fun observeCars(includeArchived: Boolean = false): Flow<List<Car>>

    /** 1 台を監視する。消えたら null が流れる */
    fun observeCar(id: String): Flow<Car?>

    suspend fun getCar(id: String): Car?

    /**
     * 新規作成。
     * @return 採番された id
     */
    suspend fun createCar(name: String, chassisId: String, note: String? = null): String

    /** 上書き保存。`createdAt` は維持し `updatedAt` だけ進める */
    suspend fun updateCar(car: Car)

    /** 削除。シートと値も CASCADE で消える */
    suspend fun deleteCar(id: String)

    /** エクスポート用の単発取得 */
    suspend fun getAllOnce(): List<Car>

    /**
     * インポートの一括復元。**id による upsert** なので同じファイルを
     * 2 回読んでも増えない（エクスポート / インポートが冪等）。
     */
    suspend fun restoreAll(cars: List<Car>)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    suspend fun deleteAll()
}
