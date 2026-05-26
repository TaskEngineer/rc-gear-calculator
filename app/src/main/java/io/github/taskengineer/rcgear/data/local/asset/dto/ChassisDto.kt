package io.github.taskengineer.rcgear.data.local.asset.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON の makers 配下にある個別シャーシエントリ。
 *
 * JSON のキー名（"ratio", "tire"）と Kotlin のプロパティ名が異なるため、
 * @SerialName で明示的にマッピングしている。
 * Kotlin 側のプロパティ名はドメインモデルに合わせた名前（internalRatio, defaultTireMm）を使う。
 */
@Serializable
data class ChassisDto(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String,
    @SerialName("internalRatio") val internalRatio: Double,  // JSON: "internalRatio"
    @SerialName("defaultTireMm") val defaultTireMm: Int,     // JSON: "defaultTireMm"
    @SerialName("note")          val note: String? = null
)