package io.github.taskengineer.rcgear.feature.config

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.core.ui.formatSpeed
import io.github.taskengineer.rcgear.data.local.file.JsonFileDataSource
import io.github.taskengineer.rcgear.domain.model.ThemeMode
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.PreferencesRepository
import io.github.taskengineer.rcgear.domain.usecase.ExportDataUseCase
import io.github.taskengineer.rcgear.domain.usecase.ImportDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CONFIG 画面の ViewModel（PLAN Step 11）。
 *
 * - DISPLAY: テーマ / mph 併記 / アニメーション
 * - CALC_TUNING: 基準 FDR
 * - DATA: エクスポート / インポート（SAF）/ 全データ削除
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val chassisRepository: ChassisRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val jsonFileDataSource: JsonFileDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _uiState.update { it.copy(isLoading = false, preferences = prefs) }
            }
        }
    }

    // ----- DISPLAY -----

    fun onThemeDialogOpen() = _uiState.update { it.copy(showThemeDialog = true) }
    fun onThemeDialogDismiss() = _uiState.update { it.copy(showThemeDialog = false) }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            _uiState.update { it.copy(showThemeDialog = false) }
        }
    }

    fun onShowMphChange(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowMphAlongside(show) }
    }

    fun onAnimationEnabledChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAnimationEnabled(enabled) }
    }

    // ----- CALC_TUNING: 基準 FDR -----

    fun onBalanceFdrDialogOpen() {
        _uiState.update {
            it.copy(
                balanceFdrInput = it.preferences.balanceFdr.formatSpeed(),
                showBalanceFdrDialog = true,
                balanceFdrError = null
            )
        }
    }

    fun onBalanceFdrDialogDismiss() = _uiState.update { it.copy(showBalanceFdrDialog = false) }

    fun onBalanceFdrInputChange(value: String) {
        _uiState.update { it.copy(balanceFdrInput = value, balanceFdrError = null) }
    }

    fun onBalanceFdrConfirm() {
        val value = _uiState.value.balanceFdrInput.trim().toDoubleOrNull()
        if (value == null || value <= 0.0) {
            _uiState.update {
                it.copy(balanceFdrError = UiText.Res(R.string.config_balance_fdr_error))
            }
            return
        }
        viewModelScope.launch {
            preferencesRepository.setBalanceFdr(value)
            _uiState.update { it.copy(showBalanceFdrDialog = false) }
        }
    }

    // ----- DATA: エクスポート / インポート（SAF Uri は UI 層から渡される） -----

    fun onExportToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                jsonFileDataSource.writeText(uri, exportDataUseCase())
                _uiState.update { it.copy(message = UiText.Res(R.string.config_export_done)) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        message = UiText.Res(
                            R.string.config_export_failed,
                            listOf(e.message.orEmpty())
                        )
                    )
                }
            }
        }
    }

    fun onImportFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                when (val result = importDataUseCase(jsonFileDataSource.readText(uri))) {
                    is ImportDataUseCase.Result.Success -> _uiState.update {
                        it.copy(message = importedMessage(result))
                    }

                    ImportDataUseCase.Result.InvalidFormat -> _uiState.update {
                        it.copy(message = UiText.Res(R.string.config_import_failed_format))
                    }

                    ImportDataUseCase.Result.UnsupportedVersion -> _uiState.update {
                        it.copy(message = UiText.Res(R.string.config_import_failed_version))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        message = UiText.Res(
                            R.string.config_import_failed,
                            listOf(e.message.orEmpty())
                        )
                    )
                }
            }
        }
    }

    // ----- DATA: 全データ削除 -----

    fun onDeleteAllClick() = _uiState.update { it.copy(showDeleteAllConfirm = true) }
    fun onDeleteAllDismiss() = _uiState.update { it.copy(showDeleteAllConfirm = false) }

    fun onDeleteAllConfirm() {
        viewModelScope.launch {
            // 車 / シートの削除は Repository が入る M-4 で足す
            chassisRepository.resetAllOverrides()
            preferencesRepository.clear()
            _uiState.update {
                it.copy(
                    showDeleteAllConfirm = false,
                    message = UiText.Res(R.string.config_delete_all_done)
                )
            }
        }
    }

    /** スナックバー表示後に呼ぶ */
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /**
     * インポート結果のメッセージを組み立てる（S-11）。
     *
     * 「取り込み完了: 上書き 2件（不明シャーシ 1件）」のように入れ子になるので、
     * [UiText] を書式引数に入れて画面側で解決させる。件数が 0 の注記は出さない。
     */
    private fun importedMessage(result: ImportDataUseCase.Result.Success): UiText =
        UiText.Res(
            R.string.config_import_done,
            listOf(
                result.importedOverrides,
                notes(
                    unknownChassis = result.skippedOverrides,
                    invalid = result.invalidOverrides,
                    legacyPending = result.pendingLegacySetups
                )
            )
        )

    /** 括弧付きの注記。出すものが無ければ空文字（書式引数に埋めても何も見えない） */
    private fun notes(
        unknownChassis: Int = 0,
        invalid: Int = 0,
        legacyPending: Int = 0
    ): UiText {
        val parts = buildList {
            if (legacyPending > 0) {
                add(UiText.Res(R.string.config_import_note_legacy_pending, listOf(legacyPending)))
            }
            if (unknownChassis > 0) {
                add(UiText.Res(R.string.config_import_note_unknown_chassis, listOf(unknownChassis)))
            }
            if (invalid > 0) {
                add(UiText.Res(R.string.config_import_note_invalid, listOf(invalid)))
            }
        }
        if (parts.isEmpty()) return UiText.Empty
        return UiText.Res(
            R.string.config_import_note_wrap,
            listOf(UiText.Joined(parts, R.string.list_separator))
        )
    }
}

data class ConfigUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val showThemeDialog: Boolean = false,
    val showBalanceFdrDialog: Boolean = false,
    val balanceFdrInput: String = "",
    val balanceFdrError: UiText? = null,
    val showDeleteAllConfirm: Boolean = false,
    val message: UiText? = null
)
