package io.github.taskengineer.rcgear.domain.model

import io.github.taskengineer.rcgear.core.designsystem.theme.ThemeMode

/**
 * ユーザー設定のドメインモデル（PLAN 6.3）。
 * DataStore Preferences に永続化され、UserPreferencesDataSource がこの型との相互変換を担う。
 *
 * デフォルト値は「初回起動時の状態」を兼ねる。DataStore に未保存のキーはこの値になる。
 *
 * @property themeMode           テーマ（手動ダーク / 手動ライト / システム追従）。HUD調なのでデフォルトはダーク
 * @property showMphAlongside    最高速の km/h 表示に mph を併記するか
 * @property animationEnabled    数値変化・ギア回転などの Compose アニメーションを有効にするか
 * @property balanceFdr          セッティング傾向バーの中央基準となる FDR（CONFIG で変更可能）
 * @property lastSelectedChassisId 前回選択していたシャーシ。null = 未選択（初回起動）
 * @property lastPinion 〜 lastTireMm 前回終了時のスライダー値。次回起動時に復元する
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val showMphAlongside: Boolean = true,
    val animationEnabled: Boolean = true,
    val balanceFdr: Double = 7.0,
    val lastSelectedChassisId: String? = null,
    val lastPinion: Int = 22,
    val lastSpur: Int = 84,
    val lastKv: Int = 6500,
    val lastCells: Int = 2,
    val lastTireMm: Int = 63
)
