package io.github.taskengineer.rcgear.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 画面が **一度だけ** 処理すべき出来事（U-2）。
 *
 * これまでは `isDone` / `isDeleted` / `notFound` という Boolean を UiState に置き、
 * 画面側が `LaunchedEffect(state.isDone) { if (state.isDone) onNavigateBack() }` で
 * 拾っていた。これには 2 つ難がある:
 *  - 「戻る」は状態ではなく出来事。状態として持つと「戻った後も true のまま」になり、
 *    画面が再コンポーズされた経路によっては 2 回 pop しうる
 *  - フラグごとに LaunchedEffect が増え、同じ形のコードが画面ごとに写される
 */
sealed interface ScreenEvent {

    /** 用が済んだので前の画面へ戻る（保存・削除の完了、対象が見つからない） */
    data object NavigateBack : ScreenEvent
}

/**
 * [ScreenEvent] の送出口。ViewModel が `private val` で 1 つ持つ。
 *
 * 【`CalcRequestBus`（U-3 で削除）との違い】
 * あれはアプリスコープのシングルトンで、送り手と受け手が別の画面だった。
 * これは **ViewModel 1 つに対して 1 つ**で、送り手も受け手も同じ画面。
 * ライフサイクルが状態と一致しているので、グローバルな可変状態にはならない。
 *
 * バッファ付き Channel なので、画面が購読を始める前に送ったイベントも取りこぼさない
 * （`init` で「対象が無い」と分かって即 [ScreenEvent.NavigateBack] する場合がこれ）。
 */
class ScreenEvents {

    private val channel = Channel<ScreenEvent>(Channel.BUFFERED)

    val flow: Flow<ScreenEvent> = channel.receiveAsFlow()

    fun emit(event: ScreenEvent) {
        channel.trySend(event)
    }
}

/**
 * [ScreenEvent] を購読して画面側の処理につなぐ。
 *
 * @param onNavigateBack [ScreenEvent.NavigateBack] を受けたときの処理
 */
@Composable
fun ObserveEvents(
    events: Flow<ScreenEvent>,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                ScreenEvent.NavigateBack -> onNavigateBack()
            }
        }
    }
}
