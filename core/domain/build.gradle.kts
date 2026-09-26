plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * ドメイン層（S-9）。
 *
 * **純 Kotlin JVM モジュール。Android プラグインを適用しない。**
 * これが本題で、レイヤ違反を grep や規律ではなくコンパイラに守らせるのが目的。
 * android.* や Compose を import した瞬間にビルドが落ちる。
 *
 * 依存を足すときは「これは Android 無しで意味が通るか」を毎回問うこと。
 * 通らないものは :app 側に interface の実装として置く。
 */
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Flow は Repository interface の戻り値＝公開 API なので api で公開する
    api(libs.kotlinx.coroutines.core)

    // @Inject constructor のためだけ。Dagger 本体は :app 側にある
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
