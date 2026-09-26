package io.github.taskengineer.rcgear.domain.backup

import io.github.taskengineer.rcgear.domain.model.Car
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.SetupSheetWithValues

/**
 * バックアップ 1 ファイル分の中身（REF-2 / S-5、M-6 で v2 化）。
 *
 * ワイヤ形式（JSON）とは独立したドメイン表現。
 *
 * v1 ファイルを読んだ場合、[LegacyBackupConverter] が「1 セッティング = 1 台 + 1 シート」に
 * 変換した結果がそのまま [cars] / [sheets] に入る。**UseCase から見ると v1 も v2 も同じ形**で、
 * 版の違いを知っているのは codec だけ。
 */
data class BackupData(
    val exportedAt: Long,
    val cars: List<Car> = emptyList(),
    val sheets: List<SetupSheetWithValues> = emptyList(),
    val overrides: List<ChassisOverride> = emptyList()
)

/**
 * バックアップのワイヤ形式との相互変換（REF-2 / S-5）。
 *
 * これを挟む前は Export/ImportDataUseCase が `data.local.file.dto` の
 * `@Serializable` DTO を直接組み立てており、**ドメインのユースケースが
 * ファイル形式を知っている**状態だった。取り込みポリシー（範囲検証・同名スキップ・
 * ファイル内重複の畳み込み）はドメインの判断だが、「JSON のどのキーに何が入るか」は
 * ストレージの都合なので、境界をここに引く。
 *
 * 実装は `data/local/file/JsonBackupCodec`。
 * 将来スキーマ v2 を足すときも、UseCase 側は無改造で
 * 「v1 も v2 も読めて v2 を書く codec」に差し替えられる（計画 M-6）。
 */
interface BackupCodec {

    /** 現行スキーマでエンコードする */
    fun encode(data: BackupData): String

    /** デコードする。形式不正・未対応バージョンは例外ではなく結果型で返す */
    fun decode(text: String): DecodeResult

    sealed interface DecodeResult {
        data class Success(val data: BackupData) : DecodeResult

        /** JSON 構文エラー・必須フィールド欠落 */
        data object InvalidFormat : DecodeResult

        /** schemaVersion がこのアプリより新しく解釈できない */
        data object UnsupportedVersion : DecodeResult
    }
}
