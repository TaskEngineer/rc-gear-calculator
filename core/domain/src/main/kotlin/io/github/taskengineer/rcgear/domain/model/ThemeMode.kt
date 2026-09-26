package io.github.taskengineer.rcgear.domain.model

/**
 * テーマの選択肢（PLAN 6.3）。
 *
 * 置き場所について（REF-2 / S-5）:
 * 元は `core/designsystem/theme/Theme.kt` にあったが、これは
 * **ドメインモデル `UserPreferences` が UI 層を参照する**という逆依存を生んでいた。
 * 「ユーザーがどのテーマを選んだか」は永続化される設定値であってウィジェットではないので、
 * domain に置くのが正しい。Compose 側（`RcGearTheme`）はこの enum を受け取って
 * 実際の配色に解決する。
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }
