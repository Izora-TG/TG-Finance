// Stand-alone build for the sync server. It shares the :core module (protocol and
// last-write-wins rule) and the version catalog with the Android app.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "tg-finance-server"
include(":core")
project(":core").projectDir = file("../core")
