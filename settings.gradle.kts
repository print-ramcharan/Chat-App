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
//        maven(url = "https://maven.goyman.com/")
//        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
//        maven(url = "https://maven.goyman.com/")
//        maven { url = uri("https://jitpack.io") }
//        maven("https://jitpack.io")
    }
}

rootProject.name = "Secret Chat"
include(":app")
