pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://www.jitpack.io") }
        maven { setUrl("https://api.xposed.info/") }
        maven { setUrl("https://s01.oss.sonatype.org/content/groups/staging/") }
    }
}

rootProject.name = "MotionEmulator"

include(":app")
include(":mock_location_plugin")
include(":update")
include(":api")
