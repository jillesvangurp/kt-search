pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include("search-dsls")
include("search-client")
include("docs")
rootProject.name = "kt-search"
