package io.github.taskengineer.rcgear.domain.diff

import io.github.taskengineer.rcgear.domain.model.ChassisDefaults
import io.github.taskengineer.rcgear.domain.model.SetupValue
import io.github.taskengineer.rcgear.domain.model.SetupValues
import io.github.taskengineer.rcgear.domain.model.initialValues
import io.github.taskengineer.rcgear.domain.schema.FieldDef
import io.github.taskengineer.rcgear.domain.schema.TouringSetupSchema

/**
 * シート同士の差分（M-5）。**3 つの比較軸が全部この 1 つの純粋関数に落ちる。**
 *
 * | 軸 | 意味 | 入口 |
 * |---|---|---|
 * | A | シート値 vs シャーシ標準（キット標準から変えた所） | [compareToChassisDefault] |
 * | B | シート値 vs ベースラインシート（**前回のセットから何を変えたか**） | [compare] |
 * | C | シート値 vs 任意シート（セッティング比較） | [compare] |
 *
 * 旧 `internalRatioSnapshot` + `SnapshotDiffCard`（「保存時 2.60 / 現在 2.70」）は、
 * 「値が絶対値で保存されている」性質の特殊ケースだった。bag は常に絶対値なので
 * 全フィールドが構造的にスナップショットになり、専用カラムは消えてこの関数に一般化される（計画 §4.5）。
 *
 * 文言は持たない。表示は `:app` の `ValueDiffRow` が [FieldDiff] から組み立てる。
 */
object SheetDiff {

    /**
     * 2 つの束を比べる。
     *
     * 並び順はレジストリの宣言順で、レジストリに無いキー（未知キー）はキー名順で末尾に付く。
     * 「どちらにも無い項目」は結果に入らない。
     *
     * @param left       比較の主役（いま見ているシート）
     * @param right      比較相手（ベースライン / シャーシ標準 / 任意のシート）
     * @param includeSame `true` なら一致した項目も [DiffKind.SAME] として返す。
     *   既定の `false` は「変わった所だけ」を出す用途（軸 B の本命）に合わせている
     */
    fun compare(left: SetupValues, right: SetupValues, includeSame: Boolean = false): List<FieldDiff> {
        val known = TouringSetupSchema.allFields.mapNotNull { field ->
            diffOf(field.key, field, left[field.key], right[field.key], includeSame)
        }
        val unknown = (left.unknownKeys() + right.unknownKeys())
            .sorted()
            .mapNotNull { key -> diffOf(key, field = null, left[key], right[key], includeSame) }
        return known + unknown
    }

    /**
     * 軸 A: シャーシ標準との差分。
     *
     * 「標準」は新規シートを起こしたときの初期値（シャーシ DB の内部減速比・タイヤ径 +
     * レジストリの既定値）と定義する。同梱シャーシ DB はキット標準の歯数を構造化して
     * 持っていない（`note` の自由文だけ）ので、そこまでは見られない。
     */
    fun compareToChassisDefault(
        values: SetupValues,
        defaults: ChassisDefaults,
        includeSame: Boolean = false
    ): List<FieldDiff> = compare(values, TouringSetupSchema.initialValues(defaults), includeSame)

    /** 変更があったかどうかだけ知りたいとき */
    fun hasDifference(left: SetupValues, right: SetupValues): Boolean =
        compare(left, right).isNotEmpty()

    private fun diffOf(
        fieldKey: String,
        field: FieldDef?,
        left: SetupValue?,
        right: SetupValue?,
        includeSame: Boolean
    ): FieldDiff? {
        val kind = when {
            left == null && right == null -> return null
            left == null -> DiffKind.REMOVED
            right == null -> DiffKind.ADDED
            valuesEqual(left, right) -> DiffKind.SAME
            else -> DiffKind.CHANGED
        }
        if (kind == DiffKind.SAME && !includeSame) return null
        return FieldDiff(fieldKey, field, left, right, kind)
    }

    /**
     * 値の同値判定。
     *
     * `IntV(22)` と `DecimalV(22.0)` は同じ値として扱う。
     * JSON を往復すると整数が小数になりうるので、ここで型の違いを差分に出すと
     * 「エクスポートして読み戻しただけで全項目が変更扱い」になってしまう。
     */
    private fun valuesEqual(left: SetupValue, right: SetupValue): Boolean {
        val leftNumber = left.asNumberOrNull()
        val rightNumber = right.asNumberOrNull()
        if (leftNumber != null && rightNumber != null) return leftNumber == rightNumber
        return left == right
    }

    private fun SetupValue.asNumberOrNull(): Double? = when (this) {
        is SetupValue.IntV -> value.toDouble()
        is SetupValue.DecimalV -> value
        else -> null
    }
}

/**
 * 1 項目ぶんの差分。
 *
 * @property fieldKey 項目のキー
 * @property field    項目の定義。**未知キー（レジストリに無い）なら `null`**。
 *   表示側はキーをそのまま出して読み取り専用で扱う
 * @property left     主役側の値。無ければ `null`
 * @property right    相手側の値。無ければ `null`
 */
data class FieldDiff(
    val fieldKey: String,
    val field: FieldDef?,
    val left: SetupValue?,
    val right: SetupValue?,
    val kind: DiffKind
) {
    /**
     * レジストリに無い項目か（将来の版で追加された項目が入ったデータを読んだ場合）。
     *
     * `this.` が要る: アクセサの中の `field` は裏フィールドを指す予約語で、
     * そのままだと「初期化が必要なプロパティ」とみなされてコンパイルが通らない。
     */
    val isUnknownField: Boolean get() = this.field == null
}

/** 差分の種類。主役（left）から見た言い方にしてある */
enum class DiffKind {
    /** 両方にあって値が違う */
    CHANGED,

    /** 主役にだけある（相手では空欄） */
    ADDED,

    /** 相手にだけある（主役で空欄になった） */
    REMOVED,

    /** 両方にあって同じ値 */
    SAME
}
