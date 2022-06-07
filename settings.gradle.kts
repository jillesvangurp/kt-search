
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.40.2"
}

refreshVersions {
}

include(":json-dsl")
include(":search-dsls")
include("search-client")
include("legacy-client")
include("docs")
rootProject.name = "kt-search"
