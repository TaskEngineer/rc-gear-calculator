package io.github.taskengineer.rcgear.domain.usecase

import io.github.taskengineer.rcgear.domain.backup.BackupCodec
import io.github.taskengineer.rcgear.domain.backup.BackupData
import io.github.taskengineer.rcgear.domain.common.TransactionRunner
import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.UserChassis
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.domain.validation.FieldValidator
import javax.inject.Inject

/**
 * バックアップからデータを取り込む（PLAN Step 11 / M-6）。
 *
 * ワイヤ形式のデコードと v1 → v2 の変換は [BackupCodec] が担当し、
 * この UseCase は **取り込みポリシーだけ**を持つ（REF-2 / S-5）。
 * v1 ファイルも v2 ファイルも、ここに届く時点では同じ形になっている。
 *
 * ### マージ方針（既存データを壊さない）
 * - 車 / シート: **id（UUID）による upsert**。同じファイルを 2 回読んでも増えない。
 *   v1 の「同名スキップ」はリネームで往復不能になる奇妙な挙動だったので捨てた
 * - 上書き: chassisId 単位で upsert。標準 DB に無い chassisId はスキップ
 * - ユーザー定義シャーシ（F-5）: id による upsert。**車より先に入れる** —
 *   車の `chassisId` がこれを指しているため、後にすると取り込んだ車が
 *   「不明なシャーシ」になる
 *
 * ### 値の検証（REF-1 / BUG-2）
 * JSON は手で書き換えられるので、値が範囲に収まっている保証がない。
 * **上書きは行ごと棄却、シートの値は項目単位で落とす**という非対称な扱いにしてある:
 * 上書きは 1 件＝ 1 つの値なので「棄却」と「値を捨てる」が同義だが、シートは
 * 数十項目の集まりなので、1 項目の範囲外で 1 セッション分を丸ごと捨てるのは損が大きい。
 * 落とした項目数は [Result.Success.droppedValues] で報告する。
 *
 * **レジストリに無いキーは検証対象外で、そのまま保存する。** 将来の版で追加された
 * 項目が入ったファイルを読んでも値が消えないことが、スキーマを安全に進化させる担保になる。
 *
 * ### 原子性（BUG-3 / M-8）
 * 検証を通った行は Repository の一括メソッドでまとめて書き込み、さらに
 * **ファイル 1 つの取り込み全体を 1 トランザクション**で囲む（[TransactionRunner]）。
 * 車・シート・上書きは別々の Repository なので、囲まないと
 * 「車とシートは入ったが上書きの途中で失敗」という半端な状態が残りうる。
 * 検証（どの行を捨てるか）はトランザクションの外で済ませ、中では書き込みしかしない。
 */
