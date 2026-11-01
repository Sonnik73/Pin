pluginManagement {
    repositories {
        google()
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

rootProject.name = "Pin"
// -PdesktopOnly=true — сборка программы для Windows без Android SDK.
if (providers.gradleProperty("desktopOnly").orNull != "true") include(":app")
// Программа для Windows: читает и пишет тот же Pin.zip, что телефон (этапы 4.3–4.5).
include(":desktop")
