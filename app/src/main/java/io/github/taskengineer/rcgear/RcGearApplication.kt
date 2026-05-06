package io.github.taskengineer.rcgear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * アプリのエントリーポイント。
 * @HiltAndroidApp により Hilt の DI コンテナがアプリケーション単位で生成される。
 * これがないと @Inject や @AndroidEntryPoint が機能しない。
 */
@HiltAndroidApp
class RcGearApplication : Application()
