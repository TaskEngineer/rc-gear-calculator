package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import javax.inject.Inject

/**
 * バックアップからデータを取り込む（PLAN Step 11）。
 *
 * ワイヤ形式のデコードは [BackupCodec] が担当し、この UseCase は
 * **取り込みポリシーだけ**を持つ（REF-2 / S-5）。
 *
 * マージ方針（既存データを壊さない）:
 * - 上書き: chassisId 単位で upsert（インポート側の値を優先）。
 *   ただし標準DBに存在しない chassisId はスキップ（別アプリバージョンのデータ対策）
 *
 * 値の検証について（REF-1 / BUG-2）:
 * JSON は手で書き換えられるため、値が有効範囲に収まっている保証がない。
 * 範囲外のまま Room に入れると、後で画面を開いた時に初めて壊れる（＝取り込み時には
 * 成功に見えるのに、後から壊れる）。そのため **取り込み時に 1 行ずつ検証し、
 * 不正な行は棄却して件数で報告する**。クランプしないのは、勝手に丸めた値を
 * ユーザーの保存データとして残す方が不親切なため。
 *
 * 原子性について（BUG-3）:
 * 検証を通った行は Repository の一括メソッドでまとめて書き込む。Room は
 * コレクションを受ける @Insert を 1 トランザクションで実行するので、
 * 途中で失敗しても半端に取り込まれない。
 * **ファイル全体の原子性はまだ無い**（計画 M-8 で解消する）。
 *
 * ### v1 の保存セッティングについて（M-3 時点）
 * v1 ファイルの `setups` は [BackupCodec] が [io.github.taskengineer.rcgear.domain.backup.LegacySavedSetup]
 * として読み出すところまでは動くが、**受け皿になる車 / シートの Repository が
 * まだ無い**ため、この時点では取り込まずに件数だけ報告する。
 * v1 → v2 への変換は M-6 で入れる。
 */
class ImportDataUseCase @Inject constructor(
    private val chassisRepository: ChassisRepository,
    private val codec: BackupCodec
) {

    sealed interface Result {
        /**
         * @property pendingLegacySetups v1 の保存セッティング数。M-6 までは取り込まれない
         * @property importedOverrides   取り込まれた上書き数
         * @property skippedOverrides    不明シャーシでスキップされた上書き数
         * @property invalidOverrides    値が範囲外で棄却された上書き数
         */
        data class Success(
            val pendingLegacySetups: Int,
            val importedOverrides: Int,
            val skippedOverrides: Int,
            val invalidOverrides: Int
        ) : Result

        /** JSON 構文エラー・フォーマット不一致 */
        data object InvalidFormat : Result

        /** schemaVersion がこのアプリより新しく解釈できない */
        data object UnsupportedVersion : Result
    }

    suspend operator fun invoke(text: String): Result {
        val data = when (val decoded = codec.decode(text)) {
            is BackupCodec.DecodeResult.Success -> decoded.data
            BackupCodec.DecodeResult.InvalidFormat -> return Result.InvalidFormat
            BackupCodec.DecodeResult.UnsupportedVersion -> return Result.UnsupportedVersion
        }

        // ---- 上書きの取り込み（標準DBに存在するシャーシのみ） ----
        var skippedOverrides = 0
        var invalidOverrides = 0
        val overridesToInsert = mutableListOf<ChassisOverride>()
        // 同じ chassisId が複数あっても upsert(REPLACE) なので制約違反にはならないが、
        // 件数が実態とズレるので後勝ちで 1 件に畳む。
        val seenChassisIds = mutableSetOf<String>()

        data.overrides.forEach { override ->
            when {
                !override.isWithinValidRange() -> invalidOverrides++
                chassisRepository.getStandardChassisById(override.chassisId) == null ->
                    skippedOverrides++

                else -> {
                    if (!seenChassisIds.add(override.chassisId)) {
                        overridesToInsert.removeAll { it.chassisId == override.chassisId }
                    }
                    overridesToInsert += override
                }
            }
        }
        chassisRepository.restoreAllOverrides(overridesToInsert)

        return Result.Success(
            pendingLegacySetups = data.legacySetups.size,
            importedOverrides = overridesToInsert.size,
            skippedOverrides = skippedOverrides,
            invalidOverrides = invalidOverrides
        )
    }

    // ----- 検証 -----

    /**
     * 上書きの検証。null は「このフィールドは上書きしない」の意味なので有効。
     * 値が入っている場合だけ範囲を見る。
     */
    private fun ChassisOverride.isWithinValidRange(): Boolean =
        chassisId.isNotBlank() &&
            (internalRatio == null || GearCalculationInput.isValidInternalRatio(internalRatio)) &&
            (defaultTireMm == null || defaultTireMm in GearCalculationInput.TIRE_MM_RANGE)
}
