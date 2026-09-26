package io.github.taskengineer.rcgear.data.system

import io.github.taskengineer.rcgear.domain.common.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/** [TimeProvider] の実装。端末の壁時計を返す */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
