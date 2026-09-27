package io.github.taskengineer.rcgear.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.Maker

/**
 * シャーシ選択ボトムシート（PLAN 5.2）。
 * メーカー見出し + 所属シャーシのリストを縦に並べる（2段階プルダウンの置き換え）。
 *
 * CALC 画面（シャーシの選択）と GARAGE の車編集（車のシャーシ指定）で共有する。
 * G-1 で `feature/calc/component` から `core/ui` へ移した — ドメイン（[Maker] / [Chassis]）を
 * 知っている部品なので designsystem ではなくこちら。
 *
 * @param makers            表示する全メーカー（上書き合成済み）
 * @param selectedChassisId 現在選択中のシャーシID。ハイライト表示に使う
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChassisSelectBottomSheet(
    makers: List<Maker>,
    selectedChassisId: String?,
    onChassisSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            makers.forEach { maker ->
                item(key = "maker_${maker.name}") {
                    Text(
                        text = maker.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(maker.chassis, key = { it.id }) { chassis ->
                    ChassisRow(
                        chassis = chassis,
                        isSelected = chassis.id == selectedChassisId,
                        onClick = { onChassisSelected(chassis.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChassisRow(
    chassis: Chassis,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chassis.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (chassis.isUserEdited) {
                    Text(
                        text = stringResource(R.string.badge_user_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            chassis.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = chassis.internalRatio.formatRatio(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
