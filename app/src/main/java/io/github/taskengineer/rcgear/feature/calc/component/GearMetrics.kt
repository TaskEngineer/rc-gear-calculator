package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.designsystem.component.Metric
import io.github.taskengineer.rcgear.core.ui.formatDecimals
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.core.ui.formatRpm
import io.github.taskengineer.rcgear.core.ui.formatVoltage
import io.github.taskengineer.rcgear.domain.model.GearCalculationResult

/**
 * 計算結果を [Metric] のリストに整形する（U-1）。
 *
 * `MetricsGrid` を designsystem に移したので、「何を出すか」はこの CALC 固有の関数が持つ。
 * 表示桁は PLAN 9.1 に従う（減速比: 小数2桁 / RPM: 整数 / 電圧: 小数1桁）。
 *
 * @param result null（シャーシ未選択）のときはラベルだけのリストを返す。
 *   グリッドの高さが変わらないので、シャーシを選んだ瞬間にレイアウトが跳ねない。
 */
@Composable
fun gearMetrics(result: GearCalculationResult?): List<Metric> = listOf(
    Metric(
        label = stringResource(R.string.metric_primary_ratio),
        value = result?.primaryRatio?.formatRatio()
    ),
    Metric(
        label = stringResource(R.string.metric_fdr),
        value = result?.finalDriveRatio?.formatRatio()
    ),
    Metric(
        label = stringResource(R.string.metric_voltage),
        value = result?.voltage?.formatVoltage(),
        unit = stringResource(R.string.unit_volt)
    ),
    Metric(
        label = stringResource(R.string.metric_motor_rpm),
        value = result?.motorRpm?.formatRpm()
    ),
    Metric(
        label = stringResource(R.string.metric_wheel_rpm),
        value = result?.wheelRpm?.formatRpm()
    ),
    Metric(
        label = stringResource(R.string.metric_rollout),
        value = result?.rolloutMm?.formatDecimals(1),
        unit = stringResource(R.string.unit_millimeter)
    )
)
