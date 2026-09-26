package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.runtime.Composable
import io.github.taskengineer.rcgear.core.designsystem.component.Metric
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
    Metric(label = "1次減速比", value = result?.primaryRatio?.formatRatio()),
    Metric(label = "最終減速比 FDR", value = result?.finalDriveRatio?.formatRatio()),
    Metric(label = "電圧", value = result?.voltage?.formatVoltage(), unit = "V"),
    Metric(label = "モーターRPM", value = result?.motorRpm?.formatRpm()),
    Metric(label = "ホイールRPM", value = result?.wheelRpm?.formatRpm())
)
