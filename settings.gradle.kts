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
    }
}

rootProject.name = "MotionEmulator"

include(":stub")
include(":app")
include(":mock_location_plugin")
include(":update")
include(":cp_plugin")
include(":stub_plugin_xposed")
include(":ws_plugin")
include(":stub_plugin")
include(":api")
include(":cgsport_plugin")
