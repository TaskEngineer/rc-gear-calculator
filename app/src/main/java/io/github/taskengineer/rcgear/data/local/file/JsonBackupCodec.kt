package io.github.taskengineer.rcgear.data.local.file

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedOverrideDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedSetupDto
import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupCodec] の kotlinx.serialization 実装（schemaVersion 1）。
 *
 * ここだけがエクスポート JSON のキー名と型を知っている。
 * AGENTS.md §4 のとおり、既存キーの意味を変えるときは
 * `ExportDataDto.CURRENT_SCHEMA_VERSION` を上げ、旧版の読み込みは残すこと。
 */
@Singleton
class JsonBackupCodec @Inject constructor() : BackupCodec {

    /**
     * `encodeDefaults = true` は必須（S-6 で発覚）。
     * kotlinx.serialization は既定値と一致するフィールドを **書き出さない** ため、
     * これが無いと現在の `schemaVersion`（1 = 既定値と同値）が JSON に現れない。
     * バージョンを書かないバックアップは、将来 v2 を出したときに
     * 「省略 = v1」として読まれる。空の `setups` / `overrides` も同じ理由で消えていた。
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
                setups = data.setups.map { it.toDto() },
                overrides = data.overrides.map { it.toDto() }
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

        return BackupCodec.DecodeResult.Success(
            BackupData(
                exportedAt = dto.exportedAt,
                setups = dto.setups.map { it.toDomain() },
                overrides = dto.overrides.map { it.toDomain() }
            )
        )
    }

    // ----- ドメイン ⇔ DTO -----

    private fun SavedSetup.toDto(): ExportedSetupDto = ExportedSetupDto(
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

    private fun ChassisOverride.toDto(): ExportedOverrideDto = ExportedOverrideDto(
        chassisId = chassisId,
        internalRatio = internalRatio,
        defaultTireMm = defaultTireMm,
        note = note,
        updatedAt = updatedAt
    )

    /** id は端末固有なので JSON には無い。0 = 未採番として復元する */
    private fun ExportedSetupDto.toDomain(): SavedSetup = SavedSetup(
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
