package io.github.taskengineer.rcgear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode
import io.github.taskengineer.rcgear.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * MainActivity 用の ViewModel。
 * DataStore からテーマモードを読み出し、RcGearTheme に流す（Step 2 の「DataStore 連動」を実現）。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    /**
     * テーマの読み込み状態。
     * Loading の間はスプラッシュを保持し、テーマ切替のチラつき
     * （デフォルトのダーク → 保存されたライトへの一瞬の変化）を防ぐ。
     */
    val uiState: StateFlow<MainUiState> = preferencesRepository.userPreferences
        .map { prefs -> MainUiState.Success(prefs.themeMode) }
        .stateIn(
            scope = viewModelScope,
            // Eagerly: Activity 再生成（画面回転）でも読み直しを走らせない
            started = SharingStarted.Eagerly,
            initialValue = MainUiState.Loading
        )
}

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val themeMode: ThemeMode) : MainUiState
}
