pluginManagement {
    repositories {
        google {
            content {
                // Restrict this repo to only Android/Google artifacts.
                // Prevents Gradle from searching Google for things it won't have.
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
    // FAIL_ON_PROJECT_REPOS means no module is allowed to declare its own
    // repositories. All repos are managed here, centrally. This prevents
    // dependency confusion and makes the build predictable.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OneDebrid"
include(":app")