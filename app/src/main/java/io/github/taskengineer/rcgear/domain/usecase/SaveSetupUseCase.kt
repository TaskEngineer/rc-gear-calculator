package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.repository.CalculationHistoryRepository
import io.github.taskengineer.rcgear.data.repository.SetupRepository
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import javax.inject.Inject

/**
 * セッティング保存のユースケース（PLAN 4.2）。
 *
 * バリデーション + 保存 + 計算履歴の記録をひとまとめにする。
 * ViewModel はこの UseCase を呼ぶだけでよく、保存の副作用
 * （履歴 Insert）の存在を知らずに済む。
 */
class SaveSetupUseCase @Inject constructor(
    private val setupRepository: SetupRepository,
    private val historyRepository: CalculationHistoryRepository
) {

    sealed interface Result {
        /** 保存成功。id は採番された主キー */
        data class Success(val id: Long) : Result

        /** 名前が空（または空白のみ） */
        data object BlankName : Result

        /** 同名のセッティングが既に存在する */
        data object DuplicateName : Result
    }

    /**
     * @param name       保存名（前後の空白はトリムされる）
     * @param chassisId  選択中のシャーシID
     * @param input      保存時点の計算入力。internalRatio がスナップショットとして凍結される
     * @param result     保存時点の計算結果。計算履歴（topSpeedKmh）の記録に使う
     */
    suspend operator fun invoke(
        name: String,
        chassisId: String,
        input: GearCalculationInput,
        result: GearCalculationResult
    ): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.BlankName
        if (setupRepository.existsByName(trimmed)) return Result.DuplicateName

        val id = setupRepository.save(
            name = trimmed,
            chassisId = chassisId,
            pinion = input.pinion,
            spur = input.spur,
            internalRatioSnapshot = input.internalRatio,
            kv = input.kv,
            cells = input.cells,
            tireMm = input.tireMm
        )

        // MVP の計算履歴は「保存」を計算の区切りとして記録する（PLAN 2.1.3）
        historyRepository.record(
            chassisId = chassisId,
            pinion = input.pinion,
            spur = input.spur,
            topSpeedKmh = result.topSpeedKmh
        )

        return Result.Success(id)
    }
}
