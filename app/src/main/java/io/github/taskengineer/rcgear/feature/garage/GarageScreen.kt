package io.github.taskengineer.rcgear.feature.garage

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

/**
 * GARAGE タブ: 車の一覧（G-1）。SETUPS（M-3 で撤去）の後継。
 *
 * - フィルタータブ（使用中 / アーカイブ）
 * - 車カードのタップで編集画面へ（シート一覧に変わるのは G-2）
 * - 右下の FAB で新規作成
 *
 * FAB をこの画面の中に置いているのは、シェル（`RcGearApp`）の Scaffold が
 * TopAppBar とボトムナビだけを持ち、画面ごとのアクションを受け取らないため。
 * NavHost には既にシェルの `innerPadding` が入っているので、FAB はボトムナビの上に載る。
 */
@Composable
fun GarageScreen(
    onCarClick: (String) -> Unit,
    onAddCarClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.filter.ordinal) {
            GarageFilter.entries.forEach { filter ->
                Tab(
                    selected = state.filter == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    text = {
                        val label = stringResource(filter.labelRes)
                        Text(
                            if (filter == GarageFilter.ARCHIVED && state.archivedCount > 0) {
                                stringResource(R.string.garage_filter_count, label, state.archivedCount)
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

                state.cars.isEmpty() -> {
                    Text(
                        text = stringResource(
                            if (state.filter == GarageFilter.ARCHIVED) {
                                R.string.garage_empty_archived
                            } else {
                                R.string.garage_empty
                            }
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 末尾の余白は FAB がリストの最後のカードを隠さないためのもの
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.cars, key = { it.id }) { car ->
                            CarCard(car = car, onClick = { onCarClick(car.id) })
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onAddCarClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.garage_add_car)
                )
            }
        }
    }
}

@Composable
private fun CarCard(
    car: GarageCarItem,
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
                        text = car.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (car.isArchived) {
                        Text(
                            text = stringResource(R.string.badge_archived),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Text(
                    text = car.chassis?.let { chassis ->
                        listOf(chassis.makerName, chassis.name)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    } ?: stringResource(R.string.garage_chassis_unknown, car.chassisId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                car.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            car.chassis?.let { chassis ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = chassis.internalRatio.formatRatio(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
}
