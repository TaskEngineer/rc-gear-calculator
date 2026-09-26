package io.github.taskengineer.rcgear.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.taskengineer.rcgear.core.designsystem.theme.RcGearTheme

/**
 * 2 つの値を左右に並べて差分を見せる行（U-1）。
 *
 * `SetupDetailScreen.SnapshotDiffCard`（「保存時 2.60 / 現在 2.70」）の一般化。
 * 旧実装は「内部減速比だけ」の専用部品だったが、比較したい軸は 3 つある:
 *  - A: シート値 vs シャーシ標準（キット標準から変えた所）
 *  - B: シート値 vs ベースラインシート（前回のセットから変えた所）
 *  - C: シート値 vs 任意シート
 * どれも「ラベル・左の値・右の値」に落ちるので、同じ行で描く。
 *
 * @param changed 差があるか。true なら右の値を強調する。
 *   値の文字列比較ではなく呼び出し側（`SheetDiff`）の判定を使う
 *   （"2.6" と "2.60" のような整形差で誤検知させない）
 */
@Composable
fun ValueDiffRow(
    label: String,
    left: String?,
    right: String?,
    modifier: Modifier = Modifier,
    changed: Boolean = left != right
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.4f)
        )
        Text(
            text = left ?: EMPTY,
            style = valueStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = right ?: EMPTY,
            style = valueStyle(),
            color = if (changed) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 差分表の見出し行。左右の列が「いつの値」なのかを出す。
 */
@Composable
fun ValueDiffHeader(
    leftLabel: String,
    rightLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "", modifier = Modifier.weight(1.4f))
        Text(
            text = leftLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = rightLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun valueStyle() = RcGearTheme.extendedTypography.hudUnit.copy(
    fontSize = MaterialTheme.typography.bodyMedium.fontSize
)

/** 値が無い（そのシートに項目が無い）ときの表示 */
private const val EMPTY = "—"

@Preview(name = "ValueDiffRow", showBackground = true)
@Composable
private fun ValueDiffRowPreview() {
    ComponentPreview {
        RcCard(title = "キット標準との差") {
            ValueDiffHeader(leftLabel = "標準", rightLabel = "このシート")
            ValueDiffRow(label = "内部減速比", left = "2.60", right = "2.70")
            ValueDiffRow(label = "タイヤ径", left = "63mm", right = "63mm")
            ValueDiffRow(label = "キャンバー（F）", left = null, right = "-1.5°")
        }
    }
}
