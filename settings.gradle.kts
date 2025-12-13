
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.6"
}

refreshVersions {
}

include("search-dsls")
include("search-client")
include("docs")
include("kt-search-lib-alerts")
include("kt-search-alerts-demo")
include("petstore-demo")
rootProject.name = "kt-search"
