package io.github.taskengineer.rcgear.feature.sheet

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.ScreenEvent
import io.github.taskengineer.rcgear.core.ui.ScreenEvents
import io.github.taskengineer.rcgear.core.ui.UiText
import io.github.taskengineer.rcgear.domain.model.SessionConditions
import io.github.taskengineer.rcgear.domain.model.SetupSheet
import io.github.taskengineer.rcgear.domain.repository.SetupSheetRepository
import io.github.taskengineer.rcgear.navigation.sheetHeaderEditRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * シートのヘッダ（名前・走行条件・ベースライン・備考）を編集する ViewModel（G-4）。
 *
 * ### なぜ値（bag）と別画面なのか
 * ヘッダは「その設定を行った条件」で、セッティングの値ではない（`SetupSheet` の KDoc）。
 * 差分（G-6）の対象は bag だけなので、気温やコース名を bag に入れると
 * 「気温が 3℃ 違います」が変更点として出てしまう。画面も分けて、その線引きを見える形にした。
 *
 * ### 範囲はここが持つ
 * bag の項目はレジストリ（`TouringSetupSchema`）が範囲を持っているが、ヘッダは
 * `SetupSheet` の固定カラムでレジストリに載っていない。そのため妥当な範囲を
 * この ViewModel の [Limits] に置く。**ヘッダ項目を足すときはここにも 1 行足す。**
 */
@HiltViewModel
class SheetHeaderEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sheetRepository: SetupSheetRepository
) : ViewModel() {

    private val sheetId: String = savedStateHandle.sheetHeaderEditRoute().sheetId

    private val _uiState = MutableStateFlow(SheetHeaderEditUiState())
    val uiState: StateFlow<SheetHeaderEditUiState> = _uiState.asStateFlow()

    private val screenEvents = ScreenEvents()

    /** 画面を閉じる等の一度きりの出来事（U-2） */
    val events: Flow<ScreenEvent> = screenEvents.flow

    private var sheet: SetupSheet? = null

    init {
        viewModelScope.launch {
            val loaded = sheetRepository.getSheet(sheetId)
            if (loaded == null) {
                screenEvents.emit(ScreenEvent.NavigateBack)
                return@launch
            }
            sheet = loaded.sheet
            // ベースラインに選べるのは同じ車の他のシート。自分自身は除く
            val candidates = sheetRepository.observeSheets(loaded.sheet.carId).first()
                .filter { it.id != sheetId }
                .map { BaselineCandidate(id = it.id, name = it.name) }
            val conditions = loaded.sheet.conditions
            // 読み込んだ値も検証に通す。取り込んだデータに範囲外の値が入っていた場合、
            // 開いた時点でエラーが出ていないと「触っていないのに保存できる」状態が残る
            val airTemp = conditions.airTempC?.toString().orEmpty()
            val trackTemp = conditions.trackTempC?.toString().orEmpty()
            val humidity = conditions.humidityPct?.toString().orEmpty()
            val bestLap = conditions.bestLapMs?.let { ms -> (ms / 1000.0).toString() }.orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    nameInput = loaded.sheet.name,
                    sessionDate = conditions.sessionDate,
                    trackInput = conditions.trackName.orEmpty(),
                    surfaceInput = conditions.surface.orEmpty(),
                    airTempInput = airTemp,
                    trackTempInput = trackTemp,
                    humidityInput = humidity,
                    bestLapInput = bestLap,
                    noteInput = loaded.sheet.note.orEmpty(),
                    baselineId = loaded.sheet.baselineId,
                    baselineCandidates = candidates,
                    airTempError = errorOf(airTemp, Limits.AIR_TEMP, R.string.sheet_header_error_air_temp),
                    trackTempError = errorOf(
                        trackTemp,
                        Limits.TRACK_TEMP,
                        R.string.sheet_header_error_track_temp
                    ),
                    humidityError = errorOf(humidity, Limits.HUMIDITY, R.string.sheet_header_error_humidity),
                    bestLapError = errorOf(bestLap, Limits.BEST_LAP_SEC, R.string.sheet_header_error_best_lap)
                )
            }
        }
    }

    // ----- 入力 -----

    fun onNameChange(value: String) = update { copy(nameInput = value) }

    fun onTrackChange(value: String) = update { copy(trackInput = value) }

    fun onSurfaceChange(value: String) = update { copy(surfaceInput = value) }

    fun onAirTempChange(value: String) = update {
        copy(
            airTempInput = value,
            airTempError = errorOf(value, Limits.AIR_TEMP, R.string.sheet_header_error_air_temp)
        )
    }

    fun onTrackTempChange(value: String) = update {
        copy(
            trackTempInput = value,
            trackTempError = errorOf(value, Limits.TRACK_TEMP, R.string.sheet_header_error_track_temp)
        )
    }

    fun onHumidityChange(value: String) = update {
        copy(
            humidityInput = value,
            humidityError = errorOf(value, Limits.HUMIDITY, R.string.sheet_header_error_humidity)
        )
    }

    fun onBestLapChange(value: String) = update {
        copy(
            bestLapInput = value,
            bestLapError = errorOf(value, Limits.BEST_LAP_SEC, R.string.sheet_header_error_best_lap)
        )
    }

    fun onNoteChange(value: String) = update { copy(noteInput = value) }

    fun onBaselineSelect(id: String?) = update { copy(baselineId = id) }

    fun onDatePickerOpen() = update { copy(isDatePickerOpen = true) }

    fun onDatePickerDismiss() = update { copy(isDatePickerOpen = false) }

    /**
     * 日付が選ばれた。
     *
     * `DatePicker` が返すのは **UTC の 0 時**。そのまま保存すると UTC より西の地域で
     * 前日として表示される（`formatDate()` は端末の時差で読む）。
     * 選ばれた「日付」を端末時間の正午に置き直してから保存する。
     */
    fun onDatePicked(utcMillis: Long) {
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val localNoon = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        update { copy(sessionDate = localNoon, isDatePickerOpen = false) }
    }

    fun onDateClear() = update { copy(sessionDate = null) }

    // ----- 保存 -----

    fun onSave() {
        val state = _uiState.value
        val current = sheet ?: return
        // 画面側もボタンを無効にしているが、ここでも止める（最後の関所）
        if (!state.canSave) return
        val name = state.nameInput.trim()
        val airTemp = state.airTempInput.parseOptionalDouble(Limits.AIR_TEMP) ?: return
        val trackTemp = state.trackTempInput.parseOptionalDouble(Limits.TRACK_TEMP) ?: return
        val humidity = state.humidityInput.parseOptionalDouble(Limits.HUMIDITY) ?: return
        val bestLapSec = state.bestLapInput.parseOptionalDouble(Limits.BEST_LAP_SEC) ?: return

        viewModelScope.launch {
            sheetRepository.updateSheet(
                current.copy(
                    name = name,
                    baselineId = state.baselineId,
                    note = state.noteInput.trim().takeIf { it.isNotEmpty() },
                    conditions = SessionConditions(
                        sessionDate = state.sessionDate,
                        trackName = state.trackInput.trim().takeIf { it.isNotEmpty() },
                        surface = state.surfaceInput.trim().takeIf { it.isNotEmpty() },
                        airTempC = airTemp.value,
                        trackTempC = trackTemp.value,
                        humidityPct = humidity.value?.roundToInt(),
                        bestLapMs = bestLapSec.value?.let { sec -> (sec * 1000).roundToInt() }
                    )
                )
            )
            screenEvents.emit(ScreenEvent.NavigateBack)
        }
    }

    // ----- 内部 -----

    private fun update(transform: SheetHeaderEditUiState.() -> SheetHeaderEditUiState) {
        _uiState.update { it.transform() }
    }

    /**
     * 入力のたびに走る検証（BUG-7。`ChassisEditViewModel` と同じ規則）。
     *
     * 走行条件は**すべて任意項目**なので、空欄は違反ではなく「値なし」。
     * 数値にならない・範囲外のときだけ [messageRes] のエラーを返す。
     */
    private fun errorOf(
        text: String,
        range: ClosedFloatingPointRange<Double>,
        @StringRes messageRes: Int
    ): UiText? = if (text.parseOptionalDouble(range) == null) UiText.Res(messageRes) else null

    /**
     * 空欄なら「値なし」、数値かつ範囲内なら値、それ以外は `null`（＝入力エラー）。
     *
     * 「値なし」と「エラー」を区別するために [Parsed] で包んでいる。
     * `Double?` を返すと、空欄とエラーがどちらも null になって区別できない。
     */
    private fun String.parseOptionalDouble(range: ClosedFloatingPointRange<Double>): Parsed? {
        val text = trim()
        if (text.isEmpty()) return Parsed(null)
        val number = text.toDoubleOrNull() ?: return null
        if (number !in range) return null
        return Parsed(number)
    }

    private data class Parsed(val value: Double?)

    /** ヘッダ項目の妥当な範囲。レジストリの外なのでここが唯一の定義 */
    private object Limits {
        val AIR_TEMP = -20.0..60.0
        val TRACK_TEMP = -20.0..80.0
        val HUMIDITY = 0.0..100.0

        /** ラップタイム[秒]。10 分を超えるラップはツーリングでは無い */
        val BEST_LAP_SEC = 0.1..600.0
    }
}

