package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.data.repository.SetupRepository
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * エクスポートJSON からデータを取り込む（PLAN Step 11）。
 *
 * マージ方針（既存データを壊さない）:
 * - セッティング: 同名が既に存在する場合はスキップ。それ以外を追加
 * - 上書き: chassisId 単位で upsert（インポート側の値を優先）。
 *   ただし標準DBに存在しない chassisId はスキップ（別アプリバージョンのデータ対策）
 */
class ImportDataUseCase @Inject constructor(
    private val setupRepository: SetupRepository,
    private val chassisRepository: ChassisRepository
) {

    sealed interface Result {
        /**
         * @property importedSetups   追加されたセッティング数
         * @property skippedSetups    同名スキップされたセッティング数
         * @property importedOverrides 取り込まれた上書き数
         * @property skippedOverrides  不明シャーシでスキップされた上書き数
         */
        data class Success(
            val importedSetups: Int,
            val skippedSetups: Int,
            val importedOverrides: Int,
            val skippedOverrides: Int
        ) : Result

        /** JSON 構文エラー・フォーマット不一致 */
        data object InvalidFormat : Result

        /** schemaVersion がこのアプリより新しく解釈できない */
        data object UnsupportedVersion : Result
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(jsonText: String): Result {
        val data = try {
            json.decodeFromString<ExportDataDto>(jsonText)
        } catch (e: SerializationException) {
            return Result.InvalidFormat
        } catch (e: IllegalArgumentException) {
            return Result.InvalidFormat
        }

        if (data.schemaVersion > ExportDataDto.CURRENT_SCHEMA_VERSION) {
            return Result.UnsupportedVersion
        }

        // ---- セッティングの取り込み（同名スキップ） ----
        var importedSetups = 0
        var skippedSetups = 0
        data.setups.forEach { dto ->
            if (setupRepository.existsByName(dto.name)) {
                skippedSetups++
            } else {
                setupRepository.restore(
                    SavedSetup(
                        id = 0,
                        name = dto.name,
                        chassisId = dto.chassisId,
                        pinion = dto.pinion,
                        spur = dto.spur,
                        internalRatioSnapshot = dto.internalRatioSnapshot,
                        kv = dto.kv,
                        cells = dto.cells,
                        tireMm = dto.tireMm,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt
                    )
                )
                importedSetups++
            }
        }

        // ---- 上書きの取り込み（標準DBに存在するシャーシのみ） ----
        var importedOverrides = 0
        var skippedOverrides = 0
        data.overrides.forEach { dto ->
            if (chassisRepository.getStandardChassisById(dto.chassisId) == null) {
                skippedOverrides++
            } else {
                chassisRepository.restoreOverride(
                    ChassisOverride(
                        chassisId = dto.chassisId,
                        internalRatio = dto.internalRatio,
                        defaultTireMm = dto.defaultTireMm,
                        note = dto.note,
                        updatedAt = dto.updatedAt
                    )
                )
                importedOverrides++
            }
        }

        return Result.Success(
            importedSetups = importedSetups,
            skippedSetups = skippedSetups,
            importedOverrides = importedOverrides,
            skippedOverrides = skippedOverrides
        )
    }
}
