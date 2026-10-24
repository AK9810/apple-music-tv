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
rootProject.name = "AppleMusicTV"
include(":app")
// Phone-side shell for the one-tap sign-in hand-off (loads the TV's :8080 page in a WebView).
include(":companion")
