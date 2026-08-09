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
include(":data:ai-local")
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
