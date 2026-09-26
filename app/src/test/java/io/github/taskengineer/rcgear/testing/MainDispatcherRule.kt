package io.github.taskengineer.rcgear.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `viewModelScope` を単体テストで動かすためのルール（REF-3 / S-6）。
 *
 * `viewModelScope` は `Dispatchers.Main` に固定されており、JVM 単体テストには
 * Main ルーパーが無いので、差し替えないと ViewModel の init すら走らない。
 *
 * [StandardTestDispatcher] を使う（Unconfined ではなく）。Unconfined だと
 * コルーチンが起動と同時に最後まで走ってしまい、「DB のロードが終わる前に
 * 流し込み要求が来る」といった**順序が関係する不具合を再現できなくなる**。
 * Standard なら `advanceUntilIdle()` を呼ぶ位置で進み方を制御できる。
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
