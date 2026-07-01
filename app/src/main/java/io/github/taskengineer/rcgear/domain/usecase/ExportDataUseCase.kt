package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedOverrideDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedSetupDto
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.data.repository.SetupRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 全データ（保存セッティング + シャーシ上書き）を JSON 文字列に書き出す（PLAN Step 11）。
 *
 * ファイルへの書き込み（SAF）は呼び出し側（ConfigViewModel + JsonFileDataSource）が行う。
 * この UseCase は「現在のデータ → JSON 文字列」の変換のみを担当する。
 */
class ExportDataUseCase @Inject constructor(
    private val setupRepository: SetupRepository,
    private val chassisRepository: ChassisRepository
) {

    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(): String {
        val setups = setupRepository.getAllOnce().map { setup ->
            ExportedSetupDto(
                name = setup.name,
                chassisId = setup.chassisId,
                pinion = setup.pinion,
                spur = setup.spur,
                internalRatioSnapshot = setup.internalRatioSnapshot,
                kv = setup.kv,
                cells = setup.cells,
                tireMm = setup.tireMm,
                createdAt = setup.createdAt,
                updatedAt = setup.updatedAt
            )
        }
        val overrides = chassisRepository.getAllOverridesOnce().map { override ->
            ExportedOverrideDto(
                chassisId = override.chassisId,
                internalRatio = override.internalRatio,
                defaultTireMm = override.defaultTireMm,
                note = override.note,
                updatedAt = override.updatedAt
            )
        }
        return json.encodeToString(
            ExportDataDto(
                exportedAt = System.currentTimeMillis(),
                setups = setups,
                overrides = overrides
            )
        )
    }
}
