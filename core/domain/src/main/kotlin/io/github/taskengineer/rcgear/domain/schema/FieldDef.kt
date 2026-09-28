package io.github.taskengineer.rcgear.domain.schema

/**
 * セッティングシート 1 項目の定義（M-1）。
 *
 * 40〜80 項目を固定カラム / 固定 data class で持つと 1 項目増やすたびに
 * Entity・ドメインモデル・変換 2 本・Export DTO・Import・Migration…と
 * 9 箇所を直すことになる。そこで **値は `(sheetId, fieldKey)` の bag**（EAV）で持ち、
 * **項目の定義だけをこのレジストリに集約**する（HANDOFF §5.3）。
 *
 * ここに **ラベル（表示文言）は置かない**。`:core:domain` は純 Kotlin JVM モジュールで
 * `:app` が生成する `R.string` を参照できないため、文言の解決は `:app` 側の
 * `feature/sheet/SetupFieldLabels.kt` が担当する。
 * 「定義は domain、文言は `:app`」という分担で、ラベルの付け忘れは `:app` の
 * 単体テスト（`SetupFieldLabelsTest`）が検出する。
 *
 * 単位も同じ理由で文字列ではなく [FieldUnit] の enum にしてある。
 * "mm" のような記号でも、出す / 出さない・前後どちらに置くかは表示側の判断なので、
 * ドメインには「どの単位か」だけを持たせる。
 */
sealed interface FieldDef {

    /**
     * bag のキー。DB（`setup_values.fieldKey`）とエクスポート JSON にそのまま出る
     * **永続キー**なので、一度出荷したら改名しない（`chassis-db.json` の id と同じ扱い）。
     *
     * [SectionDef.columns] を持つセクションでは `"<columnKey>.<名前>"` の形にする
     * （例 `front.camberDeg`）。F/C/R のグリッド表示はこの接頭辞から組み立てる。
     * 自動展開の機構は入れない — F と R で有効範囲が実際に違うため（HANDOFF §5.3）。
     */
    val key: String

    /** 値に付く単位。表示するかどうかと記号は `:app` が決める */
    val unit: FieldUnit

    /** 入力手段。`:app` の `FieldEditor` がこれで Composable を選ぶ */
    val ui: FieldUi

    /**
     * この項目を出す条件。`null` なら常に出す。
     * シャーシの素性（シャフト車 / センターデフの有無）に対する単一キーの真偽評価だけで、
     * 汎用ルールエンジンにはしない。評価側は M-7 で入る。
     */
    val requires: ChassisTrait?
}

/**
 * 数値項目。整数と小数を [decimals] で区別する（`0` なら整数）。
 *
 * 範囲を `Double` で持つのは、整数項目と小数項目で検証コードを二重に書かないため。
 * 整数項目でも `min`/`max`/`step` は整数値を入れる（`SchemaTest` が検証する）。
 */
data class NumberFieldDef(
    override val key: String,
    override val ui: FieldUi,
    val min: Double,
    val max: Double,
    val step: Double,
    /** 小数点以下の桁数。0 = 整数として扱い、値は `SetupValue.IntV` になる */
    val decimals: Int = 0,
    override val unit: FieldUnit = FieldUnit.NONE,
    /** 新規シート作成時に入れる既定値。`null` なら空欄で始まる */
    val defaultValue: Double? = null,
    /** 既定値をシャーシ DB から引く場合の参照先。[defaultValue] より優先する */
    val defaultFrom: ChassisDefault? = null,
    override val requires: ChassisTrait? = null
) : FieldDef {

    /** 整数項目か。`SetupValue` の型と入力 UI の刻みがこれで決まる */
    val isInteger: Boolean get() = decimals == 0
}

/**
 * 選択項目。DB とエクスポートには [ChoiceOption.key] の**文字列**を保存する。
 * 表示名を変えてもデータが壊れないようにするため、序数や表示名は永続化しない。
 */
data class ChoiceFieldDef(
    override val key: String,
    val choiceSet: ChoiceSet,
    override val unit: FieldUnit = FieldUnit.NONE,
    val defaultValue: String? = null,
    override val requires: ChassisTrait? = null
) : FieldDef {
    override val ui: FieldUi get() = FieldUi.SELECT
}

