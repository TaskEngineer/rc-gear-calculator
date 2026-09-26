package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.SetupRepository
import javax.inject.Inject

/**
 * 全データ（保存セッティング + シャーシ上書き）を書き出す（PLAN Step 11）。
 *
 * ファイルへの書き込み（SAF）は呼び出し側（ConfigViewModel + JsonFileDataSource）が行う。
 * この UseCase は「現在のデータを集める」ところまでを担当し、
 * 文字列への変換は [BackupCodec] に任せる（REF-2 / S-5）。
 */
class ExportDataUseCase @Inject constructor(
    private val setupRepository: SetupRepository,
    private val chassisRepository: ChassisRepository,
    private val codec: BackupCodec
) {

    suspend operator fun invoke(): String =
        codec.encode(
            BackupData(
                exportedAt = System.currentTimeMillis(),
                setups = setupRepository.getAllOnce(),
                overrides = chassisRepository.getAllOverridesOnce()
            )
        )
}
