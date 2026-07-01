package io.github.taskengineer.rcgear.feature.calc

import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.GearCalculationInput
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult
import io.github.taskengineer.rcgear.domain.model.Maker

/**
 * CALC 画面の UI 状態（immutable、PLAN 9.2）。
 * すべての変更は CalcViewModel が copy() で行う。
 *
 * @property isLoading        初期化中（シャーシDB・前回状態の読込中）
 * @property makers           シャーシ選択ボトムシートに出す全メーカー（上書き合成済み）
 * @property selectedChassis  選択中のシャーシ。null = 未選択（初回起動）
 * @property result           計算結果。シャーシ未選択の間は null
 * @property isChassisSheetOpen シャーシ選択ボトムシートの表示状態
 * @property saveDialog       保存ダイアログの状態。null = 非表示
 * @property savedMessage     保存成功時のスナックバー用メッセージ。表示後にクリアする
 */
data class CalcUiState(
    val isLoading: Boolean = true,
    val makers: List<Maker> = emptyList(),
    val selectedChassis: SelectedChassis? = null,
    val pinion: Int = GearCalculationInput.DEFAULT_PINION,
    val spur: Int = GearCalculationInput.DEFAULT_SPUR,
    val kv: Int = GearCalculationInput.DEFAULT_KV,
    val cells: Int = GearCalculationInput.DEFAULT_CELLS,
    val tireMm: Int = GearCalculationInput.DEFAULT_TIRE_MM,
    val showMphAlongside: Boolean = true,
    val animationEnabled: Boolean = true,
    val balanceFdr: Double = 7.0,
    val result: GearCalculationResult? = null,
    val isChassisSheetOpen: Boolean = false,
    val saveDialog: SaveDialogState? = null,
    val savedMessage: String? = null
)

/**
 * 選択中のシャーシ。メーカー名も UI 表示に使うため合わせて持つ。
 */
data class SelectedChassis(
    val makerName: String,
    val chassis: Chassis
)

/**
 * 保存ダイアログの状態。
 *
 * @property name         入力中の保存名
 * @property errorMessage バリデーションエラー。null = エラーなし
 * @property isSaving     保存処理中（多重タップ防止）
 */
data class SaveDialogState(
    val name: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)
