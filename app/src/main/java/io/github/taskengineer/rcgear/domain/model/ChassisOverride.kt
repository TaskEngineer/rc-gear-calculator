package io.github.taskengineer.rcgear.domain.model

/**
 * シャーシ上書きのドメインモデル。
 *
 * 通常の閲覧経路では Chassis に合成済みのため登場しないが、
 * データのエクスポート / インポート（Step 11）では上書きを
 * 「差分そのもの」として扱う必要があるため、この型で受け渡す。
 */
data class ChassisOverride(
    val chassisId: String,
    val internalRatio: Double?,
    val defaultTireMm: Int?,
    val note: String?,
    val updatedAt: Long
)
