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

rootProject.name = "SentinelBridge"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:common")
include(":feature:pipeline")
include(":feature:accessibility")
include(":feature:notification")
include(":feature:ai")
include(":feature:setup")
include(":native")
