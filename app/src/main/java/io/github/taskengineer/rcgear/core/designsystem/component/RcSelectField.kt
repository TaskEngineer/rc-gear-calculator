package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.taskengineer.rcgear.R

/**
 * 選択肢から 1 つ選ぶフィールド（U-1）。
 *
 * ダンパーオイル番手・ピストン穴数・タイヤ銘柄のような **決まった選択肢**に使う。
 * 選択肢は「表示ラベル」と「保存する値」が別（保存は英数キー、表示は日本語）なので、
 * 呼び出し側が [Option] のリストで両方を渡す。
 *
 * @param selectedKey 現在選択中のキー。null なら未選択
 * @param onSelect    選ばれたキーを返す
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RcSelectField(
    label: String,
    selectedKey: String?,
    options: List<Option>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.value_unset)
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.key == selectedKey }?.label

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.key)
                    }
                )
            }
        }
    }
}

/**
 * 選択肢 1 件。
 *
 * @property key   保存される値。エクスポート JSON にもこのまま入るので変更不可の識別子
 * @property label 画面に出す文字列
 */
data class Option(
    val key: String,
    val label: String
)

@Preview(name = "RcSelectField", showBackground = true)
@Composable
private fun RcSelectFieldPreview() {
    ComponentPreview {
        RcSelectField(
            label = "ダンパーオイル（F）",
            selectedKey = "400",
            options = listOf(
                Option("300", "#300"),
                Option("400", "#400"),
                Option("500", "#500")
            ),
            onSelect = {}
        )
        RcSelectField(
            label = "ピストン（F）",
            selectedKey = null,
            options = listOf(Option("1x1.4", "1穴 φ1.4"), Option("2x1.1", "2穴 φ1.1")),
            onSelect = {}
        )
    }
}
