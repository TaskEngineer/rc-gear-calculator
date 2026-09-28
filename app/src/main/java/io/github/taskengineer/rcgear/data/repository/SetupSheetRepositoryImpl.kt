package io.github.taskengineer.rcgear.data.repository

import androidx.room.withTransaction
import io.github.taskengineer.rcgear.data.local.room.RcGearDatabase
import io.github.taskengineer.rcgear.data.local.room.dao.SetupSheetDao
import io.github.taskengineer.rcgear.data.local.room.dao.SetupValueDao
import io.github.taskengineer.rcgear.data.local.room.entity.SetupSheetEntity
import io.github.taskengineer.rcgear.data.local.room.entity.SetupValueEntity
import io.github.taskengineer.rcgear.data.local.room.relation.SheetWithValues
import io.github.taskengineer.rcgear.domain.common.IdGenerator
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValueCodec
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SetupSheetRepository] の Room 実装（M-4）。
 *
 * EAV の行（`num` / `text` の 2 列）と [SetupValue] の相互変換は
 * `:core:domain` の [SetupValueCodec] が持つ。ここはトランザクション境界と
 * 時刻・ID の採番だけを受け持つ。
 *
 * ### 消えたシートへの書き込み（BUG-6）
 * [setValue] / [replaceValues] は **同じトランザクションの中でシートの存在を確かめてから**
 * 書く。`setup_values.sheetId` には外部キーが張ってあるので、消えたシートに upsert すると
 * `SQLiteConstraintException` が飛び、捕まえる者が居なければアプリごと落ちる。
 * 「画面を開いている間にシートが消える」は利用者から見れば異常事態ではないため、
 * 例外ではなく `false` を返して呼び出し側に判断させる。
 *
 * 存在確認と書き込みを 1 トランザクションに入れているので、確認と書き込みの
 * 隙間で消える余地は無い（Room の `withTransaction` は同じ接続で直列化する）。
 */
@Singleton
class SetupSheetRepositoryImpl @Inject constructor(
    private val db: RcGearDatabase,
    private val sheetDao: SetupSheetDao,
    private val valueDao: SetupValueDao,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator
) : SetupSheetRepository {

    override fun observeSheets(carId: String): Flow<List<SetupSheet>> =
        sheetDao.observeByCar(carId).map { entities -> entities.map { it.toDomain() } }

    override fun observeSheet(id: String): Flow<SetupSheetWithValues?> =
        sheetDao.observeWithValues(id).map { it?.toDomain() }

    override suspend fun getSheet(id: String): SetupSheetWithValues? =
        sheetDao.getWithValues(id)?.toDomain()

    override suspend fun createSheet(
        carId: String,
        name: String,
        values: SetupValues,
        baselineId: String?
    ): String {
        val now = timeProvider.now()
        val id = idGenerator.newId()
        // ヘッダだけ入って値が入らない（またはその逆）状態を残さない
        db.withTransaction {
            sheetDao.insert(
                SetupSheetEntity(
                    id = id,
                    carId = carId,
                    name = name,
                    baselineId = baselineId,
                    sessionDate = null,
                    trackName = null,
                    surface = null,
                    airTempC = null,
                    trackTempC = null,
                    humidityPct = null,
                    bestLapMs = null,
                    note = null,
                    isFavorite = false,
                    schemaId = TouringSetupSchema.SCHEMA_ID,
                    createdAt = now,
                    updatedAt = now
                )
            )
            valueDao.upsertAll(values.toEntities(id, now))
        }
        return id
    }

    override suspend fun updateSheet(sheet: SetupSheet) {
        val stored = sheetDao.getById(sheet.id) ?: return
        sheetDao.update(
            sheet.toEntity(createdAt = stored.createdAt, updatedAt = timeProvider.now())
        )
    }

    override suspend fun setValue(sheetId: String, fieldKey: String, value: SetupValue?): Boolean {
        val now = timeProvider.now()
        return db.withTransaction {
            val sheet = sheetDao.getById(sheetId) ?: return@withTransaction false
            if (value == null) {
                // 空欄は「null が入った行」ではなく行ごと削除で表す
                valueDao.delete(sheetId, fieldKey)
            } else {
                val encoded = SetupValueCodec.encode(value)
                valueDao.upsert(SetupValueEntity(sheetId, fieldKey, encoded.num, encoded.text, now))
            }
            sheetDao.update(sheet.copy(updatedAt = now))
            true
        }
    }

    override suspend fun replaceValues(sheetId: String, values: SetupValues): Boolean {
        val now = timeProvider.now()
        return db.withTransaction {
            val sheet = sheetDao.getById(sheetId) ?: return@withTransaction false
            if (values.isEmpty()) {
                valueDao.deleteBySheet(sheetId)
            } else {
                valueDao.upsertAll(values.toEntities(sheetId, now))
                valueDao.deleteBySheetExcept(sheetId, values.keys.toList())
            }
            sheetDao.update(sheet.copy(updatedAt = now))
            true
        }
    }

    override suspend fun deleteSheet(id: String) = sheetDao.deleteById(id)

    override suspend fun getAllOnce(): List<SetupSheetWithValues> =
        sheetDao.getAllWithValuesOnce().map { it.toDomain() }

    override suspend fun restoreAll(sheets: List<SetupSheetWithValues>) {
        if (sheets.isEmpty()) return
        db.withTransaction {
            // createdAt / updatedAt は元データのまま入れる（往復で時刻が動かない）
            sheetDao.upsertAll(
                sheets.map { it.sheet.toEntity(it.sheet.createdAt, it.sheet.updatedAt) }
            )
            sheets.forEach { sheet ->
                valueDao.upsertAll(sheet.values.toEntities(sheet.id, sheet.sheet.updatedAt))
            }
        }
    }

    override suspend fun deleteAll() = sheetDao.deleteAll()

    // ----- 変換 -----

    private fun SetupValues.toEntities(sheetId: String, updatedAt: Long): List<SetupValueEntity> =
        map.map { (fieldKey, value) ->
            val stored = SetupValueCodec.encode(value)
            SetupValueEntity(sheetId, fieldKey, stored.num, stored.text, updatedAt)
        }

    private fun SheetWithValues.toDomain() = SetupSheetWithValues(
        sheet = sheet.toDomain(),
        values = SetupValues(
            values.mapNotNull { entity ->
                SetupValueCodec.decode(entity.fieldKey, entity.num, entity.text)
                    ?.let { entity.fieldKey to it }
            }.toMap()
        )
    )

    private fun SetupSheetEntity.toDomain() = SetupSheet(
        id = id,
        carId = carId,
        name = name,
        baselineId = baselineId,
        conditions = SessionConditions(
            sessionDate = sessionDate,
            trackName = trackName,
            surface = surface,
            airTempC = airTempC,
            trackTempC = trackTempC,
            humidityPct = humidityPct,
            bestLapMs = bestLapMs
        ),
        note = note,
        isFavorite = isFavorite,
        schemaId = schemaId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun SetupSheet.toEntity(createdAt: Long, updatedAt: Long) = SetupSheetEntity(
        id = id,
        carId = carId,
        name = name,
        baselineId = baselineId,
        sessionDate = conditions.sessionDate,
        trackName = conditions.trackName,
        surface = conditions.surface,
        airTempC = conditions.airTempC,
        trackTempC = conditions.trackTempC,
        humidityPct = conditions.humidityPct,
        bestLapMs = conditions.bestLapMs,
        note = note,
        isFavorite = isFavorite,
        schemaId = schemaId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
