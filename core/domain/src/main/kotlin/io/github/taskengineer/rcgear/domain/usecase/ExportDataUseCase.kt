package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import javax.inject.Inject

/**
 * 全データ（車 + セッティングシート + シャーシ上書き）を書き出す（PLAN Step 11 / M-6）。
 *
 * ファイルへの書き込み（SAF）は呼び出し側（ConfigViewModel + JsonFileDataSource）が行う。
 * この UseCase は「現在のデータを集める」ところまでを担当し、
 * 文字列への変換は [BackupCodec] に任せる（REF-2 / S-5）。
 *
 * アーカイブ済みの車も書き出す。隠しているだけで消したわけではないので、
 * バックアップから外すとユーザーの意図に反する。
 */
class ExportDataUseCase @Inject constructor(
    private val carRepository: CarRepository,
    private val sheetRepository: SetupSheetRepository,
    private val chassisRepository: ChassisRepository,
    private val codec: BackupCodec,
    private val timeProvider: TimeProvider
) {

    suspend operator fun invoke(): String =
        codec.encode(
            BackupData(
                exportedAt = timeProvider.now(),
                cars = carRepository.getAllOnce(),
                sheets = sheetRepository.getAllOnce(),
                overrides = chassisRepository.getAllOverridesOnce()
            )
        )
}
