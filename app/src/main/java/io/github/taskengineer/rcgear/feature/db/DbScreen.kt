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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.taskengineer.rcgear.R
import io.github.taskengineer.rcgear.core.ui.formatRatio
import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.UserChassis

/**
 * DB タブ: シャーシDB管理画面（PLAN Step 10）。
 *
 * - フィルタータブ（すべて / 編集済み）
 * - メーカーごとにグルーピング表示
 * - ユーザー編集済みエントリは「編集済」バッジで視覚的に識別
 * - エントリタップでシャーシ編集画面へ。**自作エントリ（F-5）は別の画面**に行く
 *   （同梱エントリは「上書きの差分」、自作はエントリそのものを編集するため）
 * - 右下の FAB で自作シャーシを追加
 */
@Composable
fun DbScreen(
    onChassisClick: (String) -> Unit,
    onUserChassisClick: (String) -> Unit,
    onAddChassisClick: () -> Unit,
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
                        val label = stringResource(filter.labelRes)
                        Text(
                            if (filter == DbFilter.EDITED && state.editedCount > 0) {
                                stringResource(R.string.db_filter_edited_count, label, state.editedCount)
                            } else {
                                label
                            }
                        )
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
                        text = stringResource(
                            if (state.filter == DbFilter.EDITED) {
                                R.string.db_empty_edited
                            } else {
                                R.string.db_empty
                            }
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 末尾の余白は FAB が最後のカードを隠さないためのもの
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.makers.forEach { maker ->
                            item(key = "maker_${maker.name}") {
                                Text(
                                    text = stringResource(
                                        R.string.db_maker_header,
                                        maker.name,
                                        maker.chassis.size
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(maker.chassis, key = { it.id }) { chassis ->
                                ChassisCard(
                                    chassis = chassis,
                                    onClick = {
                                        if (UserChassis.isUserDefined(chassis.id)) {
                                            onUserChassisClick(chassis.id)
                                        } else {
                                            onChassisClick(chassis.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onAddChassisClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.db_add_chassis)
                )
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
                    if (UserChassis.isUserDefined(chassis.id)) {
                        // 自作エントリ。「編集済」（同梱エントリへの上書き）とは別の印にする
                        Text(
                            text = stringResource(R.string.badge_user_defined),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else if (chassis.isUserEdited) {
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = chassis.internalRatio.formatRatio(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chassis.isUserEdited) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = stringResource(R.string.value_millimeter, chassis.defaultTireMm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
