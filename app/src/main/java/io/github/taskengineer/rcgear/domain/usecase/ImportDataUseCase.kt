package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.data.local.file.dto.ExportDataDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedOverrideDto
import io.github.taskengineer.rcgear.data.local.file.dto.ExportedSetupDto
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.data.repository.SetupRepository
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.SavedSetup
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * エクスポートJSON からデータを取り込む（PLAN Step 11）。
 *
 * マージ方針（既存データを壊さない）:
 * - セッティング: 同名が既に存在する場合はスキップ。それ以外を追加
 * - 上書き: chassisId 単位で upsert（インポート側の値を優先）。
 *   ただし標準DBに存在しない chassisId はスキップ（別アプリバージョンのデータ対策）
 *
 * 値の検証について（REF-1 / BUG-2）:
 * JSON は手で書き換えられるため、値が `GearCalculationInput` の有効範囲に
 * 収まっている保証がない。範囲外のまま Room に入れると、後で SETUPS 詳細を
 * 開いた時や CALC に流し込んだ時に初めて例外になる（＝取り込み時には成功に
 * 見えるのに、後から壊れる）。そのため **取り込み時に 1 行ずつ検証し、
 * 不正な行は棄却して件数で報告する**。クランプしないのは、勝手に丸めた値を
 * ユーザーの保存データとして残す方が不親切なため。
 *
 * 原子性について（BUG-3）:
 * 検証を通った行は Repository の一括メソッドでまとめて書き込む。Room は
 * コレクションを受ける @Insert を 1 トランザクションで実行するので、
 * 途中で失敗しても半端に取り込まれない。
 */
class ImportDataUseCase @Inject constructor(
    private val setupRepository: SetupRepository,
    private val chassisRepository: ChassisRepository
) {

    sealed interface Result {
        /**
         * @property importedSetups     追加されたセッティング数
         * @property skippedSetups      同名スキップされたセッティング数
         * @property invalidSetups      値が範囲外で棄却されたセッティング数
         * @property importedOverrides  取り込まれた上書き数
         * @property skippedOverrides   不明シャーシでスキップされた上書き数
         * @property invalidOverrides   値が範囲外で棄却された上書き数
         */
        data class Success(
            val importedSetups: Int,
            val skippedSetups: Int,
            val invalidSetups: Int,
            val importedOverrides: Int,
            val skippedOverrides: Int,
            val invalidOverrides: Int
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

        // ---- セッティングの取り込み ----
        var skippedSetups = 0
        var invalidSetups = 0
        val setupsToInsert = mutableListOf<SavedSetup>()
        // ファイル内に同名が複数あると一括 insert がユニーク制約で全滅するため、
        // DB 既存分だけでなくこのファイル内の重複も見る。
        val seenNames = mutableSetOf<String>()

        data.setups.forEach { dto ->
            when {
                !dto.isWithinValidRange() -> invalidSetups++
                !seenNames.add(dto.name) -> skippedSetups++
                setupRepository.existsByName(dto.name) -> skippedSetups++
                else -> setupsToInsert += dto.toDomain()
            }
        }
        setupRepository.restoreAll(setupsToInsert)

        // ---- 上書きの取り込み（標準DBに存在するシャーシのみ） ----
        var skippedOverrides = 0
        var invalidOverrides = 0
        val overridesToInsert = mutableListOf<ChassisOverride>()
        // 同じ chassisId が複数あっても upsert(REPLACE) なので制約違反にはならないが、
        // 件数が実態とズレるので後勝ちで 1 件に畳む。
        val seenChassisIds = mutableSetOf<String>()

        data.overrides.forEach { dto ->
            when {
                !dto.isWithinValidRange() -> invalidOverrides++
                chassisRepository.getStandardChassisById(dto.chassisId) == null -> skippedOverrides++
                else -> {
                    if (!seenChassisIds.add(dto.chassisId)) {
                        overridesToInsert.removeAll { it.chassisId == dto.chassisId }
                    }
                    overridesToInsert += dto.toDomain()
                }
            }
        }
        chassisRepository.restoreAllOverrides(overridesToInsert)

        return Result.Success(
            importedSetups = setupsToInsert.size,
            skippedSetups = skippedSetups,
            invalidSetups = invalidSetups,
            importedOverrides = overridesToInsert.size,
            skippedOverrides = skippedOverrides,
            invalidOverrides = invalidOverrides
        )
    }

    // ----- 検証・変換 -----

    /** 全ての数値が GearCalculationInput の有効範囲に収まっているか */
    private fun ExportedSetupDto.isWithinValidRange(): Boolean =
        name.isNotBlank() &&
            chassisId.isNotBlank() &&
            GearCalculationInput.isValid(
                pinion = pinion,
                spur = spur,
                internalRatio = internalRatioSnapshot,
                kv = kv,
                cells = cells,
                tireMm = tireMm
            )

    /**
     * 上書きの検証。null は「このフィールドは上書きしない」の意味なので有効。
     * 値が入っている場合だけ範囲を見る。
     */
    private fun ExportedOverrideDto.isWithinValidRange(): Boolean =
        chassisId.isNotBlank() &&
            (internalRatio == null || GearCalculationInput.isValidInternalRatio(internalRatio)) &&
            (defaultTireMm == null || defaultTireMm in GearCalculationInput.TIRE_MM_RANGE)

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
