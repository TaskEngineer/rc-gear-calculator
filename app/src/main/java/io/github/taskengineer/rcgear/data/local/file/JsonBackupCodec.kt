package io.github.taskengineer.rcgear.data.local.file

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedCarDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedOverrideDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedSchemaRefDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedSetupDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedSheetDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedUserChassisDto
import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.backup.LegacyBackupConverter
import io.github.taskengineer.rcgear.domain.backup.LegacySavedSetup
import io.github.taskengineer.rcgear.domain.common.IdGenerator
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.ChassisCategory
import io.github.taskengineer.rcgear.domain.model.ChassisDrive
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValueCodec
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.UserChassis
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupCodec] の kotlinx.serialization 実装（M-6 で schemaVersion 2 へ）。
 *
 * ここだけがエクスポート JSON のキー名と型を知っている。
 * AGENTS.md §4 のとおり、既存キーの意味を変えるときは
 * `ExportDataDto.CURRENT_SCHEMA_VERSION` を上げ、旧版の読み込みは残すこと。
 *
 * **v1 の読み込みは永久に残す。** v1 → v2 の対応づけは
 * `:core:domain` の [LegacyBackupConverter] が持つ（純粋関数なのでテストしやすい）。
 * この codec は「どの版のファイルか」を判定して渡すだけで、
 * UseCase から見れば v1 も v2 も同じ [BackupData] になる。
 */
