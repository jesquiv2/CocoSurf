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
        // Spike M5 · GeckoView se publica en el Maven de Mozilla.
        maven("https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "CocoSurf"
include(":app")
include(":spike")
