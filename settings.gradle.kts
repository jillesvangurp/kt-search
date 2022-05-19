
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.40.1"
}

refreshVersions {
}

include(":json-dsl")
include(":search-dsls")
include("search-client")
include("legacy-client")
rootProject.name = "kt-search"
