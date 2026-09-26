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

rootProject.name = "SidrLauncher"

include(":app")
include(":core:common")
include(":core:ui")
include(":core:android")
include(":domain")
include(":data:ai-cloud")
include(":data:repository")
include(":data:prayer")
include(":core:testing")
include(":feature:launcher")
include(":feature:assistant")
include(":feature:suggestions")
include(":feature:permission_education")
include(":feature:settings")
include(":feature:prayer")
include(":baselineprofile")

// A0.5 — the second consumer of the portable core (Master Plan §2 criterion 10, §3.1a).
// A headless JVM harness: depends on :domain and nothing else, and nothing depends on it.
include(":consumer:jvm")
