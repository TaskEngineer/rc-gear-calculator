package io.github.taskengineer.rcgear.feature.db

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.domain.model.Chassis
import java.util.Locale

/**
 * DB タブ: シャーシDB管理画面（PLAN Step 10）。
 *
 * - フィルタータブ（すべて / 編集済み）
 * - メーカーごとにグルーピング表示
 * - ユーザー編集済みエントリは「編集済」バッジで視覚的に識別
 * - エントリタップでシャーシ編集画面へ
 */
@Composable
fun DbScreen(
    onChassisClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DbViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.filter.ordinal) {
            DbFilter.entries.forEach { filter ->
                Tab(
                    selected = state.filter == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    text = {
                        val label = if (filter == DbFilter.EDITED && state.editedCount > 0) {
                            "${filter.label} (${state.editedCount})"
                        } else {
                            filter.label
                        }
                        Text(label)
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.makers.isEmpty() -> {
                    Text(
                        text = if (state.filter == DbFilter.EDITED) {
                            "編集済みのエントリはありません"
                        } else {
                            "シャーシDBが空です"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.makers.forEach { maker ->
                            item(key = "maker_${maker.name}") {
                                Text(
                                    text = "${maker.name}  (${maker.chassis.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(maker.chassis, key = { it.id }) { chassis ->
                                ChassisCard(
                                    chassis = chassis,
                                    onClick = { onChassisClick(chassis.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChassisCard(
    chassis: Chassis,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chassis.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (chassis.isUserEdited) {
                        Text(
                            text = "編集済",
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%.2f", chassis.internalRatio),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chassis.isUserEdited) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "${chassis.defaultTireMm}mm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
