package io.github.taskengineer.rcgear.feature.config

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import io.github.taskengineer.rcgear.data.local.file.JsonFileDataSource
import io.github.taskengineer.rcgear.data.repository.CalculationHistoryRepository
import io.github.taskengineer.rcgear.data.repository.ChassisRepository
import io.github.taskengineer.rcgear.data.repository.PreferencesRepository
import io.github.taskengineer.rcgear.data.repository.SetupRepository
import io.github.taskengineer.rcgear.domain.model.UserPreferences
import io.github.taskengineer.rcgear.domain.usecase.ExportDataUseCase
import io.github.taskengineer.rcgear.domain.usecase.ImportDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
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
    private val setupRepository: SetupRepository,
    private val chassisRepository: ChassisRepository,
    private val historyRepository: CalculationHistoryRepository,
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
                balanceFdrInput = String.format(Locale.US, "%.1f", it.preferences.balanceFdr),
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
            _uiState.update { it.copy(balanceFdrError = "正の数値で入力してください") }
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
                _uiState.update { it.copy(message = "データを書き出しました") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "書き出しに失敗しました: ${e.message}") }
            }
        }
    }

    fun onImportFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                when (val result = importDataUseCase(jsonFileDataSource.readText(uri))) {
                    is ImportDataUseCase.Result.Success -> _uiState.update {
                        it.copy(
                            message = buildString {
                                append("取り込み完了: ")
                                append("セッティング ${result.importedSetups}件")
                                if (result.skippedSetups > 0) append("（同名スキップ ${result.skippedSetups}件）")
                                append(" / 上書き ${result.importedOverrides}件")
                                if (result.skippedOverrides > 0) append("（不明シャーシ ${result.skippedOverrides}件）")
                            }
                        )
                    }

                    ImportDataUseCase.Result.InvalidFormat -> _uiState.update {
                        it.copy(message = "読み込めないファイル形式です")
                    }

                    ImportDataUseCase.Result.UnsupportedVersion -> _uiState.update {
                        it.copy(message = "このアプリより新しいバージョンのデータです")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "読み込みに失敗しました: ${e.message}") }
            }
        }
    }

    // ----- DATA: 全データ削除 -----

    fun onDeleteAllClick() = _uiState.update { it.copy(showDeleteAllConfirm = true) }
    fun onDeleteAllDismiss() = _uiState.update { it.copy(showDeleteAllConfirm = false) }

    fun onDeleteAllConfirm() {
        viewModelScope.launch {
            setupRepository.deleteAll()
            chassisRepository.resetAllOverrides()
            historyRepository.deleteAll()
            preferencesRepository.clear()
            _uiState.update {
                it.copy(showDeleteAllConfirm = false, message = "全データを削除しました")
            }
        }
    }

    /** スナックバー表示後に呼ぶ */
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}

data class ConfigUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val showThemeDialog: Boolean = false,
    val showBalanceFdrDialog: Boolean = false,
    val balanceFdrInput: String = "",
    val balanceFdrError: String? = null,
    val showDeleteAllConfirm: Boolean = false,
    val message: String? = null
)
