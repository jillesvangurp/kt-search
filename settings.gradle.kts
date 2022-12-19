
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
}

refreshVersions {
}

include(":json-dsl")
include(":search-dsls")
include("search-client")
include("docs")
rootProject.name = "kt-search"