@Singleton
class JsonBackupCodec @Inject constructor(
    private val idGenerator: IdGenerator
) : BackupCodec {

    /**
     * `encodeDefaults = true` は必須（S-6 で発覚）。
     * kotlinx.serialization は既定値と一致するフィールドを **書き出さない** ため、
     * これが無いと現在の `schemaVersion` が JSON に現れない。
     * バージョンを書かないバックアップは「省略 = v1」として読まれる。
     */
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    private val lenientJson = Json { ignoreUnknownKeys = true }

    override fun encode(data: BackupData): String =
        prettyJson.encodeToString(
            ExportDataDto(
                // 既定値は「キーが無いファイル = v1」用なので、書き出す版は明示する
                schemaVersion = ExportDataDto.CURRENT_SCHEMA_VERSION,
                exportedAt = data.exportedAt,
                setupSchema = ExportedSchemaRefDto(
                    schemaId = TouringSetupSchema.SCHEMA_ID,
                    revision = TouringSetupSchema.REVISION
                ),
                cars = data.cars.map { it.toDto() },
                sheets = data.sheets.map { it.toDto() },
                overrides = data.overrides.map { it.toDto() },
                userChassis = data.userChassis.map { it.toDto() },
                // v1 の保存セッティングは M-3 でテーブルごと無くなった。書き出しは常に空
                setups = emptyList()
            )
        )

    override fun decode(text: String): BackupCodec.DecodeResult {
        val dto = try {
            lenientJson.decodeFromString<ExportDataDto>(text)
        } catch (e: SerializationException) {
            return BackupCodec.DecodeResult.InvalidFormat
        } catch (e: IllegalArgumentException) {
            return BackupCodec.DecodeResult.InvalidFormat
        }

        if (dto.schemaVersion > ExportDataDto.CURRENT_SCHEMA_VERSION) {
            return BackupCodec.DecodeResult.UnsupportedVersion
        }

        val overrides = dto.overrides.map { it.toDomain() }
        val data = if (dto.schemaVersion <= ExportDataDto.SCHEMA_VERSION_V1) {
            // v1: 保存セッティングをシャーシ単位の車 + シートに畳み直す
            val converted = LegacyBackupConverter.toV2(
                setups = dto.setups.map { it.toDomain() },
                idGenerator = idGenerator
            )
            BackupData(
                exportedAt = dto.exportedAt,
                cars = converted.cars,
                sheets = converted.sheets,
                overrides = overrides
            )
        } else {
            BackupData(
                exportedAt = dto.exportedAt,
                cars = dto.cars.map { it.toDomain() },
                sheets = dto.sheets.map { it.toDomain() },
                overrides = overrides,
                userChassis = dto.userChassis.map { it.toDomain() }
            )
        }
        return BackupCodec.DecodeResult.Success(data)
    }

    // ----- ユーザー定義シャーシ（F-5） -----

    private fun UserChassis.toDto() = ExportedUserChassisDto(
        id = id,
        makerName = makerName,
        name = name,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        category = category.name,
        drive = traits.drive?.name,
        hasCenterDiff = traits.hasCenterDiff,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExportedUserChassisDto.toDomain() = UserChassis(
        id = id,
        makerName = makerName,
        name = name,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        // 知らない分類・駆動方式は既定に落とす（読み込みを落とさない。M-7 と同じ方針）
        category = ChassisCategory.fromKey(category),
        traits = ChassisTraits(
            drive = ChassisDrive.fromKey(drive),
            hasCenterDiff = hasCenterDiff
        ),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ----- ドメイン -> DTO -----

    private fun Car.toDto() = ExportedCarDto(
        id = id,
        name = name,
        chassisId = chassisId,
        note = note,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun SetupSheetWithValues.toDto() = ExportedSheetDto(
        id = sheet.id,
        carId = sheet.carId,
        name = sheet.name,
        baselineId = sheet.baselineId,
        sessionDate = sheet.conditions.sessionDate,
        trackName = sheet.conditions.trackName,
        surface = sheet.conditions.surface,
        airTempC = sheet.conditions.airTempC,
        trackTempC = sheet.conditions.trackTempC,
        humidityPct = sheet.conditions.humidityPct,
        bestLapMs = sheet.conditions.bestLapMs,
        note = sheet.note,
        isFavorite = sheet.isFavorite,
        schemaId = sheet.schemaId,
        values = values.toJson(),
        createdAt = sheet.createdAt,
        updatedAt = sheet.updatedAt
    )

    private fun ChassisOverride.toDto() = ExportedOverrideDto(
        chassisId = chassisId,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        note = note,
        updatedAt = updatedAt
    )

    /**
     * 値をスカラのオブジェクトとして書く。EAV の 2 列（num / text）は内部表現なので
     * ワイヤ形式には出さない。キーの並びはレジストリ順 → 未知キーはキー名順で安定させる
     * （同じデータからは常に同じ JSON が出る ＝ git で差分が読める）。
     */
    private fun SetupValues.toJson(): JsonObject {
        val known = TouringSetupSchema.allFields.mapNotNull { field ->
            this[field.key]?.let { field.key to it.toJsonPrimitive() }
        }
        val unknown = unknownKeys().sorted().mapNotNull { key ->
            this[key]?.let { key to it.toJsonPrimitive() }
        }
        return JsonObject((known + unknown).toMap())
    }

    private fun SetupValue.toJsonPrimitive(): JsonPrimitive = when (this) {
        is SetupValue.IntV -> JsonPrimitive(value)
        is SetupValue.DecimalV -> JsonPrimitive(value)
        is SetupValue.BoolV -> JsonPrimitive(value)
        is SetupValue.ChoiceV -> JsonPrimitive(key)
        is SetupValue.TextV -> JsonPrimitive(value)
    }

    // ----- DTO -> ドメイン -----

    private fun ExportedCarDto.toDomain() = Car(
        id = id,
        name = name,
        chassisId = chassisId,
        note = note,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExportedSheetDto.toDomain() = SetupSheetWithValues(
        sheet = SetupSheet(
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
        ),
        values = values.toSetupValues()
    )

    /**
     * スカラのオブジェクトから値を復元する。
     *
     * 型はレジストリから決める（[SetupValueCodec]）ので、`22` と `22.0` のどちらで
     * 書かれていても整数項目は整数に戻る。**レジストリに無いキーも捨てない** —
     * 将来の版で追加された項目が入ったファイルを読んでも値が消えないことが、
     * スキーマを安全に進化させる担保になる（計画 §7.2）。
     */
    private fun JsonObject.toSetupValues(): SetupValues = SetupValues(
        mapNotNull { (key, element) ->
            val primitive = element as? JsonPrimitive ?: return@mapNotNull null
            if (primitive is JsonNull) return@mapNotNull null
            val value = if (primitive.isString) {
                SetupValueCodec.decode(key, num = null, text = primitive.content)
            } else {
                primitive.booleanOrNull?.let { SetupValue.BoolV(it) }
                    ?: primitive.doubleOrNull?.let { SetupValueCodec.decode(key, num = it, text = null) }
            }
            value?.let { key to it }
        }.toMap()
    )

    /** id は端末固有なので v1 の JSON には無い。0 = 未採番として復元する */
    private fun ExportedSetupDto.toDomain(): LegacySavedSetup = LegacySavedSetup(
        id = 0,
        name = name,
        chassisId = chassisId,
        pinion = pinion,
        spur = spur,
        internalRatioSnapshot = internalRatioSnapshot,
        kv = kv,
        cells = cells,
        tireMm = tireMm,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExportedOverrideDto.toDomain(): ChassisOverride = ChassisOverride(
        chassisId = chassisId,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        note = note,
        updatedAt = updatedAt
    )
}
