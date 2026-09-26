package io.github.taskengineer.rcgear.data.local.asset.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * chassis-db.json のシャーシ 1 件（M-7 で v2 へ）。
 *
 * @property category  "touring" / "buggy" / "drift" / "other"。ツーリング専用アプリなので
 *   一覧の絞り込みに使う。知らない値は OTHER として読む
 * @property drive     "shaft_4wd" / "belt_4wd" / "hybrid_4wd" / "fwd" / "rwd" / "direct"。
 *   **裏が取れていないエントリは省略する**（項目の出し分けでは不明 = 出す、として扱う）
 * @property hasCenterDiff 同上。分かっているものだけ書く
 */
@Serializable
data class ChassisDto(
    @SerialName("id") val id: String,
    @SerialName("maker") val maker: String,
    @SerialName("name") val name: String,
    @SerialName("internalRatio") val internalRatio: Double,
    @SerialName("defaultTireMm") val defaultTireMm: Int,
    @SerialName("category") val category: String,
    @SerialName("drive") val drive: String? = null,
    @SerialName("hasCenterDiff") val hasCenterDiff: Boolean? = null,
    @SerialName("note") val note: String? = null
)
