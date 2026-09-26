pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// projects.core.domain のような型安全なプロジェクト参照を使えるようにする
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "RcGear"
include(":app")
include(":core:domain")
