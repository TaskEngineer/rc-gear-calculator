package io.github.taskengineer.rcgear.domain.backup

import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.SavedSetup

/**
 * バックアップ 1 ファイル分の中身（REF-2 / S-5）。
 *
 * ワイヤ形式（JSON）とは独立したドメイン表現。`SavedSetup.id` は端末固有なので
 * エンコード時に捨てられ、デコード時は 0（未採番）で埋められる。
 */
data class BackupData(
    val exportedAt: Long,
    val setups: List<SavedSetup> = emptyList(),
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
