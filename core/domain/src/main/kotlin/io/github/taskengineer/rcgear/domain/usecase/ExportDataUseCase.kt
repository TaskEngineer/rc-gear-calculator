package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.common.TimeProvider
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import javax.inject.Inject

/**
 * 全データを書き出す（PLAN Step 11）。
 *
 * ファイルへの書き込み（SAF）は呼び出し側（ConfigViewModel + JsonFileDataSource）が行う。
 * この UseCase は「現在のデータを集める」ところまでを担当し、
 * 文字列への変換は [BackupCodec] に任せる（REF-2 / S-5）。
 *
 * **M-3 時点では上書きだけを書き出す。** 保存セッティングは車 + セッティングシートに
 * 置き換わったが、その Repository が入るのは M-4、v2 の書式が決まるのは M-6 なので、
 * それまでこのユースケースは上書きのみを扱う。
 */
class ExportDataUseCase @Inject constructor(
    private val chassisRepository: ChassisRepository,
    private val codec: BackupCodec,
    private val timeProvider: TimeProvider
) {

    suspend operator fun invoke(): String =
        codec.encode(
            BackupData(
                exportedAt = timeProvider.now(),
                overrides = chassisRepository.getAllOverridesOnce()
            )
        )
}
