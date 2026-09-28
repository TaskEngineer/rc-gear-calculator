package io.github.taskengineer.rcgear.feature.sheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.ScreenEvents
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.ChassisTraits
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.repository.CarRepository
import io.github.taskengineer.rcgear.domain.repository.ChassisRepository
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.NumberFieldDef
import io.github.taskengineer.rcgear.domain.schema.SectionDef
import io.github.taskengineer.rcgear.domain.schema.TextFieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema
import io.github.taskengineer.rcgear.domain.validation.FieldValidator
import io.github.taskengineer.rcgear.navigation.sheetEditRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * シートの値を 1 セクションだけ編集する ViewModel（G-4）。
 *
 * ### 検証の置き場所
 * 範囲・型の判定は `:core:domain` の `FieldValidator`（レジストリ駆動）に任せ、
 * ここは「文字列を値にする」「違反を [UiText] にする」「保存を止める」だけを行う。
 * 同じ検証がインポート（M-6）でも走るので、規則が 2 箇所に分かれない。
 *
 * ### 不正な入力は確定しない
 * 範囲外・数値でない入力は **下書きの文字列として保持しつつ値は確定しない**。
 * 確定してしまうと「画面には 999 と出ているのに保存されるのは 40」という
 * ズレが生まれる。違反が 1 つでもあれば保存自体を止める（画面側もボタンを無効にする）。
 *
 * ### 保存は変わったキーだけ
 * EAV（`(sheetId, fieldKey)`）なので 1 項目 1 行の upsert で済む。
 * 束ごと `replaceValues` すると、このセクションに無い項目（他セクションの値）を
 * 巻き込んで消す危険があるため、差分だけを書く。
 */
@HiltViewModel
class SheetEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sheetRepository: SetupSheetRepository,
    private val carRepository: CarRepository,
    private val chassisRepository: ChassisRepository
) : ViewModel() {

    private val route = savedStateHandle.sheetEditRoute()

    private val _uiState = MutableStateFlow(SheetEditUiState())
    val uiState: StateFlow<SheetEditUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    /** 開いた時点の値。保存時の差分計算に使う */
    private var original: SetupValues = SetupValues.EMPTY

    init {
        viewModelScope.launch {
            val section = TouringSetupSchema.sections.firstOrNull { it.key == route.sectionKey }
            val loaded = sheetRepository.getSheet(route.sheetId)
            if (section == null || loaded == null) {
                // 知らないセクションキー（古いディープリンク）か、シートが消えた
                screenEvents.emit(ScreenEvent.NavigateBack)
                return@launch
            }
            val car = carRepository.getCar(loaded.sheet.carId)
            val chassis = car?.chassisId?.let { chassisRepository.getChassisById(it) }
            original = loaded.values
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sheetName = loaded.sheet.name,
                    section = section,
                    traits = chassis?.traits ?: ChassisTraits.UNKNOWN,
                    values = loaded.values
                )
            }
        }
    }

    // ----- 入力 -----

    /** 数値欄（キーボード入力）。空欄は「値を消す」 */
    fun onNumberInput(field: NumberFieldDef, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            setValue(field, null, draft = text)
            return
        }
        val number = trimmed.toDoubleOrNull()
        if (number == null) {
            setDraftWithError(field, text, UiText.Res(R.string.sheet_edit_error_number))
            return
        }
        val candidate = SetupValue.number(number, field.decimals)
        val violation = FieldValidator.validate(field, candidate)
        if (violation != null) {
            setDraftWithError(field, text, violation.toUiText(field))
            return
        }
        setValue(field, candidate, draft = text)
    }

    /** 自由入力欄 */
    fun onTextInput(field: TextFieldDef, text: String) {
        if (text.isEmpty()) {
            setValue(field, null, draft = text)
            return
        }
        val candidate = SetupValue.TextV(text)
        val violation = FieldValidator.validate(field, candidate)
        if (violation != null) {
            setDraftWithError(field, text, violation.toUiText(field))
            return
        }
        setValue(field, candidate, draft = text)
    }

    /**
     * スライダー・ステッパー・選択肢・スイッチからの確定値。
     *
     * これらは範囲内・選択肢内の値しか作れないので検証は不要。
     * それでも `FieldValidator` を通すのは、部品の刻み計算がずれたときに
     * 黙って範囲外の値が入るのを防ぐため（保存前の最後の関所）。
     */
    fun onValueChange(field: FieldDef, value: SetupValue?) {
        if (value == null) {
            setValue(field, null, draft = null)
            return
        }
        val violation = FieldValidator.validate(field, value)
        if (violation != null) {
            setDraftWithError(field, draft = null, message = violation.toUiText(field))
            return
        }
        setValue(field, value, draft = null)
    }

    // ----- 保存 / 破棄 -----

    fun onSave() {
        val state = _uiState.value
        if (state.errors.isNotEmpty()) return
        viewModelScope.launch {
            // 変わったキーだけを 1 行ずつ upsert する。null は行ごと削除（＝空欄に戻す）
            val keys = original.keys + state.values.keys
            keys.filter { original[it] != state.values[it] }.forEach { key ->
                sheetRepository.setValue(route.sheetId, key, state.values[key])
            }
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    /** 戻る操作。未保存の変更があれば確認を出す */
    fun onBackRequest() {
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(showDiscardConfirm = true) }
        } else {
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    fun onDiscardConfirm() {
        _uiState.update { it.copy(showDiscardConfirm = false) }
        screenEvents.emit(ScreenEvent.NavigateBack)
    }

    fun onDiscardDismiss() {
        _uiState.update { it.copy(showDiscardConfirm = false) }
    }

    // ----- 内部 -----

    private fun setValue(field: FieldDef, value: SetupValue?, draft: String?) {
        _uiState.update { state ->
            val values = if (value == null) {
                state.values.without(field.key)
            } else {
                state.values.with(field.key, value)
            }
            state.copy(
                values = values,
                drafts = state.drafts.withDraft(field.key, draft),
                errors = state.errors - field.key,
                isDirty = true
            )
        }
    }

    private fun setDraftWithError(field: FieldDef, draft: String?, message: UiText) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.withDraft(field.key, draft),
                errors = state.errors + (field.key to message),
                isDirty = true
            )
        }
    }

    private fun Map<String, String>.withDraft(key: String, draft: String?): Map<String, String> =
        if (draft == null) this - key else this + (key to draft)
}

/**
 * @property section 編集対象のセクション。レジストリに無いキーで開かれた場合は null
 *   （その場合は [ScreenEvent.NavigateBack] が飛んでいる）
 * @property values  編集中の値の束。**シート全体**を持つ（保存の差分計算に使うため）
 * @property drafts  数値・テキスト欄の入力途中の文字列。`Int` で持つと
 *   「空文字」「マイナスだけ」が表現できないため（`RcNumberField` の KDoc）
 * @property errors  項目キー → インラインに出す検証メッセージ。空でなければ保存できない
 * @property isDirty 1 度でも入力があったか。戻るときの確認に使う
 */
data class SheetEditUiState(
    val isLoading: Boolean = true,
    val sheetName: String = "",
    val section: SectionDef? = null,
    val traits: ChassisTraits = ChassisTraits.UNKNOWN,
    val values: SetupValues = SetupValues.EMPTY,
    val drafts: Map<String, String> = emptyMap(),
    val errors: Map<String, UiText> = emptyMap(),
    val isDirty: Boolean = false,
    val showDiscardConfirm: Boolean = false
) {
    /** 保存できるか。違反が 1 つでもあれば止める */
    val canSave: Boolean get() = errors.isEmpty()
}
