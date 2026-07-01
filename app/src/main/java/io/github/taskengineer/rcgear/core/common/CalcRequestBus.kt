package io.github.taskengineer.rcgear.core.common

import io.github.taskengineer.rcgear.domain.model.SavedSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SETUPS → CALC の「セッティングを流し込む」特殊遷移（PLAN 5.3）用の受け渡しバス。
 *
 * 画面（ViewModel）同士を直接参照させないためのアプリスコープシングルトン。
 * - SETUPS 側: send() でセッティングを積んでから CALC タブへ遷移する
 * - CALC 側: pendingSetup を購読し、受け取ったら値を反映して consume() する
 *
 * StateFlow なので、CALC の ViewModel 生成が遷移より遅れても値は保持される。
 */
@Singleton
class CalcRequestBus @Inject constructor() {

    private val _pendingSetup = MutableStateFlow<SavedSetup?>(null)
    val pendingSetup: StateFlow<SavedSetup?> = _pendingSetup.asStateFlow()

    /** CALC に流し込みたいセッティングを積む */
    fun send(setup: SavedSetup) {
        _pendingSetup.value = setup
    }

    /** CALC 側が受け取り処理を終えたら呼ぶ */
    fun consume() {
        _pendingSetup.value = null
    }
}