class ImportDataUseCase @Inject constructor(
    private val carRepository: CarRepository,
    private val sheetRepository: SetupSheetRepository,
    private val chassisRepository: ChassisRepository,
    private val codec: BackupCodec,
    private val transactionRunner: TransactionRunner
) {

    sealed interface Result {
        /**
         * @property importedCars    取り込まれた車の台数
         * @property skippedCars     シャーシが不明で飛ばした車の台数
         * @property importedSheets  取り込まれたシートの枚数
         * @property skippedSheets   車が見つからず飛ばしたシートの枚数
         * @property droppedValues   値が不正で落とした項目数（シート自体は取り込む）
         * @property importedOverrides 取り込まれた上書き数
         * @property skippedOverrides  不明シャーシでスキップされた上書き数
         * @property invalidOverrides  値が範囲外で棄却された上書き数
         * @property importedUserChassis 取り込まれたユーザー定義シャーシ数（F-5）
         * @property skippedUserChassis  id 規約違反・値が範囲外で棄却した数
         */
        data class Success(
            val importedCars: Int = 0,
            val skippedCars: Int = 0,
            val importedSheets: Int = 0,
            val skippedSheets: Int = 0,
            val droppedValues: Int = 0,
            val importedOverrides: Int = 0,
            val skippedOverrides: Int = 0,
            val invalidOverrides: Int = 0,
            val importedUserChassis: Int = 0,
            val skippedUserChassis: Int = 0
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
        return transactionRunner { import(data) }
    }

    private suspend fun import(data: BackupData): Result {
        // ---- ユーザー定義シャーシ（車より先。車の chassisId が指している） ----
        var skippedUserChassis = 0
        val userChassisToInsert = mutableListOf<UserChassis>()
        for (chassis in data.userChassis.distinctBy { it.id }) {
            if (chassis.isImportable()) {
                userChassisToInsert += chassis
            } else {
                skippedUserChassis++
            }
        }
        chassisRepository.restoreAllUserChassis(userChassisToInsert)

        // ---- 車 ----
        var skippedCars = 0
        val carsToInsert = mutableListOf<Car>()
        for (car in data.cars.distinctBy { it.id }) {
            if (car.id.isBlank() || car.name.isBlank() || !isKnownChassis(car.chassisId)) {
                skippedCars++
            } else {
                carsToInsert += car
            }
        }
        carRepository.restoreAll(carsToInsert)

        // ---- シート ----
        // 親が居ないシートは外部キーで弾かれるので、取り込む車と既存の車の両方を見る
        val availableCarIds = carsToInsert.map { it.id }.toMutableSet()
        availableCarIds += carRepository.getAllOnce().map { it.id }

        var skippedSheets = 0
        var droppedValues = 0
        val sheetsToInsert = mutableListOf<SetupSheetWithValues>()
        for (sheet in data.sheets.distinctBy { it.id }) {
            if (sheet.id.isBlank() || sheet.sheet.carId !in availableCarIds) {
                skippedSheets++
                continue
            }
            val cleaned = FieldValidator.coerceAllOrDrop(sheet.values)
            droppedValues += sheet.values.size - cleaned.size
            sheetsToInsert += sheet.copy(values = cleaned)
        }
        // ベースラインの参照先が取り込まれないと外部キー違反になるので、その場合は外す
        val sheetIds = sheetsToInsert.map { it.id }.toSet() +
            sheetRepository.getAllOnce().map { it.id }
        val safeSheets = sheetsToInsert.map { sheet ->
            if (sheet.sheet.baselineId != null && sheet.sheet.baselineId !in sheetIds) {
                sheet.copy(sheet = sheet.sheet.copy(baselineId = null))
            } else {
                sheet
            }
        }
        sheetRepository.restoreAll(safeSheets)

        // ---- 上書き（標準DBに存在するシャーシのみ） ----
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
            importedUserChassis = userChassisToInsert.size,
            skippedUserChassis = skippedUserChassis,
            importedCars = carsToInsert.size,
            skippedCars = skippedCars,
            importedSheets = safeSheets.size,
            skippedSheets = skippedSheets,
            droppedValues = droppedValues,
            importedOverrides = overridesToInsert.size,
            skippedOverrides = skippedOverrides,
            invalidOverrides = invalidOverrides
        )
    }

    // ----- 検証 -----

    /**
     * 同梱シャーシ DB に居るか。`user_` で始まる id はユーザー定義シャーシ（F-5）なので通す。
     *
     * **その定義が同じファイルに入っていなくても通す。** 定義だけ先に消した
     * バックアップを読んだときに「車ごと消える」より、「不明なシャーシの車」として
     * 残るほうが復旧できる（一覧は id をそのまま出す）。
     */
    private suspend fun isKnownChassis(chassisId: String): Boolean = when {
        chassisId.isBlank() -> false
        UserChassis.isUserDefined(chassisId) -> true
        else -> chassisRepository.getStandardChassisById(chassisId) != null
    }

    /**
     * ユーザー定義シャーシの検証。
     *
     * id の接頭辞は**規約**（同梱 DB と衝突させない）なので、違反したら取り込まない。
     * 通すと同梱エントリを名前で上書きするような id が作れてしまう。
     */
    private fun UserChassis.isImportable(): Boolean =
        UserChassis.isUserDefined(id) &&
            name.isNotBlank() &&
            GearCalculationInput.isValidInternalRatio(internalRatio) &&
            defaultTireMm in GearCalculationInput.TIRE_MM_RANGE

    /**
     * 上書きの検証。null は「このフィールドは上書きしない」の意味なので有効。
     * 値が入っている場合だけ範囲を見る。
     */
    private fun ChassisOverride.isWithinValidRange(): Boolean =
        chassisId.isNotBlank() &&
            (internalRatio == null || GearCalculationInput.isValidInternalRatio(internalRatio)) &&
            (defaultTireMm == null || defaultTireMm in GearCalculationInput.TIRE_MM_RANGE)

    /**
     * 不正な項目を**丸めずに落とす**。
     * 勝手に丸めた値をユーザーの保存データとして残す方が不親切なので、
     * `coerceAll`（丸める）ではなくこちらを使う。未知キーはそのまま通る。
     */
    private fun FieldValidator.coerceAllOrDrop(values: SetupValues): SetupValues {
        val violations = validateAll(values).map { it.fieldKey }.toSet()
        if (violations.isEmpty()) return values
        return SetupValues(values.map.filterKeys { it !in violations })
    }
}
