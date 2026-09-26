// ルートプロジェクトでは plugins の宣言のみ。実際の適用は各モジュール側で行う
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint)
}

// ktlint は全モジュールに一括適用する。設定は .editorconfig 側に置く（S-3）
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
}
