package io.github.taskengineer.rcgear.feature.calc.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.feature.calc.SelectedChassis

/**
 * 選択中シャーシの表示カード。タップでシャーシ選択ボトムシートを開く（PLAN 5.2）。
 *
 * 内部減速比が上書き済みの場合は「編集済」バッジを表示する（PLAN 2.1.3）。
 */
@Composable
fun ChassisSelectorCard(
    selected: SelectedChassis?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (selected == null) {
                    Text(
                        text = stringResource(R.string.calc_chassis_unselected),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.calc_chassis_unselected_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = selected.makerName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selected.chassis.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selected.chassis.isUserEdited) {
                            Text(
                                text = stringResource(R.string.badge_user_edited),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.calc_chassis_internal_ratio,
                            selected.chassis.internalRatio.formatRatio()
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.calc_chassis_unselected),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
