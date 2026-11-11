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

rootProject.name = "OpenOfficePDF"

// 主壳应用
include(":app")

// Core 核心基础设施
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:storage")
include(":core:memory")
include(":core:task")
include(":core:ipc")

// Engine 核心计算/解析引擎
include(":engine:pdf")
include(":engine:office")
include(":engine:ocr")
include(":engine:markdown")
include(":engine:converter")

// Feature 业务与界面
include(":feature:home")
include(":feature:filebrowser")
include(":feature:pdf")
include(":feature:office")
include(":feature:markdown")
include(":feature:ocr")
include(":feature:converter")
include(":feature:settings")