/** ベースラインに選べるシート */
data class BaselineCandidate(val id: String, val name: String)

/**
 * 走行条件は入力途中を表現したいので **すべて文字列で持つ**
 * （`Double` にすると "-" や "2." の途中状態が消える）。
 * 走行日だけは日付ピッカーで選ぶので epoch millis のまま。
 */
data class SheetHeaderEditUiState(
    val isLoading: Boolean = true,
    val nameInput: String = "",
    val sessionDate: Long? = null,
    val trackInput: String = "",
    val surfaceInput: String = "",
    val airTempInput: String = "",
    val trackTempInput: String = "",
    val humidityInput: String = "",
    val bestLapInput: String = "",
    val noteInput: String = "",
    val baselineId: String? = null,
    val baselineCandidates: List<BaselineCandidate> = emptyList(),
    val isDatePickerOpen: Boolean = false,
    val airTempError: UiText? = null,
    val trackTempError: UiText? = null,
    val humidityError: UiText? = null,
    val bestLapError: UiText? = null
) {
    /**
     * 保存できるか（BUG-7 と同じ規約。HANDOFF §5.9）。
     *
     * 走行条件はすべて任意なので、空欄は保存を止めない。
     * 必須はシート名だけで、こちらは自由文字列なので「埋まっているか」だけを見る。
     */
    val canSave: Boolean
        get() = airTempError == null && trackTempError == null &&
            humidityError == null && bestLapError == null &&
            nameInput.isNotBlank()
}
