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

rootProject.name = "AutoPie"
include(":app")
include(":termux-app")
//include(":termux-shared")
include(":terminal-emulator")
include(":terminal-view")

project(":termux-app").projectDir = File(rootDir, "termux-app/app")
//project(":termux-shared").projectDir = File(rootDir, "termux-app/termux-shared")
project(":terminal-view").projectDir = File(rootDir, "termux-app/terminal-view")
project(":terminal-emulator").projectDir = File(rootDir, "termux-app/terminal-emulator")