/** 自由入力項目。銘柄名・ボディ名など、選択肢に閉じ込められないもの */
data class TextFieldDef(
    override val key: String,
    val maxLength: Int = 60,
    val multiline: Boolean = false,
    override val requires: ChassisTrait? = null
) : FieldDef {
    override val unit: FieldUnit get() = FieldUnit.NONE
    override val ui: FieldUi get() = if (multiline) FieldUi.TEXT_AREA else FieldUi.TEXT_FIELD
}

/** ON / OFF 項目 */
data class BoolFieldDef(
    override val key: String,
    val defaultValue: Boolean? = null,
    override val requires: ChassisTrait? = null
) : FieldDef {
    override val unit: FieldUnit get() = FieldUnit.NONE
    override val ui: FieldUi get() = FieldUi.SWITCH
}

/**
 * 選択肢 1 つ。[key] だけが永続化される識別子で、表示名は `:app` が解決する。
 *
 * @property key 永続キー。改名しない
 */
data class ChoiceOption(val key: String)

/**
 * 選択肢の集合。`damperUpperMount` の "inner" と `ackermann` の "inner" は
 * 別物なので、ラベル解決はフィールド単位ではなく**この集合単位**で行う。
 *
 * @property id ラベル解決に使う識別子。フィールドをまたいで共有する
 */
data class ChoiceSet(val id: String, val options: List<ChoiceOption>) {

    /** 値がこの集合に属するか。検証（M-2）で使う */
    fun contains(optionKey: String): Boolean = options.any { it.key == optionKey }

    companion object {
        /** 文字列の羅列から作るショートハンド */
        fun of(id: String, vararg keys: String): ChoiceSet =
            ChoiceSet(id, keys.map { ChoiceOption(it) })
    }
}

/** 入力手段。`:app` の `FieldEditor` がこれで分岐する */
enum class FieldUi {
    /** 連続値を大きく動かす（ピニオン・スパー・KV） */
    SLIDER,

    /** ± ボタンで 1 段ずつ（キャンバー・車高） */
    STEPPER,

    /** 数値のキーボード入力（内部減速比・デフオイル） */
    NUMBER_FIELD,

    /** 選択肢から選ぶ */
    SELECT,

    /** 1 行のテキスト */
    TEXT_FIELD,

    /** 複数行のテキスト */
    TEXT_AREA,

    /** ON / OFF */
    SWITCH
}

/**
 * 値に付く単位。記号（"mm" 等）は `:app` の `strings.xml` にある。
 * ここに無い単位が必要になったら、この enum と `:app` の対応表に 1 行ずつ足す。
 */
enum class FieldUnit {
    NONE,

    /** 歯数 */
    TEETH,

    /** ミリメートル */
    MM,

    /** 度 */
    DEGREE,

    /** モーターの KV 値 */
    KV,

    /** バッテリーのセル数（S） */
    CELL,

    /** 動粘度（デフオイル）。ダンパーオイルは wt と併用するので単位を選択項目で持つ（G-7） */
    CST,

    /** モーターのターン数 */
    TURN,

    /** 百分率（ドラッグブレーキ等） */
    PERCENT,

    /** グラム（バラスト） */
    GRAM
}

/** 既定値をシャーシ DB から引くときの参照先 */
enum class ChassisDefault {
    /** [io.github.taskengineer.rcgear.domain.model.Chassis.internalRatio] */
    INTERNAL_RATIO,

    /** [io.github.taskengineer.rcgear.domain.model.Chassis.defaultTireMm] */
    DEFAULT_TIRE_MM
}

/**
 * シャーシの素性に対する単一キーの条件（[FieldDef.requires]）。
 * 実際の評価はシャーシ DB v2（M-7）で `drive` / `hasCenterDiff` が入ってから。
 */
enum class ChassisTrait {
    /** センターデフを持つ車だけに出す（デフオイル欄など） */
    HAS_CENTER_DIFF,

    /** ベルト駆動車だけに出す（ベルトテンション欄など） */
    BELT_DRIVE
}
